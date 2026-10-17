from dataclasses import replace
from types import SimpleNamespace

from fastapi.testclient import TestClient

from backend.app import main
from backend.app.analyzer import GeminiAnalyzer
from backend.app import firebase_services
from backend.app.schemas import AnalysisReport, AnalyzeRequest, InputType, validate_social_url


def test_accepts_supported_post_urls() -> None:
    request = AnalyzeRequest(
        input_type=InputType.URL,
        content="https://www.instagram.com/p/example/",
    )

    assert request.content == "https://www.instagram.com/p/example/"


def test_rejects_untrusted_hosts_and_non_https_urls() -> None:
    for url in (
        "https://instagram.com.attacker.example/p/1",
        "http://instagram.com/p/1",
        "https://user:password@instagram.com/p/1",
        "https://t.co/example",
    ):
        try:
            validate_social_url(url)
        except ValueError:
            continue
        raise AssertionError(f"Unexpectedly accepted URL: {url}")


def test_rejects_blank_or_oversized_text() -> None:
    try:
        AnalyzeRequest(input_type=InputType.TEXT, content="   ")
    except ValueError:
        pass
    else:
        raise AssertionError("Blank text should be rejected")

    try:
        AnalyzeRequest(input_type=InputType.TEXT, content="x" * 12_001)
    except ValueError:
        pass
    else:
        raise AssertionError("Oversized text should be rejected")


def test_analysis_is_disabled_without_explicit_configuration(monkeypatch) -> None:
    monkeypatch.setattr(
        main,
        "settings",
        replace(
            main.settings,
            firebase_project_id="test-project",
            client_token="test-client-token",
            grounded_analysis_enabled=False,
        ),
    )
    monkeypatch.setattr(
        main.firebase_services,
        "verify_firebase_id_token",
        lambda id_token: {"uid": "test-user"},
    )
    client = TestClient(main.app)

    response = client.post(
        "/api/v1/analyze",
        json={"input_type": "TEXT", "content": "A factual claim to check."},
        headers={
            "Authorization": "Bearer valid-firebase-token",
            "X-NarrativeOS-Token": "test-client-token",
        },
    )

    assert response.status_code == 503
    assert "disabled" in response.json()["detail"].lower()


def test_analysis_requires_firebase_identity_when_configured(monkeypatch) -> None:
    monkeypatch.setattr(
        main,
        "settings",
        replace(main.settings, firebase_project_id="test-project", client_token="test-client-token"),
    )
    client = TestClient(main.app)

    response = client.post(
        "/api/v1/analyze",
        json={"input_type": "TEXT", "content": "A factual claim to check."},
        headers={"X-NarrativeOS-Token": "test-client-token"},
    )

    assert response.status_code == 401


def test_analysis_saves_report_for_authenticated_user(monkeypatch) -> None:
    monkeypatch.setattr(
        main,
        "settings",
        replace(
            main.settings,
            firebase_project_id="test-project",
            client_token="test-client-token",
            gemini_api_key="test-provider-key",
            grounded_analysis_enabled=True,
        ),
    )
    monkeypatch.setattr(
        main.firebase_services,
        "verify_firebase_id_token",
        lambda id_token: {"uid": "firebase-user-42"},
    )
    report = AnalysisReport(
        input_type=InputType.TEXT,
        retrieval_status="FULL",
        overview="One claim found.",
        claims=[],
    )
    monkeypatch.setattr(main.analyzer, "analyze", lambda input_type, content: report)
    saved: dict[str, object] = {}

    def save_report(user_id: str, report_to_save: AnalysisReport) -> str:
        saved["user_id"] = user_id
        saved["report"] = report_to_save
        return "analysis-doc-1"

    monkeypatch.setattr(main.firebase_services, "save_report", save_report)
    client = TestClient(main.app)

    response = client.post(
        "/api/v1/analyze",
        json={"input_type": "TEXT", "content": "Private user text"},
        headers={
            "Authorization": "Bearer valid-firebase-token",
            "X-NarrativeOS-Token": "test-client-token",
        },
    )

    assert response.status_code == 200
    assert saved["user_id"] == "firebase-user-42"
    assert response.json()["analysis_id"] == "analysis-doc-1"
    assert "Private user text" not in str(saved["report"])


def test_history_routes_are_scoped_to_authenticated_user(monkeypatch) -> None:
    monkeypatch.setattr(
        main,
        "settings",
        replace(
            main.settings,
            firebase_project_id="test-project",
            client_token="test-client-token",
        ),
    )
    monkeypatch.setattr(
        main.firebase_services,
        "verify_firebase_id_token",
        lambda id_token: {"uid": "firebase-user-42"},
    )
    report = AnalysisReport(
        analysis_id="analysis-doc-1",
        input_type=InputType.TEXT,
        retrieval_status="FULL",
        overview="Saved finding.",
        claims=[],
    )
    calls: dict[str, str] = {}

    def list_reports(user_id: str) -> list[AnalysisReport]:
        calls["list_user"] = user_id
        return [report]

    def delete_report(user_id: str, analysis_id: str) -> bool:
        calls["delete_user"] = user_id
        calls["delete_id"] = analysis_id
        return True

    monkeypatch.setattr(main.firebase_services, "list_reports", list_reports)
    monkeypatch.setattr(main.firebase_services, "delete_report", delete_report)
    client = TestClient(main.app)
    headers = {
        "Authorization": "Bearer valid-firebase-token",
        "X-NarrativeOS-Token": "test-client-token",
    }

    history_response = client.get("/api/v1/history", headers=headers)
    delete_response = client.delete("/api/v1/history/analysis-doc-1", headers=headers)

    assert history_response.status_code == 200
    assert history_response.json()[0]["analysis_id"] == "analysis-doc-1"
    assert delete_response.status_code == 204
    assert calls == {
        "list_user": "firebase-user-42",
        "delete_user": "firebase-user-42",
        "delete_id": "analysis-doc-1",
    }


def test_firestore_writes_only_report_data_under_user_path(monkeypatch) -> None:
    saved: dict[str, object] = {}
    path: list[str] = []

    class DocumentReference:
        id = "analysis-doc-1"

        def collection(self, name: str) -> "CollectionReference":
            path.append(name)
            return CollectionReference()

        def set(self, data: dict[str, object]) -> None:
            saved.update(data)

    class CollectionReference:
        def collection(self, name: str) -> "CollectionReference":
            path.append(name)
            return self

        def document(self, document_id: str | None = None) -> DocumentReference:
            if document_id is not None:
                path.append(document_id)
            return DocumentReference()

    class FirestoreClient:
        def collection(self, name: str) -> CollectionReference:
            path.append(name)
            return CollectionReference()

    monkeypatch.setattr(firebase_services, "_firebase_app", lambda: object())
    monkeypatch.setattr(firebase_services.firestore, "client", lambda app: FirestoreClient())
    report = AnalysisReport(
        input_type=InputType.TEXT,
        retrieval_status="FULL",
        overview="A report summary.",
        claims=[],
    )

    document_id = firebase_services.save_report("firebase-user-42", report)

    assert document_id == "analysis-doc-1"
    assert path == ["users", "firebase-user-42", "analyses"]
    assert saved["overview"] == "A report summary."
    assert "content" not in saved


def test_public_deployment_root_and_health_are_accessible() -> None:
    client = TestClient(main.app)

    root = client.get("/")
    health = client.get("/health")

    assert root.status_code == 200
    assert root.json()["status"] == "ok"
    assert root.json()["interactive_api"] == "/docs"
    assert health.status_code == 200
    assert health.json() == {"status": "ok"}


def test_configured_client_token_is_required(monkeypatch) -> None:
    monkeypatch.setattr(
        main,
        "settings",
        replace(main.settings, client_token="test-client-token"),
    )
    client = TestClient(main.app)
    payload = {"input_type": "TEXT", "content": "A factual claim to check."}

    assert client.post("/api/v1/analyze", json=payload).status_code == 401
    assert client.post(
        "/api/v1/analyze",
        json=payload,
        headers={"X-NarrativeOS-Token": "wrong-token"},
    ).status_code == 401
    assert client.post(
        "/api/v1/analyze",
        json=payload,
        headers={"X-NarrativeOS-Token": "test-client-token"},
    ).status_code == 503


def test_grounded_sources_come_only_from_provider_annotations() -> None:
    interaction = SimpleNamespace(
        steps=[
            SimpleNamespace(
                type="model_output",
                content=[
                    SimpleNamespace(
                        type="text",
                        annotations=[
                            SimpleNamespace(
                                type="url_citation",
                                title="Official source",
                                url="https://example.gov/report",
                            ),
                            SimpleNamespace(type="text", title="Made up", url="https://fake.example"),
                        ],
                    ),
                ],
            ),
        ],
    )

    citations = GeminiAnalyzer._grounded_sources(interaction)

    assert [(citation.title, citation.url) for citation in citations] == [
        ("Official source", "https://example.gov/report"),
    ]