from __future__ import annotations

import os
from dataclasses import dataclass


def _env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    client_token: str = os.getenv("NARRATIVEOS_CLIENT_TOKEN", "")
    firebase_project_id: str = os.getenv("FIREBASE_PROJECT_ID", os.getenv("GOOGLE_CLOUD_PROJECT", ""))
    gemini_model: str = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
    grounded_analysis_enabled: bool = _env_bool("ENABLE_GROUNDED_ANALYSIS")
    max_daily_analyses: int = int(os.getenv("MAX_DAILY_ANALYSES", "10"))


settings = Settings()