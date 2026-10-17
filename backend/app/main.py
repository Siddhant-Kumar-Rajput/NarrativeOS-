from __future__ import annotations

import asyncio
import hmac
from datetime import UTC, date, datetime
from threading import Lock

from fastapi import FastAPI, Header, HTTPException

from . import firebase_services
from .analyzer import GeminiAnalyzer
from .schemas import AnalysisReport, AnalyzeRequest
from .settings import settings


class DailyAnalysisLimit:
    def __init__(self, limit: int) -> None:
        self._limit = max(1, limit)
        self._day: date | None = None
        self._count = 0
        self._lock = Lock()

    def reserve(self) -> bool:
        today = datetime.now(UTC).date()
        with self._lock:
            if self._day != today:
                self._day = today
                self._count = 0
            if self._count >= self._limit:
                return False
            self._count += 1
            return True


app = FastAPI(title="NarrativeOS API", version="0.1.0")
daily_limit = DailyAnalysisLimit(settings.max_daily_analyses)
analyzer = GeminiAnalyzer(settings)


@app.get("/", include_in_schema=False)
async def root() -> dict[str, str]:
    return {
        "service": "NarrativeOS API",
        "status": "ok",
        "health": "/health",
        "interactive_api": "/docs",
    }


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


async def _require_user_id(authorization: str | None) -> str:
    if not settings.firebase_project_id:
        raise HTTPException(status_code=503, detail="Firebase authentication is not configured.")
    scheme, _, id_token = (authorization or "").partition(" ")
    if scheme.lower() != "bearer" or not id_token:
        raise HTTPException(status_code=401, detail="Sign in with Firebase to continue.")
    try:
        claims = await asyncio.to_thread(firebase_services.verify_firebase_id_token, id_token)
    except Exception as error:
        raise HTTPException(status_code=401, detail="Firebase sign-in is invalid or expired.") from error
    user_id = claims.get("uid")
    if not isinstance(user_id, str) or not user_id:
        raise HTTPException(status_code=401, detail="Firebase sign-in did not identify a user.")
    return user_id


@app.post("/api/v1/analyze", response_model=AnalysisReport)
async def analyze(
    request: AnalyzeRequest,
    client_token: str | None = Header(default=None, alias="X-NarrativeOS-Token"),
    authorization: str | None = Header(default=None),
) -> AnalysisReport:
    if settings.client_token and not hmac.compare_digest(client_token or "", settings.client_token):
        raise HTTPException(status_code=401, detail="A valid app access token is required.")
    user_id = await _require_user_id(authorization)
    if not settings.grounded_analysis_enabled:
        raise HTTPException(
            status_code=503,
            detail="Grounded analysis is disabled. Configure the backend explicitly to enable it.",
        )
    if not settings.gemini_api_key:
        raise HTTPException(
            status_code=503,
            detail="The backend has no Gemini API key configured.",
        )
    if not daily_limit.reserve():
        raise HTTPException(
            status_code=429,
            detail="The configured daily analysis limit has been reached.",
        )

    try:
        report = await asyncio.to_thread(analyzer.analyze, request.input_type, request.content)
    except Exception as error:
        raise HTTPException(
            status_code=502,
            detail="The analysis provider failed. No report was generated.",
        ) from error

    try:
        analysis_id = await asyncio.to_thread(firebase_services.save_report, user_id, report)
    except Exception as error:
        raise HTTPException(
            status_code=503,
            detail="The report was analyzed but could not be saved to Firestore.",
        ) from error
    return report.model_copy(
        update={"analysis_id": analysis_id, "created_at": datetime.now(UTC)},
    )


@app.get("/api/v1/history", response_model=list[AnalysisReport])
async def get_history(
    client_token: str | None = Header(default=None, alias="X-NarrativeOS-Token"),
    authorization: str | None = Header(default=None),
) -> list[AnalysisReport]:
    if settings.client_token and not hmac.compare_digest(client_token or "", settings.client_token):
        raise HTTPException(status_code=401, detail="A valid app access token is required.")
    user_id = await _require_user_id(authorization)
    try:
        return await asyncio.to_thread(firebase_services.list_reports, user_id)
    except Exception as error:
        raise HTTPException(status_code=503, detail="Firestore history is unavailable.") from error


@app.delete("/api/v1/history/{analysis_id}", status_code=204)
async def delete_history_item(
    analysis_id: str,
    client_token: str | None = Header(default=None, alias="X-NarrativeOS-Token"),
    authorization: str | None = Header(default=None),
) -> None:
    if settings.client_token and not hmac.compare_digest(client_token or "", settings.client_token):
        raise HTTPException(status_code=401, detail="A valid app access token is required.")
    user_id = await _require_user_id(authorization)
    try:
        deleted = await asyncio.to_thread(firebase_services.delete_report, user_id, analysis_id)
    except Exception as error:
        raise HTTPException(status_code=503, detail="Firestore history is unavailable.") from error
    if not deleted:
        raise HTTPException(status_code=404, detail="Analysis history item not found.")