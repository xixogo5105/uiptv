"""Database location belongs to UIPTV, never to builder configuration."""
from pathlib import Path
import subprocess
import pytest
from uiptv_epg_builder.bridge import JavaBridge
from uiptv_epg_builder.config import load_config


def test_missing_automatically_resolved_database_is_not_created(tmp_path):
    bridge = JavaBridge(jvm_args=["-Duser.home=" + str(tmp_path)])
    with pytest.raises(RuntimeError, match="existing UIPTV database"):
        bridge.export_inventory()
    assert list(tmp_path.rglob("*.db")) == []


def test_database_cli_override_is_rejected(tmp_path):
    bridge = JavaBridge(jvm_args=["-Duser.home=" + str(tmp_path)])
    result = subprocess.run(bridge._build_command("status", "--database", str(tmp_path / "other.db")),
                            capture_output=True, text=True)
    assert result.returncode != 0
    assert "database is resolved by UIPTV" in result.stderr
    assert not (tmp_path / "other.db").exists()


def test_legacy_builder_database_setting_has_no_effect(tmp_path):
    path = tmp_path / "config.toml"
    path.write_text('[database]\npath="/ignored/old.db"\n')
    config = load_config(path)
    assert not hasattr(config, "database")
