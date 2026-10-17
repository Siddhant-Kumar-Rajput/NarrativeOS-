from types import SimpleNamespace

from fastapi.testclient import TestClient

from backend.app import main
from backend.app.analyzer import GeminiAnalyzer
from backend.app.schemas import AnalyzeRequest, InputType, validate_social_url


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


def test_analysis_is_disabled_without_explicit_configuration() -> None:
    client = TestClient(main.app)

    response = client.post(
        "/api/v1/analyze",
        json={"input_type": "TEXT", "content": "A factual claim to check."},
    )

    assert response.status_code == 503
    assert "disabled" in response.json()["detail"].lower()


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