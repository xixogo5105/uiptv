"""Source registry and collector management for UIPTV EPG Builder."""

from __future__ import annotations

import hashlib
import json
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from enum import Enum
from pathlib import Path
from typing import Any

import requests

from .config import CollectorConfig, SourceConfig


class SourceType(Enum):
    """Supported source types."""
    XMLTV_URL = "xmltv_url"
    LOCAL_XMLTV = "local_xmltv"
    IPTV_ORG_SITE = "iptv_org_site"
    PUBLISHED_AGGREGATE = "published_aggregate"


class SourceStatus(Enum):
    """Source fetch status."""
    UNKNOWN = "unknown"
    SUCCESS = "success"
    FAILED = "failed"
    SKIPPED_FRESH = "skipped_fresh"
    SKIPPED_DISABLED = "skipped_disabled"
    SKIPPED_FILTERED = "skipped_filtered"


@dataclass
class SourceHealth:
    """Health and metadata for a source."""
    source_id: str
    last_attempt: float = 0.0
    last_success: float = 0.0
    last_failure: float = 0.0
    consecutive_failures: int = 0
    status: SourceStatus = SourceStatus.UNKNOWN
    error_message: str = ""
    etag: str = ""
    last_modified: str = ""
    content_hash: str = ""
    size_bytes: int = 0
    programme_count: int = 0
    channel_count: int = 0

    def to_dict(self) -> dict[str, Any]:
        return {
            "source_id": self.source_id,
            "last_attempt": self.last_attempt,
            "last_success": self.last_success,
            "last_failure": self.last_failure,
            "consecutive_failures": self.consecutive_failures,
            "status": self.status.value,
            "error_message": self.error_message,
            "etag": self.etag,
            "last_modified": self.last_modified,
            "content_hash": self.content_hash,
            "size_bytes": self.size_bytes,
            "programme_count": self.programme_count,
            "channel_count": self.channel_count,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> SourceHealth:
        health = cls(source_id=data.get("source_id", ""))
        health.last_attempt = data.get("last_attempt", 0.0)
        health.last_success = data.get("last_success", 0.0)
        health.last_failure = data.get("last_failure", 0.0)
        health.consecutive_failures = data.get("consecutive_failures", 0)
        health.status = SourceStatus(data.get("status", "unknown"))
        health.error_message = data.get("error_message", "")
        health.etag = data.get("etag", "")
        health.last_modified = data.get("last_modified", "")
        health.content_hash = data.get("content_hash", "")
        health.size_bytes = data.get("size_bytes", 0)
        health.programme_count = data.get("programme_count", 0)
        health.channel_count = data.get("channel_count", 0)
        return health


@dataclass
class FetchResult:
    """Result of fetching a source."""
    source_id: str
    success: bool
    content: bytes = b""
    content_type: str = ""
    etag: str = ""
    last_modified: str = ""
    error: str = ""
    from_cache: bool = False
    duration_seconds: float = 0.0


class SourceRegistry:
    """Manages the registry of EPG sources."""

    def __init__(self, config: CollectorConfig, working_dir: str):
        self.config = config
        self.working_dir = Path(working_dir)
        self.working_dir.mkdir(parents=True, exist_ok=True)
        self.sources: dict[str, SourceConfig] = {}
        self.health: dict[str, SourceHealth] = {}
        self._load_health()

    def register_source(self, source: SourceConfig) -> None:
        """Register a source."""
        self.sources[source.id] = source
        if source.id not in self.health:
            self.health[source.id] = SourceHealth(source_id=source.id)

    def unregister_source(self, source_id: str) -> None:
        """Unregister a source."""
        self.sources.pop(source_id, None)
        self.health.pop(source_id, None)

    def get_source(self, source_id: str) -> SourceConfig | None:
        """Get a source by ID."""
        return self.sources.get(source_id)

    def get_enabled_sources(self, include: list[str] | None = None, exclude: list[str] | None = None,
                           region_filter: list[str] | None = None, language_filter: list[str] | None = None) -> list[SourceConfig]:
        """Get enabled sources, optionally filtered."""
        include_set = set(include) if include else None
        exclude_set = set(exclude) if exclude else None
        region_set = set(region_filter) if region_filter else None
        language_set = set(language_filter) if language_filter else None

        result = []
        for source in self.sources.values():
            if not source.enabled:
                continue
            if include_set and source.id not in include_set:
                continue
            if exclude_set and source.id in exclude_set:
                continue
            if region_set and source.countries and not any(c in region_set for c in source.countries):
                continue
            if language_set and source.languages and not any(l in language_set for l in source.languages):
                continue
            result.append(source)

        # Sort by priority (lower = higher priority)
        result.sort(key=lambda s: s.priority)
        return result

    def is_source_due(self, source: SourceConfig, force: bool = False) -> bool:
        """Check if a source is due for refresh."""
        if force:
            return True

        health = self.health.get(source.id)
        if not health or health.last_success == 0:
            return True

        # Check if enough time has passed
        elapsed_hours = (time.time() - health.last_success) / 3600
        return elapsed_hours >= source.refresh_hours

    def get_health(self, source_id: str) -> SourceHealth:
        """Get health for a source."""
        return self.health.get(source_id, SourceHealth(source_id=source_id))

    def update_health(self, health: SourceHealth) -> None:
        """Update source health."""
        self.health[health.source_id] = health
        self._save_health()

    def _load_health(self) -> None:
        """Load health from disk."""
        health_file = self.working_dir / "source_health.json"
        if health_file.exists():
            try:
                with open(health_file) as f:
                    data = json.load(f)
                for item in data:
                    health = SourceHealth.from_dict(item)
                    self.health[health.source_id] = health
            except (json.JSONDecodeError, OSError):
                pass

    def _save_health(self) -> None:
        """Save health to disk."""
        health_file = self.working_dir / "source_health.json"
        try:
            with open(health_file, "w") as f:
                json.dump([h.to_dict() for h in self.health.values()], f, indent=2)
        except OSError:
            pass


class SourceCollector:
    """Collects EPG data from registered sources."""

    def __init__(self, registry: SourceRegistry, config: CollectorConfig):
        self.registry = registry
        self.config = config
        self.session = requests.Session()
        self.session.headers.update({"User-Agent": config.user_agent})
        self.session.verify = config.verify_ssl
        self.session.max_redirects = config.max_redirects

    def fetch_source(self, source: SourceConfig, force: bool = False) -> FetchResult:
        """Fetch a single source."""
        start_time = time.time()
        health = self.registry.get_health(source.id)

        cache = self.registry.working_dir / (hashlib.sha256(source.id.encode()).hexdigest() + ".xmltv")
        # A fresh source still needs its cached content for matching.

        if not force and not self.registry.is_source_due(source, force=False) and cache.is_file():
            health.status = SourceStatus.SKIPPED_FRESH
            self.registry.update_health(health)
            return FetchResult(
                source_id=source.id,
                success=True,
                from_cache=True,
                content=cache.read_bytes(),
                duration_seconds=time.time() - start_time,
            )

        # Build request
        headers = dict(source.headers)
        if health.etag and not force and cache.is_file():
            headers["If-None-Match"] = health.etag
        if health.last_modified and not force and cache.is_file():
            headers["If-Modified-Since"] = health.last_modified

        try:
            if source.type == SourceType.LOCAL_XMLTV.value:
                return self._fetch_local(source, health, start_time)
            elif source.type == SourceType.IPTV_ORG_SITE.value:
                return self._fetch_iptv_org(source, health, start_time)
            elif source.type in (SourceType.XMLTV_URL.value, SourceType.PUBLISHED_AGGREGATE.value):
                if not source.url.startswith(("https://", "http://")):
                    raise ValueError("Remote sources require HTTP(S)")
                return self._fetch_http(source, health, headers, start_time)
            else:
                raise ValueError("Unsupported source type")
        except Exception as e:
            health.status = SourceStatus.FAILED
            health.last_failure = time.time()
            health.consecutive_failures += 1
            health.error_message = f"Source fetch failed ({type(e).__name__})"
            self.registry.update_health(health)
            return FetchResult(
                source_id=source.id,
                success=False,
                error=health.error_message,
                duration_seconds=time.time() - start_time,
            )

    def _fetch_http(self, source: SourceConfig, health: SourceHealth,
                   headers: dict[str, str], start_time: float) -> FetchResult:
        """Fetch from HTTP(S) URL."""
        cache = self.registry.working_dir / (hashlib.sha256(source.id.encode()).hexdigest() + ".xmltv")
        response = self.session.get(
            source.url,
            headers=headers,
            timeout=source.timeout_seconds,
            allow_redirects=self.config.follow_redirects,
            stream=True,
        )

        try:
            # Check for 304 Not Modified
            if response.status_code == 304:
                response.close()
                if not cache.is_file():
                    raise ValueError("304 without cached content")
                health.status = SourceStatus.SKIPPED_FRESH
                health.last_attempt = time.time()
                self.registry.update_health(health)
                return FetchResult(
                    source_id=source.id,
                    success=True,
                    from_cache=True,
                    content=cache.read_bytes(),
                    duration_seconds=time.time() - start_time,
                )

            response.raise_for_status()

            # Check content size
            content_length = response.headers.get("Content-Length")
            if content_length and int(content_length) > source.max_size_mb * 1024 * 1024:
                raise ValueError(f"Content too large: {content_length} bytes")

            # Read content with size limit
            content = bytearray()
            max_size = source.max_size_mb * 1024 * 1024
            deadline_start = time.monotonic()
            for chunk in response.iter_content(chunk_size=8192):
                if time.monotonic() - deadline_start > source.timeout_seconds:
                    response.close()
                    raise TimeoutError("Source deadline exceeded")
                content.extend(chunk)
                if len(content) > max_size:
                    raise ValueError(f"Content exceeds size limit: {len(content)} bytes")

            response.close()
            content = bytes(content)
            temporary = cache.with_suffix(".tmp")
            temporary.write_bytes(content)
            temporary.replace(cache)

            # Update health
            health.last_attempt = time.time()
            health.last_success = time.time()
            health.status = SourceStatus.SUCCESS
            health.consecutive_failures = 0
            health.error_message = ""
            health.etag = response.headers.get("ETag", "")
            health.last_modified = response.headers.get("Last-Modified", "")
            health.size_bytes = len(content)
            health.content_hash = hashlib.sha256(content).hexdigest()[:16]

            self.registry.update_health(health)

            return FetchResult(
                source_id=source.id,
                success=True,
                content=content,
                content_type=response.headers.get("Content-Type", ""),
                etag=health.etag,
                last_modified=health.last_modified,
                duration_seconds=time.time() - start_time,
            )
        finally:
            response.close()

    def _fetch_local(self, source: SourceConfig, health: SourceHealth,
                    start_time: float) -> FetchResult:
        """Fetch from local file."""
        path = Path(source.url)
        if not path.exists():
            raise FileNotFoundError(f"Local XMLTV file not found: {path}")

        stat = path.stat()
        if stat.st_size > source.max_size_mb * 1024 * 1024:
            raise ValueError(f"File too large: {stat.st_size} bytes")

        content = path.read_bytes()
        file_hash = hashlib.sha256(content).hexdigest()[:16]

        health.last_attempt = time.time()
        health.last_success = time.time()
        health.status = SourceStatus.SUCCESS
        health.consecutive_failures = 0
        health.error_message = ""
        health.size_bytes = len(content)
        health.content_hash = file_hash
        health.last_modified = datetime.fromtimestamp(stat.st_mtime, tz=timezone.utc).strftime("%a, %d %b %Y %H:%M:%S GMT")

        self.registry.update_health(health)

        return FetchResult(
            source_id=source.id,
            success=True,
            content=content,
            content_type="application/xml",
            duration_seconds=time.time() - start_time,
        )

    def _fetch_iptv_org(self, source: SourceConfig, health: SourceHealth,
                       start_time: float) -> FetchResult:
        """Fetch using iptv-org/epg CLI."""
        # This would invoke the iptv-org/epg tool
        # For now, return an error indicating it's not implemented
        health.status = SourceStatus.FAILED
        health.last_failure = time.time()
        health.consecutive_failures += 1
        health.error_message = "iptv-org/epg collector not yet implemented"
        self.registry.update_health(health)
        return FetchResult(
            source_id=source.id,
            success=False,
            error="iptv-org/epg collector not yet implemented",
            duration_seconds=time.time() - start_time,
        )

    def close(self) -> None:
        """Close the collector session."""
        self.session.close()


def load_source_registry(path: str | Path) -> list[SourceConfig]:
    """Load source registry from JSON file."""
    with open(path) as f:
        data = json.load(f)

    sources = []
    for item in data.get("sources", []):
        sources.append(SourceConfig(
            id=item["id"],
            name=item["name"],
            type=item["type"],
            url=item.get("url", ""),
            countries=item.get("countries", []),
            languages=item.get("languages", []),
            enabled=item.get("enabled", True),
            priority=item.get("priority", 100),
            refresh_hours=item.get("refresh_hours", 6),
            max_size_mb=item.get("max_size_mb", 200),
            timeout_seconds=item.get("timeout_seconds", 60),
            decompress=item.get("decompress", True),
            headers=item.get("headers", {}),
            extra=item.get("extra", {}),
        ))
    return sources


def save_source_registry(sources: list[SourceConfig], path: str | Path) -> None:
    """Save source registry to JSON file."""
    data = {
        "version": 1,
        "updated": datetime.now(timezone.utc).isoformat(),
        "sources": [],
    }
    for src in sources:
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

    with open(path, "w") as f:
        json.dump(data, f, indent=2)
