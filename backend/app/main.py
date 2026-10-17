from __future__ import annotations

import asyncio
from datetime import UTC, date, datetime
from threading import Lock

from fastapi import FastAPI, HTTPException

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


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/v1/analyze", response_model=AnalysisReport)
async def analyze(request: AnalyzeRequest) -> AnalysisReport:
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
        return await asyncio.to_thread(analyzer.analyze, request.input_type, request.content)
    except Exception as error:
        raise HTTPException(
            status_code=502,
            detail="The analysis provider failed. No report was generated.",
        ) from error