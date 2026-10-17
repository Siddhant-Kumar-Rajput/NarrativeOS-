from __future__ import annotations

from dataclasses import dataclass
from urllib.parse import urlsplit

from parth_dl import get_info


@dataclass(frozen=True)
class RetrievalResult:
    status: str
    extracted_text: str
    limitations: tuple[str, ...] = ()


def retrieve_social_url(url: str) -> RetrievalResult:
    hostname = (urlsplit(url).hostname or "").lower().removeprefix("www.")
    if hostname in {"x.com", "twitter.com"}:
        return RetrievalResult(
            status="UNSUPPORTED",
            extracted_text="",
            limitations=("Automated X retrieval is not available yet. Paste the post text instead.",),
        )
    if hostname != "instagram.com":
        return RetrievalResult(
            status="UNSUPPORTED",
            extracted_text="",
            limitations=("Automated retrieval is not available for this platform.",),
        )

    try:
        metadata = get_info(url)
    except Exception:
        return RetrievalResult(
            status="FAILED",
            extracted_text="",
            limitations=("Instagram could not provide accessible public post metadata.",),
        )

    caption_excerpt = str(metadata.get("title") or "").strip()
    if not caption_excerpt or caption_excerpt.lower().startswith("media by "):
        return RetrievalResult(
            status="PARTIAL",
            extracted_text="",
            limitations=(
                "Post metadata was retrieved, but no caption text was available. Media analysis is not implemented yet.",
            ),
        )

    content_type = str(metadata.get("type") or "post")
    uploader = str(metadata.get("uploader") or "unknown")
    extracted_text = (
        f"Retrieved Instagram {content_type} by {uploader}. "
        f"Caption excerpt (parth-dl title field): {caption_excerpt}"
    )
    return RetrievalResult(
        status="PARTIAL",
        extracted_text=extracted_text,
        limitations=(
            "Only parth-dl's caption excerpt was analyzed; Instagram media and the remainder of longer captions were not processed.",
        ),
    )