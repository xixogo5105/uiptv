"""Packaged bridge discovery must work without a checkout or Java on PATH."""
from pathlib import Path
import importlib.util
import os
import sys
import pytest
from uiptv_epg_builder.bridge import JavaBridge


def test_frozen_bridge_uses_adjacent_java_and_jar(tmp_path, monkeypatch):
    binary = tmp_path / "folder with spaces" / "uiptv-epg"
    binary.parent.mkdir()
    jar = binary.parent / "bridge/epg-bridge.jar"
    jar.parent.mkdir()
    jar.touch()
    monkeypatch.setattr(sys, "frozen", True, raising=False)
    monkeypatch.setattr(sys, "executable", str(binary))
    command = JavaBridge()._build_command("status")
    java = binary.parent / "runtime/bin" / ("java.exe" if os.name == "nt" else "java")
    assert command == [str(java), "--enable-native-access=ALL-UNNAMED", "-jar", str(jar), "status"]


def test_frozen_java_subprocess_restores_library_environment(monkeypatch):
    monkeypatch.setattr(sys, "frozen", True, raising=False)
    monkeypatch.setenv("LD_LIBRARY_PATH", "/frozen-python-libs")
    monkeypatch.setenv("LD_LIBRARY_PATH_ORIG", "/original-libs")
    monkeypatch.setenv("DYLD_LIBRARY_PATH", "/frozen-python-libs")
    monkeypatch.delenv("DYLD_LIBRARY_PATH_ORIG", raising=False)
    environment = JavaBridge._java_environment()
    assert environment["LD_LIBRARY_PATH"] == "/original-libs"
    assert "DYLD_LIBRARY_PATH" not in environment
    assert os.environ["LD_LIBRARY_PATH"] == "/frozen-python-libs"


def test_native_builder_rejects_cross_compilation_before_mutating_files(tmp_path, monkeypatch):
    spec = importlib.util.spec_from_file_location("native_builder", Path(__file__).parents[1] / "packaging/build_native.py")
    builder = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(builder)
    monkeypatch.setattr(builder, "host_target", lambda: "linux-x86_64")
    from argparse import Namespace
    with pytest.raises(ValueError, match="matching build host"):
        builder.build(Namespace(target="mac-aarch64", output=str(tmp_path)))
    assert list(tmp_path.iterdir()) == []
