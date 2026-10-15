"""Offline collector regression tests; never call public feeds."""
import time
from unittest.mock import Mock
from uiptv_epg_builder.config import CollectorConfig, SourceConfig
from uiptv_epg_builder.registry import SourceRegistry, SourceCollector


def collector(tmp_path):
    config = CollectorConfig()
    registry = SourceRegistry(config, str(tmp_path))
    return SourceCollector(registry, config)


def response(content=b'<tv/>', status=200):
    result = Mock(status_code=status, headers={"ETag": "version"})
    result.iter_content.return_value = [content]
    return result


def test_first_http_fetch_is_not_skipped_and_fresh_fetch_reuses_bytes(tmp_path):
    fetcher = collector(tmp_path)
    fetcher.session.get = Mock(return_value=response())
    source = SourceConfig("source", "Source", "xmltv_url", "https://example.invalid/guide")
    assert fetcher.fetch_source(source).content == b'<tv/>'
    result = fetcher.fetch_source(source)
    assert result.from_cache and result.content == b'<tv/>'
    assert fetcher.session.get.call_count == 1


def test_304_reuses_cached_content(tmp_path):
    fetcher = collector(tmp_path)
    fetcher.session.get = Mock(side_effect=[response(), response(status=304)])
    source = SourceConfig("source", "Source", "xmltv_url", "https://example.invalid/guide", refresh_hours=0)
    fetcher.fetch_source(source)
    result = fetcher.fetch_source(source)
    assert result.success and result.from_cache and result.content == b'<tv/>'


def test_local_file_path_has_no_undefined_force_and_returns_content(tmp_path):
    file = tmp_path / "guide.xml"
    file.write_bytes(b'<tv/>')
    fetcher = collector(tmp_path)
    source = SourceConfig("local", "Local", "local_xmltv", str(file))
    assert fetcher.fetch_source(source).content == b'<tv/>'
    assert fetcher.fetch_source(source).content == b'<tv/>'


def test_failed_source_preserves_previous_cached_bytes(tmp_path):
    fetcher = collector(tmp_path)
    fetcher.session.get = Mock(side_effect=[response(), RuntimeError("connection failed")])
    source = SourceConfig("source", "Source", "xmltv_url", "https://example.invalid/guide")
    fetcher.fetch_source(source)
    assert not fetcher.fetch_source(source, force=True).success
    assert fetcher.fetch_source(source).content == b'<tv/>'
