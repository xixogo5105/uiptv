"""Tests for config module."""

import tempfile
from pathlib import Path

import pytest

from uiptv_epg_builder.config import (
    Config, SourceConfig, MatchingConfig,
    CollectorConfig, BridgeConfig, LoggingConfig, LockConfig,
    load_config, save_config, create_default_config,
)


def test_default_config():
    """Test default configuration creation."""
    with tempfile.TemporaryDirectory() as tmpdir:
        config_path = Path(tmpdir) / "config.toml"
        config = create_default_config(config_path)

        assert "[database]" not in config_path.read_text()
        assert len(config.sources) == 3
        assert config.matching.min_confidence == 0.85
        assert config.collector.max_concurrent == 3
        assert config.working_dir != ""
        assert config.lock.lock_file != ""
        assert config.dry_run is True


def test_load_save_config():
    """Test loading and saving configuration."""
    with tempfile.TemporaryDirectory() as tmpdir:
        config_path = Path(tmpdir) / "config.toml"

        # Create a custom config
        config = Config(
            sources=[
                SourceConfig(
                    id="test-source",
                    name="Test Source",
                    type="xmltv_url",
                    url="http://example.com/epg.xml.gz",
                    priority=50,
                )
            ],
            matching=MatchingConfig(min_confidence=0.9),
            collector=CollectorConfig(max_concurrent=5),
        )

        save_config(config, config_path)
        loaded = load_config(config_path)

        assert not hasattr(loaded, "database")
        assert len(loaded.sources) == 1
        assert loaded.sources[0].id == "test-source"
        assert loaded.matching.min_confidence == 0.9
        assert loaded.collector.max_concurrent == 5


def test_source_config_defaults():
    """Test source config default values."""
    source = SourceConfig(
        id="test",
        name="Test",
        type="xmltv_url",
    )

    assert source.enabled is True
    assert source.priority == 100
    assert source.refresh_hours == 6
    assert source.max_size_mb == 200
    assert source.timeout_seconds == 60
    assert source.decompress is True


def test_matching_config_defaults():
    """Test matching config defaults."""
    matching = MatchingConfig()

    assert matching.min_confidence == 0.85
    assert matching.margin_threshold == 0.10
    assert matching.enable_fuzzy is True
    assert matching.quality_aliases is True
    assert matching.exact_id_first is True
    assert matching.canonical_alias_first is True

def test_unspecified_cli_filters_preserve_config_file_filters(tmp_path, monkeypatch):
    from uiptv_epg_builder.runner import run_builder, RunResult
    path = tmp_path / "config.toml"
    save_config(Config(region_filter=["IN", "PK", "US", "AU", "NZ", "GB", "CA"], include_sources=["mine"]), path)
    seen = {}
    class Runner:
        def __init__(self, config):
            seen["config"] = config
        def run(self):
            return RunResult(success=True)
    monkeypatch.setattr("uiptv_epg_builder.runner.EpgBuilderRunner", Runner)
    assert run_builder(str(path), region_filter=None, include_sources=None).success
    assert seen["config"].region_filter == ["IN", "PK", "US", "AU", "NZ", "GB", "CA"]
    assert seen["config"].include_sources == ["mine"]
    assert run_builder(str(path), region_filter=["GB"]).success
    assert seen["config"].region_filter == ["GB"]
