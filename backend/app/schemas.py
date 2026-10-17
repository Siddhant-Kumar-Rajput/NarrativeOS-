from __future__ import annotations

from enum import Enum
from urllib.parse import urlsplit

from pydantic import BaseModel, Field, field_validator, model_validator


SUPPORTED_HOSTS = {"instagram.com", "x.com", "twitter.com"}
MAX_CONTENT_LENGTH = 12_000


class InputType(str, Enum):
    URL = "URL"
    TEXT = "TEXT"


class Verdict(str, Enum):
    SUPPORTED = "supported"
    CONTRADICTED = "contradicted"
    PARTIALLY_SUPPORTED = "partially_supported"
    UNVERIFIED = "unverified"
    NOT_APPLICABLE = "not_applicable"


class ClaimCategory(str, Enum):
    FACTUAL = "factual"
    OPINION = "opinion"
    PREDICTION = "prediction"
    QUESTION = "question"
    SATIRE = "satire"
    PERSONAL_EXPERIENCE = "personal_experience"
    RECOMMENDATION = "recommendation"


class AnalyzeRequest(BaseModel):
    input_type: InputType
    content: str = Field(min_length=1, max_length=MAX_CONTENT_LENGTH)

    @field_validator("content")
    @classmethod
    def content_must_not_be_blank(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Content must not be blank.")
        return value

    @model_validator(mode="after")
    def validate_input(self) -> AnalyzeRequest:
        if self.input_type is InputType.URL:
            validate_social_url(self.content)
        return self


def validate_social_url(value: str) -> str:
    try:
        parsed = urlsplit(value)
        hostname = (parsed.hostname or "").lower().removeprefix("www.")
        port = parsed.port
    except ValueError as error:
        raise ValueError("Enter a valid public Instagram or X post URL.") from error

    if (
        parsed.scheme != "https"
        or hostname not in SUPPORTED_HOSTS
        or parsed.username is not None
        or parsed.password is not None
        or port is not None
        or not parsed.path.strip("/")
    ):
        raise ValueError("Use an HTTPS URL for a public Instagram or X post.")

    return value


class Citation(BaseModel):
    title: str
    url: str
    evidence_summary: str


class ClaimFinding(BaseModel):
    original_claim: str
    category: ClaimCategory
    verdict: Verdict
    reasoning: str
    confidence_explanation: str
    citations: list[Citation] = Field(default_factory=list)


class AnalysisReport(BaseModel):
    input_type: InputType
    retrieval_status: str
    overview: str
    claims: list[ClaimFinding]
    limitations: list[str] = Field(default_factory=list)