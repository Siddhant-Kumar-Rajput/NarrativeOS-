from __future__ import annotations

from threading import Lock
from typing import Any

import firebase_admin
from firebase_admin import auth, firestore

from .schemas import AnalysisReport
from .settings import settings


_app_lock = Lock()


def _firebase_app() -> firebase_admin.App:
    if not settings.firebase_project_id:
        raise RuntimeError("FIREBASE_PROJECT_ID is not configured.")
    try:
        return firebase_admin.get_app()
    except ValueError:
        with _app_lock:
            try:
                return firebase_admin.get_app()
            except ValueError:
                return firebase_admin.initialize_app(
                    options={"projectId": settings.firebase_project_id},
                )


def verify_firebase_id_token(id_token: str) -> dict[str, Any]:
    return auth.verify_id_token(id_token, app=_firebase_app())


def save_report(user_id: str, report: AnalysisReport) -> str:
    client = firestore.client(app=_firebase_app())
    reference = (
        client.collection("users")
        .document(user_id)
        .collection("analyses")
        .document()
    )
    data = report.model_dump(mode="json", exclude={"analysis_id", "created_at"})
    data["created_at"] = firestore.SERVER_TIMESTAMP
    reference.set(data)
    return reference.id


def list_reports(user_id: str, limit: int = 25) -> list[AnalysisReport]:
    client = firestore.client(app=_firebase_app())
    collection = (
        client.collection("users")
        .document(user_id)
        .collection("analyses")
    )
    documents = collection.order_by(
        "created_at",
        direction=firestore.Query.DESCENDING,
    ).limit(limit).stream()

    reports: list[AnalysisReport] = []
    for document in documents:
        data = document.to_dict() or {}
        data["analysis_id"] = document.id
        reports.append(AnalysisReport.model_validate(data))
    return reports


def delete_report(user_id: str, analysis_id: str) -> bool:
    client = firestore.client(app=_firebase_app())
    reference = (
        client.collection("users")
        .document(user_id)
        .collection("analyses")
        .document(analysis_id)
    )
    if not reference.get().exists:
        return False
    reference.delete()
    return True