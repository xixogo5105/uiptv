"""Build a host-native, self-contained EPG utility. Invoked by Maven package."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import platform
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import tempfile
import venv
from datetime import datetime, timedelta, timezone


def host_target():
    system = {"Darwin": "mac", "Windows": "windows", "Linux": "linux"}.get(platform.system())
    arch = {"arm64": "aarch64", "aarch64": "aarch64", "AMD64": "x86_64", "amd64": "x86_64", "x86_64": "x86_64"}.get(platform.machine())
    if not system or not arch:
        raise ValueError("Unsupported native build host")
    return f"{system}-{arch}"


def run(command, **kwargs):
    print("Running:", " ".join(map(str, command)), flush=True)
    return subprocess.run(list(map(str, command)), check=True, timeout=900, **kwargs)


def smoke_test(bundle, baseline):
    """Exercise frozen Python + bundled Java + SQLite without system Python/Java."""
    binary = bundle / ("uiptv-epg.exe" if os.name == "nt" else "uiptv-epg")
    with tempfile.TemporaryDirectory(prefix="uiptv-epg-smoke-") as directory:
        work = Path(directory)
        home = work / "home"
        app_home = home / ("uiptv" if sys.platform in ("darwin", "win32") else ".config/uiptv")
        app_home.mkdir(parents=True)
        database = app_home / "uiptv.db"
        with sqlite3.connect(database) as connection:
            connection.executescript(baseline.read_text(encoding="utf-8"))
            connection.execute("INSERT INTO Configuration(id,epgEnabled,epgMappingsJson) VALUES(1,'1','{}')")
            connection.execute("INSERT INTO Account(id,accountName,type) VALUES(1,'Package smoke test','M3U8_LOCAL')")
            connection.execute("INSERT INTO Category(id,categoryId,accountId,accountType) VALUES(10,'provider-category','1','itv')")
            connection.execute("INSERT INTO Channel(id,channelId,categoryId,name,extraJson) VALUES(100,'provider-id','10','Station',?)", ('{"xmltv_id":"guide"}',))
        now = datetime.now(timezone.utc)
        guide = work / "guide.xml"
        guide.write_text(f'<tv><channel id="guide"><display-name>Station</display-name></channel><programme channel="guide" start="{(now-timedelta(minutes=1)):%Y%m%d%H%M%S} +0000" stop="{(now+timedelta(hours=1)):%Y%m%d%H%M%S} +0000"><title>Packaged programme</title></programme></tv>', encoding="utf-8")
        config = work / "config.toml"
        config.write_text(f'working_dir = {json.dumps(str(work / "cache"))}\n[[sources]]\nid="smoke"\nname="Local smoke guide"\ntype="local_xmltv"\nurl={json.dumps(str(guide))}\n', encoding="utf-8")
        environment = os.environ.copy()
        # Java's ordinary user.home mechanism isolates automatic database discovery.
        environment["JAVA_TOOL_OPTIONS"] = '-Duser.home="' + str(home) + '"'
        environment.pop("_JAVA_OPTIONS", None)
        environment.pop("JDK_JAVA_OPTIONS", None)
        environment.pop("PYTHONPATH", None)
        environment.pop("JAVA_HOME", None)
        environment["PATH"] = ""
        run([binary, "--help"], env=environment, cwd=work, capture_output=True, text=True)
        for mode in ("--dry-run", "--apply", "--apply"):
            result = run([binary, "build", "--config", config, mode, "--json"], env=environment, cwd=work, capture_output=True, text=True)
            if not json.loads(result.stdout)["success"]:
                raise RuntimeError("Packaged pipeline returned failure")
            with sqlite3.connect(database) as connection:
                count = connection.execute("SELECT count(*) FROM epg_programme").fetchone()[0]
                if count != (0 if mode == "--dry-run" else 1):
                    raise RuntimeError("Packaged pipeline wrote unexpected programme count")
        status = run([binary, "status", "--config", config, "--json"], env=environment, cwd=work, capture_output=True, text=True)
        if json.loads(status.stdout)["programme_count"] != 1:
            raise RuntimeError("Packaged status did not find the imported programme")
    print("Frozen pipeline smoke test passed (no Python or Java on PATH).", flush=True)


def build(args):
    if sys.version_info < (3, 11):
        raise ValueError("Native packaging requires Python 3.11 or newer")
    if args.target != host_target():
        raise ValueError(f"Native target {args.target} requires a matching build host; current host is {host_target()}")
    source = Path(__file__).resolve().parents[1]
    root = source.parents[2]
    target = Path(args.output).resolve()
    java_home = Path(args.java_home).resolve()
    suffix = ".exe" if os.name == "nt" else ""
    jlink = java_home / "bin" / ("jlink" + suffix)
    bridge = Path(args.bridge).resolve()
    if not bridge.is_file() or not jlink.is_file():
        raise ValueError("Build the shaded EPG bridge first and use a JDK with jlink")
    settings = subprocess.run([str(java_home / "bin" / ("java" + suffix)), "-XshowSettings:properties", "-version"], capture_output=True, text=True, check=True)
    arch = args.target.rsplit("-", 1)[1]
    accepted = {"aarch64", "arm64"} if arch == "aarch64" else {"amd64", "x86_64"}
    actual = next(line.split("=", 1)[1].strip() for line in settings.stderr.splitlines() if "os.arch =" in line)
    if actual not in accepted:
        raise ValueError("Maven JDK architecture does not match the selected target")
    target.mkdir(parents=True, exist_ok=True)
    environment_dir = target / "build-venv"
    if not environment_dir.exists():
        venv.EnvBuilder(with_pip=True).create(environment_dir)
    python = environment_dir / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    run([python, "-m", "pip", "install", "--disable-pip-version-check", "-r", source / "packaging/requirements.txt", source])
    dist = target / "dist"
    run([python, "-m", "PyInstaller", "--noconfirm", "--clean", "--onedir", "--console", "--name", "uiptv-epg",
         "--paths", source, "--distpath", dist, "--workpath", target / "pyinstaller", "--specpath", target,
         source / "packaging/entrypoint.py"], cwd=source)
    bundle = dist / "uiptv-epg"
    (bundle / "bridge").mkdir()
    shutil.copy2(bridge, bundle / "bridge/epg-bridge.jar")
    run([jlink, "--add-modules", "java.base,java.sql,java.xml,java.net.http,jdk.httpserver,jdk.net,java.desktop,java.naming,java.management,jdk.crypto.ec",
         "--strip-debug", "--no-header-files", "--no-man-pages", "--output", bundle / "runtime"])
    shutil.copy2(source / "README.md", bundle / "README.md")
    shutil.copy2(source / "CONFIGURATION.md", bundle / "CONFIGURATION.md")
    for name in ("LICENSE", "LICENSE.md", "LICENSE.txt"):
        if (root / name).is_file():
            shutil.copy2(root / name, bundle / name)
    smoke_test(bundle, root / "core/src/main/resources/db/migrations/0000_baseline.sql")
    archive_base = dist / f"uiptv-epg-{args.version}-{args.target}"
    if os.name == "nt":
        archive = Path(shutil.make_archive(str(archive_base), "zip", dist, "uiptv-epg"))
    else:
        archive = Path(str(archive_base) + ".tar.gz")
        with tarfile.open(archive, "w:gz") as output:
            output.add(bundle, arcname="uiptv-epg")
    print(f"Native EPG utility: {archive}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--java-home", required=True)
    parser.add_argument("--bridge", required=True)
    parser.add_argument("--output", required=True)
    try:
        build(parser.parse_args())
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        if isinstance(error, subprocess.CalledProcessError):
            print(error.stderr or error.stdout or str(error), file=sys.stderr)
        else:
            print(error, file=sys.stderr)
        sys.exit(1)
