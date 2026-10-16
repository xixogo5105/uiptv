"""Java bridge for UIPTV EPG Builder - exports inventory and imports bundles."""

from __future__ import annotations

import json
import subprocess
import sys
import os
import tempfile
from pathlib import Path
from typing import Any

from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from .inventory import InventoryExport
if TYPE_CHECKING:
    from .bundle import EpgBundle


class JavaBridge:
    """Interface to the Java EPG bridge."""

    def __init__(self, java_executable: str = "java", bridge_jar: str = "", classpath: str = "",
                 timeout_seconds: int = 120, jvm_args: list[str] | None = None):
        bundle = Path(sys.executable).resolve().parent if getattr(sys, "frozen", False) else None
        if bundle and java_executable == "java":
            java_executable = str(bundle / "runtime" / "bin" / ("java.exe" if os.name == "nt" else "java"))
        self.java_executable = java_executable
        self.bridge_jar = bridge_jar
        self.classpath = classpath
        self.timeout_seconds = timeout_seconds
        self.jvm_args = jvm_args or []
        self.name_rules = None

    @staticmethod
    def _java_environment():
        environment = os.environ.copy()
        if getattr(sys, "frozen", False):
            # PyInstaller's Python-library search path must not leak into Java.
            for key in ("LD_LIBRARY_PATH", "DYLD_LIBRARY_PATH"):
                if key + "_ORIG" in environment:
                    environment[key] = environment[key + "_ORIG"]
                else:
                    environment.pop(key, None)
        return environment

    def _build_command(self, action: str, *args: str) -> list[str]:
        """Build the Java command."""
        cmd = [self.java_executable, "--enable-native-access=ALL-UNNAMED"]
        cmd.extend(self.jvm_args)

        if self.bridge_jar:
            cmd.extend(["-jar", self.bridge_jar])
        elif self.classpath:
            cmd.extend(["-cp", self.classpath, "com.uiptv.epgbuilder.EpgBridge"])
        else:
            # Try to find the bridge JAR
            bridge_jar = self._find_bridge_jar()
            if bridge_jar:
                cmd.extend(["-jar", bridge_jar])
            else:
                raise RuntimeError("Java bridge not found. Build the project first or configure bridge_jar/classpath.")

        cmd.extend([action, *args])
        return cmd

    def _find_bridge_jar(self) -> str | None:
        """Find the bridge JAR in the project."""
        if getattr(sys, "frozen", False):
            path = Path(sys.executable).resolve().parent / "bridge" / "epg-bridge.jar"
            return str(path) if path.is_file() else None
        project_root = Path(__file__).parent.parent.parent.parent.parent
        possible_paths = [
            project_root / "epg-bridge" / "target" / "epg-bridge-*.jar",
            project_root / "core" / "target" / "epg-bridge-*.jar",
            project_root / "target" / "epg-bridge-*.jar",
        ]

        for pattern in possible_paths:
            matches = [p for p in project_root.glob(str(pattern.relative_to(project_root))) if not p.name.startswith("original-")]
            if matches:
                return str(max(matches, key=lambda p: p.stat().st_mtime))
        return None

    def export_inventory(self) -> InventoryExport:
        """Export channel inventory from UIPTV database."""
        cmd = self._build_command("export-inventory-jsonl")
        from .inventory import InventoryExport
        # Spool to disk so subprocess output is never duplicated as a huge string.
        with tempfile.TemporaryFile(mode="w+", encoding="utf-8") as output:
            result = subprocess.run(cmd, env=self._java_environment(), stdout=output,
                                    stderr=subprocess.PIPE, text=True, timeout=self.timeout_seconds)
            if result.returncode != 0:
                raise RuntimeError(f"Java bridge export-inventory failed: {result.stderr}")
            output.seek(0)
            return InventoryExport.from_json_lines(output)

    def import_bundle(self, bundle: EpgBundle) -> dict[str, Any]:
        """Import bundle into UIPTV database."""
        # Write bundle to temp file
        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
            f.write(bundle.to_json())
            bundle_path = f.name

        try:
            cmd = self._build_command("import-bundle", "--bundle", bundle_path)

            result = subprocess.run(
                cmd,
                env=self._java_environment(),
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
            )

            if result.returncode != 0:
                raise RuntimeError(f"Java bridge import-bundle failed: {result.stderr}")

            return json.loads(result.stdout.strip()) if result.stdout.strip() else {"success": True}

        finally:
            Path(bundle_path).unlink(missing_ok=True)

    def validate_bundle(self, bundle: EpgBundle) -> dict[str, Any]:
        """Validate bundle against current database."""
        with tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False) as f:
            f.write(bundle.to_json())
            bundle_path = f.name

        try:
            cmd = self._build_command("validate-bundle", "--bundle", bundle_path)

            result = subprocess.run(
                cmd,
                env=self._java_environment(),
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
            )

            if result.returncode != 0:
                raise RuntimeError(f"Java bridge validate-bundle failed: {result.stderr}")

            return json.loads(result.stdout.strip()) if result.stdout.strip() else {"valid": True}

        finally:
            Path(bundle_path).unlink(missing_ok=True)

    def parse_xmltv(self, content, now):
        with tempfile.TemporaryDirectory() as directory:
            xml = Path(directory) / "guide.xml"
            xml.write_bytes(content)
            request = Path(directory) / "request.json"
            payload = {"path": str(xml), "now": int(now.timestamp() * 1000)}
            if self.name_rules is not None:
                payload["name_rules"] = self.name_rules
            request.write_text(json.dumps(payload), encoding="utf-8")
            result = subprocess.run(self._build_command("parse-xmltv", "--bundle", str(request)),
                                    env=self._java_environment(),
                capture_output=True, text=True, timeout=self.timeout_seconds)
            if result.returncode:
                raise ValueError("Java XMLTV parsing failed")
            return json.loads(result.stdout)

    # Keep merge requests well below the Java bridge's 128 MiB file limit.
    MERGE_REQUEST_LIMIT_BYTES = 16 * 1024 * 1024

    def merge_schedules(self, schedules):
        """Batch complete timelines by UTF-8 bytes; Java owns interval resolution."""
        prefix, suffix = b'{"schedules":[', b']}'
        batch = []
        size = len(prefix) + len(suffix)
        merged = []
        for schedule in schedules:
            encoded = json.dumps(schedule, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            if len(encoded) + len(prefix) + len(suffix) > self.MERGE_REQUEST_LIMIT_BYTES:
                raise ValueError("A single guide timeline exceeds the merge request limit")
            extra = len(encoded) + bool(batch)
            if batch and size + extra > self.MERGE_REQUEST_LIMIT_BYTES:
                merged.extend(self._merge_schedule_batch(prefix + b",".join(batch) + suffix, len(batch)))
                batch, size = [], len(prefix) + len(suffix)
                extra = len(encoded)
            batch.append(encoded)
            size += extra
        if batch:
            merged.extend(self._merge_schedule_batch(prefix + b",".join(batch) + suffix, len(batch)))
        return merged

    def _merge_schedule_batch(self, payload, expected):
        with tempfile.TemporaryDirectory() as directory:
            request = Path(directory) / "schedules.json"
            request.write_bytes(payload)
            result = subprocess.run(self._build_command("merge-schedules", "--bundle", str(request)),
                                    env=self._java_environment(),
                                    capture_output=True, text=True, timeout=self.timeout_seconds)
            if result.returncode:
                raise RuntimeError("Java schedule merge failed: " + result.stderr)
            schedules = json.loads(result.stdout)["schedules"]
            if not isinstance(schedules, list) or len(schedules) != expected:
                raise ValueError("Java merge returned an incomplete set of schedules")
            return schedules

    def get_status(self) -> dict[str, Any]:
        """Get EPG cache status."""
        cmd = self._build_command("status")

        result = subprocess.run(
            cmd,
            env=self._java_environment(),
            capture_output=True,
            text=True,
            timeout=self.timeout_seconds,
        )

        if result.returncode != 0:
            raise RuntimeError(f"Java bridge status failed: {result.stderr}")

        return json.loads(result.stdout.strip()) if result.stdout.strip() else {}

def bridge_from_config(config):
    """One subprocess contract for inventory, validation, status and publication."""
    return JavaBridge(config.java_executable, config.bridge_jar, config.classpath,
                      config.timeout_seconds, config.jvm_args)
