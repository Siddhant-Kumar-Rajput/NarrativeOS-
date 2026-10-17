from backend.app import retrieval


def test_instagram_caption_is_partial_until_media_processing_exists(monkeypatch) -> None:
    monkeypatch.setattr(
        retrieval,
        "get_info",
        lambda url: {"title": "A claim in a public caption", "type": "video", "uploader": "account"},
    )

    result = retrieval.retrieve_social_url("https://www.instagram.com/reel/example/")

    assert result.status == "PARTIAL"
    assert "A claim in a public caption" in result.extracted_text
    assert "media" in result.limitations[0].lower()


def test_x_url_returns_actionable_unsupported_result() -> None:
    result = retrieval.retrieve_social_url("https://x.com/account/status/123")

    assert result.status == "UNSUPPORTED"
    assert result.extracted_text == ""
    assert "paste" in result.limitations[0].lower()


def test_inaccessible_instagram_content_does_not_produce_text(monkeypatch) -> None:
    def fail_retrieval(url: str) -> dict[str, str]:
        raise RuntimeError("upstream retrieval failed")

    monkeypatch.setattr(retrieval, "get_info", fail_retrieval)

    result = retrieval.retrieve_social_url("https://www.instagram.com/p/example/")

    assert result.status == "FAILED"
    assert result.extracted_text == ""