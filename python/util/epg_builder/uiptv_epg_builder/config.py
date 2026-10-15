"""Configuration management for UIPTV EPG Builder."""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import tomllib as tomli
import tomli_w


@dataclass
class SourceConfig:
    """Configuration for an EPG source."""
    id: str
    name: str
    type: str  # xmltv_url, local_xmltv, iptv_org_site, published_aggregate
    url: str = ""
    countries: list[str] = field(default_factory=list)
    languages: list[str] = field(default_factory=list)
    enabled: bool = True
    priority: int = 100
    refresh_hours: int = 6
    max_size_mb: int = 200
    timeout_seconds: int = 60
    decompress: bool = True
    headers: dict[str, str] = field(default_factory=dict)
    extra: dict[str, Any] = field(default_factory=dict)


@dataclass
class MatchingConfig:
    """Channel matching configuration."""
    min_confidence: float = 0.85
    margin_threshold: float = 0.10
    enable_fuzzy: bool = True
    quality_aliases: bool = True
    exact_id_first: bool = True
    canonical_alias_first: bool = True


@dataclass
class CollectorConfig:
    """Collector configuration."""
    max_concurrent: int = 3
    max_retries: int = 3
    retry_backoff_seconds: float = 2.0
    user_agent: str = "UIPTV-EPG-Builder/0.1.0"
    verify_ssl: bool = True
    follow_redirects: bool = True
    max_redirects: int = 5


@dataclass
class BridgeConfig:
    """Java bridge configuration."""
    java_executable: str = "java"
    bridge_jar: str = ""
    classpath: str = ""
    timeout_seconds: int = 120
    jvm_args: list[str] = field(default_factory=list)


@dataclass
class LoggingConfig:
    """Logging configuration."""
    level: str = "INFO"
    file: str = ""
    max_size_mb: int = 10
    backup_count: int = 5
    redact_secrets: bool = True


@dataclass
class LockConfig:
    """Process lock configuration."""
    lock_file: str = ""
    timeout_seconds: int = 300


@dataclass
class Config:
    """Main configuration."""
    sources: list[SourceConfig] = field(default_factory=list)
    matching: MatchingConfig = field(default_factory=MatchingConfig)
    collector: CollectorConfig = field(default_factory=CollectorConfig)
    bridge: BridgeConfig = field(default_factory=BridgeConfig)
    logging: LoggingConfig = field(default_factory=LoggingConfig)
    lock: LockConfig = field(default_factory=LockConfig)
    working_dir: str = ""
    dry_run: bool = True
    force_refresh: bool = False
    include_sources: list[str] = field(default_factory=list)
    exclude_sources: list[str] = field(default_factory=list)
    region_filter: list[str] = field(default_factory=list)
    language_filter: list[str] = field(default_factory=list)


def load_config(path: str | Path) -> Config:
    """Load configuration from TOML file."""
    path = Path(path)
    if not path.exists():
        raise FileNotFoundError(f"Configuration file not found: {path}")

    with open(path, "rb") as f:
        data = tomli.load(f)

    config = Config()

    # Sources config
    if "sources" in data:
        for src in data["sources"]:
            config.sources.append(SourceConfig(
                id=src["id"],
                name=src["name"],
                type=src["type"],
                url=src.get("url", ""),
                countries=src.get("countries", []),
                languages=src.get("languages", []),
                enabled=src.get("enabled", True),
                priority=src.get("priority", 100),
                refresh_hours=src.get("refresh_hours", 6),
                max_size_mb=src.get("max_size_mb", 200),
                timeout_seconds=src.get("timeout_seconds", 60),
                decompress=src.get("decompress", True),
                headers=src.get("headers", {}),
                extra=src.get("extra", {}),
            ))

    # Matching config
    if "matching" in data:
        m = data["matching"]
        config.matching = MatchingConfig(
            min_confidence=m.get("min_confidence", 0.85),
            margin_threshold=m.get("margin_threshold", 0.10),
            enable_fuzzy=m.get("enable_fuzzy", True),
            quality_aliases=m.get("quality_aliases", True),
            exact_id_first=m.get("exact_id_first", True),
            canonical_alias_first=m.get("canonical_alias_first", True),
        )

    # Collector config
    if "collector" in data:
        c = data["collector"]
        config.collector = CollectorConfig(
            max_concurrent=c.get("max_concurrent", 3),
            max_retries=c.get("max_retries", 3),
            retry_backoff_seconds=c.get("retry_backoff_seconds", 2.0),
            user_agent=c.get("user_agent", "UIPTV-EPG-Builder/0.1.0"),
            verify_ssl=c.get("verify_ssl", True),
            follow_redirects=c.get("follow_redirects", True),
            max_redirects=c.get("max_redirects", 5),
        )

    # Bridge config
    if "bridge" in data:
        b = data["bridge"]
        config.bridge = BridgeConfig(
            java_executable=b.get("java_executable", "java"),
            bridge_jar=b.get("bridge_jar", ""),
            classpath=b.get("classpath", ""),
            timeout_seconds=b.get("timeout_seconds", 120),
            jvm_args=b.get("jvm_args", []),
        )

    # Logging config
    if "logging" in data:
        l = data["logging"]
        config.logging = LoggingConfig(
            level=l.get("level", "INFO"),
            file=l.get("file", ""),
            max_size_mb=l.get("max_size_mb", 10),
            backup_count=l.get("backup_count", 5),
            redact_secrets=l.get("redact_secrets", True),
        )

    # Lock config
    if "lock" in data:
        l = data["lock"]
        config.lock = LockConfig(
            lock_file=l.get("lock_file", ""),
            timeout_seconds=l.get("timeout_seconds", 300),
        )

    # Top-level settings
    config.working_dir = data.get("working_dir", "")
    config.dry_run = data.get("dry_run", True)
    config.force_refresh = data.get("force_refresh", False)
    config.include_sources = data.get("include_sources", [])
    config.exclude_sources = data.get("exclude_sources", [])
    config.region_filter = data.get("region_filter", [])
    config.language_filter = data.get("language_filter", [])

    config.working_dir = str(Path(config.working_dir or Path.home() / ".uiptv" / "epg-builder").expanduser())
    if config.bridge.bridge_jar:
        config.bridge.bridge_jar = str(Path(config.bridge.bridge_jar).expanduser())
    if not 0 <= config.matching.min_confidence <= 1 or not 0 <= config.matching.margin_threshold <= 1:
        raise ValueError("Matching thresholds must be between zero and one")
    ids = [source.id for source in config.sources]
    if len(ids) != len(set(ids)):
        raise ValueError("Source IDs must be unique")
    for source in config.sources:
        if source.max_size_mb <= 0 or source.timeout_seconds <= 0 or source.refresh_hours < 0:
            raise ValueError("Invalid source size, timeout or refresh interval")
    return config


def save_config(config: Config, path: str | Path) -> None:
    """Save configuration to TOML file."""
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)

    data: dict[str, Any] = {}

    data["sources"] = []
    for src in config.sources:
        data["sources"].append({
            "id": src.id,
            "name": src.name,
            "type": src.type,
            "url": src.url,
            "countries": src.countries,
            "languages": src.languages,
            "enabled": src.enabled,
            "priority": src.priority,
            "refresh_hours": src.refresh_hours,
            "max_size_mb": src.max_size_mb,
            "timeout_seconds": src.timeout_seconds,
            "decompress": src.decompress,
            "headers": src.headers,
            "extra": src.extra,
        })

    data["matching"] = {
        "min_confidence": config.matching.min_confidence,
        "margin_threshold": config.matching.margin_threshold,
        "enable_fuzzy": config.matching.enable_fuzzy,
        "quality_aliases": config.matching.quality_aliases,
        "exact_id_first": config.matching.exact_id_first,
        "canonical_alias_first": config.matching.canonical_alias_first,
    }

    data["collector"] = {
        "max_concurrent": config.collector.max_concurrent,
        "max_retries": config.collector.max_retries,
        "retry_backoff_seconds": config.collector.retry_backoff_seconds,
        "user_agent": config.collector.user_agent,
        "verify_ssl": config.collector.verify_ssl,
        "follow_redirects": config.collector.follow_redirects,
        "max_redirects": config.collector.max_redirects,
    }

    data["bridge"] = {
        "java_executable": config.bridge.java_executable,
        "bridge_jar": config.bridge.bridge_jar,
        "classpath": config.bridge.classpath,
        "timeout_seconds": config.bridge.timeout_seconds,
        "jvm_args": config.bridge.jvm_args,
    }

    data["logging"] = {
        "level": config.logging.level,
        "file": config.logging.file,
        "max_size_mb": config.logging.max_size_mb,
        "backup_count": config.logging.backup_count,
        "redact_secrets": config.logging.redact_secrets,
    }

    data["lock"] = {
        "lock_file": config.lock.lock_file,
        "timeout_seconds": config.lock.timeout_seconds,
    }

    data["working_dir"] = config.working_dir
    data["dry_run"] = config.dry_run
    data["force_refresh"] = config.force_refresh
    data["include_sources"] = config.include_sources
    data["exclude_sources"] = config.exclude_sources
    data["region_filter"] = config.region_filter
    data["language_filter"] = config.language_filter

    with open(path, "wb") as f:
        tomli_w.dump(data, f)


def create_default_config(path: str | Path) -> Config:
    """Create a default configuration file."""
    config = Config()

    # Set up working directory
    if not config.working_dir:
        config.working_dir = str(Path.home() / ".uiptv" / "epg-builder")

    # Set up lock file
    if not config.lock.lock_file:
        config.lock.lock_file = str(Path(config.working_dir) / "epg-builder.lock")

    # Add example sources
    config.sources = [
        SourceConfig(
            id="iptv-org-epg",
            name="IPTV.org EPG",
            type="iptv_org_site",
            url="https://github.com/iptv-org/epg",
            countries=["US", "GB", "CA", "AU", "DE", "FR", "ES", "IT", "NL", "PL"],
            languages=["en", "de", "fr", "es", "it", "nl", "pl"],
            enabled=False,
            priority=50,
            refresh_hours=12,
            extra={"sites": "all", "languages": "all"},
        ),
        SourceConfig(
            id="epgshare01",
            name="EPGShare01",
            type="xmltv_url",
            url="https://epgshare01.online/epgshare01/epg_ripper.xml.gz",
            countries=["US", "GB", "CA", "AU", "DE", "FR", "ES", "IT", "NL", "PL"],
            languages=["en", "de", "fr", "es", "it", "nl", "pl"],
            enabled=False,
            priority=60,
            refresh_hours=12,
        ),
        SourceConfig(
            id="iptv-epg-org",
            name="IPTV-EPG.org",
            type="xmltv_url",
            url="https://iptv-epg.org/guides",
            countries=["US", "GB", "DE", "FR", "ES", "IT", "NL", "PL"],
            languages=["en", "de", "fr", "es", "it", "nl", "pl"],
            enabled=False,
            priority=70,
            refresh_hours=24,
        ),
    ]

    save_config(config, path)
    return config