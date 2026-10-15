"""Tests for CLI."""

import pytest
from click.testing import CliRunner

from uiptv_epg_builder.cli import cli


def test_cli_help():
    """Test CLI help."""
    runner = CliRunner()
    result = runner.invoke(cli, ["--help"])
    assert result.exit_code == 0
    assert "UIPTV EPG Builder" in result.output


def test_init_command(tmp_path):
    """Test init command."""
    runner = CliRunner()
    config_path = tmp_path / "config.toml"
    result = runner.invoke(cli, ["init", "--output", str(config_path)])
    assert result.exit_code == 0
    assert "Created default configuration" in result.output


def test_sources_list(tmp_path):
    """Test sources list command."""
    runner = CliRunner()
    config_path = tmp_path / "config.toml"
    result = runner.invoke(cli, ["init", "--output", str(config_path)])
    assert result.exit_code == 0

    result = runner.invoke(cli, ["sources", "list", "--config", str(config_path)])
    assert result.exit_code == 0
    # Should list the 3 default sources
    assert "iptv-org-epg" in result.output
    assert "epgshare01" in result.output
    assert "iptv-epg-org" in result.output


def test_build_json_reports_failure_and_nonzero_exit(monkeypatch):
    import json
    from uiptv_epg_builder.runner import RunResult
    monkeypatch.setattr("uiptv_epg_builder.cli.run_builder", lambda **kwargs: RunResult(success=False, error="test failure"))
    result = CliRunner().invoke(cli, ["build", "--json"])
    assert result.exit_code == 1
    assert json.loads(result.stdout)["error"] == "test failure"


def test_build_dry_run_passes_flag(monkeypatch):
    from uiptv_epg_builder.runner import RunResult
    seen = {}
    def run(**kwargs):
        seen.update(kwargs)
        return RunResult(success=True)
    monkeypatch.setattr("uiptv_epg_builder.cli.run_builder", run)
    result = CliRunner().invoke(cli, ["build"])
    assert result.exit_code == 0
    assert seen["dry_run"] is True
    result = CliRunner().invoke(cli, ["build", "--apply"])
    assert result.exit_code == 0
    assert seen["dry_run"] is False


def test_scan_command(tmp_path):
    """Test scan command."""
    runner = CliRunner()
    config_path = tmp_path / "config.toml"
    result = runner.invoke(cli, ["init", "--output", str(config_path)])
    assert result.exit_code == 0

    # Scan will fail without Java bridge
    result = runner.invoke(cli, ["scan", "--config", str(config_path)])
    assert "Java bridge not found" in result.output or "Error" in result.output or "Database path not configured" in result.output


def test_status_calls_bridge(tmp_path, monkeypatch):
    import json
    from uiptv_epg_builder.config import Config, save_config
    path = tmp_path / "config.toml"
    save_config(Config(), path)
    monkeypatch.setattr("uiptv_epg_builder.bridge.JavaBridge.get_status", lambda self: {"programme_count": 7})
    result = CliRunner().invoke(cli, ["status", "--config", str(path), "--json"])
    assert result.exit_code == 0
    assert json.loads(result.stdout)["programme_count"] == 7


def test_report_command(tmp_path):
    """Test report command."""
    runner = CliRunner()
    config_path = tmp_path / "config.toml"
    result = runner.invoke(cli, ["init", "--output", str(config_path)])
    assert result.exit_code == 0

    result = runner.invoke(cli, ["report", "--config", str(config_path)])
    assert result.exit_code == 1
    assert "No saved report" in result.output
