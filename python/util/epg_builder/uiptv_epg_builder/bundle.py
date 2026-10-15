"""Bundle format for UIPTV EPG Builder - import/export data structure."""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .inventory import InventoryExport
from .matching import ChannelMatch
from .merge import MergedSchedule
from .xmltv import XmltvGuide


BUNDLE_SCHEMA_VERSION = 1


@dataclass
class BundleSourceInfo:
    """Source information in bundle."""
    source_id: str
    name: str
    type: str
    url: str = ""
    priority: int = 100
    fetched_at: str = ""
    size_bytes: int = 0
    programme_count: int = 0
    channel_count: int = 0
    content_hash: str = ""
    warning: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "source_id": self.source_id,
            "name": self.name,
            "type": self.type,
            "url": self.url,
            "priority": self.priority,
            "fetched_at": self.fetched_at,
            "size_bytes": self.size_bytes,
            "programme_count": self.programme_count,
            "channel_count": self.channel_count,
            "content_hash": self.content_hash,
            "warning": self.warning,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> BundleSourceInfo:
        return cls(**data)


@dataclass
class BundleChannelMapping:
    """Channel mapping in bundle."""
    account_id: str
    provider_channel_id: str
    db_id: str
    signature: str
    guide_channel_id: str
    guide_channel_name: str
    source_id: str
    match_class: str
    confidence: float
    reason: str
    manual_override: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "account_id": self.account_id,
            "provider_channel_id": self.provider_channel_id,
            "db_id": self.db_id,
            "signature": self.signature,
            "guide_channel_id": self.guide_channel_id,
            "guide_channel_name": self.guide_channel_name,
            "source_id": self.source_id,
            "match_class": self.match_class,
            "confidence": self.confidence,
            "reason": self.reason,
            "manual_override": self.manual_override,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> BundleChannelMapping:
        return cls(**data)


@dataclass
class BundleProgramme:
    """Programme in bundle."""
    source_id: str
    guide_channel_id: str
    start: int
    stop: int
    title: str
    description: str = ""

    def to_dict(self) -> dict[str, Any]:
        return {
            "source_id": self.source_id,
            "guide_channel_id": self.guide_channel_id,
            "start": self.start,
            "stop": self.stop,
            "title": self.title,
            "description": self.description,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> BundleProgramme:
        return cls(**data)


@dataclass
class EpgBundle:
    """Complete EPG bundle for import."""
    schema_version: int = BUNDLE_SCHEMA_VERSION
    created_at: str = ""
    generation_id: str = ""
    database_path: str = ""
    database_identity: str = ""
    inventory_revision: str = ""
    inventory_generation: int = 0
    config_signature: str = ""
    sources: list[BundleSourceInfo] = field(default_factory=list)
    mappings: list[BundleChannelMapping] = field(default_factory=list)
    programmes: list[BundleProgramme] = field(default_factory=list)
    validation_summary: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self):
        if not self.created_at:
            self.created_at = datetime.now(timezone.utc).isoformat()
        if not self.generation_id:
            self.generation_id = hashlib.sha256(
                f"{self.database_identity}{self.created_at}".encode()
            ).hexdigest()[:16]

    def to_json(self, indent: int = 2) -> str:
        return json.dumps({
            "schema_version": self.schema_version,
            "created_at": self.created_at,
            "generation_id": self.generation_id,
            "database_path": self.database_path,
            "database_identity": self.database_identity,
            "inventory_revision": self.inventory_revision,
            "inventory_generation": self.inventory_generation,
            "config_signature": self.config_signature,
            "sources": [s.to_dict() for s in self.sources],
            "mappings": [m.to_dict() for m in self.mappings],
            "programmes": [p.to_dict() for p in self.programmes],
            "validation_summary": self.validation_summary,
        }, indent=indent)

    @classmethod
    def from_json(cls, json_str: str) -> EpgBundle:
        data = json.loads(json_str)
        return cls(
            schema_version=data.get("schema_version", BUNDLE_SCHEMA_VERSION),
            created_at=data.get("created_at", ""),
            generation_id=data.get("generation_id", ""),
            database_path=data.get("database_path", ""),
            database_identity=data.get("database_identity", ""),
            inventory_revision=data.get("inventory_revision", ""),
            inventory_generation=data.get("inventory_generation", 0),
            config_signature=data.get("config_signature", ""),
            sources=[BundleSourceInfo.from_dict(s) for s in data.get("sources", [])],
            mappings=[BundleChannelMapping.from_dict(m) for m in data.get("mappings", [])],
            programmes=[BundleProgramme.from_dict(p) for p in data.get("programmes", [])],
            validation_summary=data.get("validation_summary", {}),
        )

    def save(self, path: str | Path) -> None:
        """Save bundle to file."""
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w") as f:
            f.write(self.to_json())

    @classmethod
    def load(cls, path: str | Path) -> EpgBundle:
        """Load bundle from file."""
        with open(path) as f:
            return cls.from_json(f.read())


class BundleBuilder:
    """Builds EPG bundle from matched data."""

    def __init__(self, inventory: InventoryExport):
        self.inventory = inventory

    def build_bundle(
        self,
        matches: list[ChannelMatch],
        merged_schedules: list[MergedSchedule],
        guides: dict[str, XmltvGuide],
        source_configs: dict[str, Any],
        validation_summary: dict[str, Any] | None = None,
    ) -> EpgBundle:
        """Build bundle from all processed data."""
        bundle = EpgBundle(
            database_path=self.inventory.database_path,
            database_identity=self.inventory.database_identity,
            inventory_revision=self.inventory.revision,
            inventory_generation=self.inventory.generation,
            config_signature=self.inventory.config_signature,
        )

        # Add source info
        for source_id, guide in guides.items():
            config = source_configs.get(source_id, {})
            programmes_count = sum(len(p) for p in guide.programmes.values())
            channels_count = len(guide.programmes)

            bundle.sources.append(BundleSourceInfo(
                source_id=source_id,
                name=config.get("name", source_id),
                type=config.get("type", "unknown"),
                url=config.get("url", ""),
                priority=config.get("priority", 100),
                fetched_at=datetime.now(timezone.utc).isoformat(),
                size_bytes=config.get("size_bytes", 0),
                programme_count=programmes_count,
                channel_count=channels_count,
                content_hash=config.get("content_hash", ""),
            ))

        now = int(datetime.fromisoformat(bundle.created_at).timestamp() * 1000)
        # Apply the publication window once per source/guide at the bundle timestamp.
        schedules = {
            (source, guide_id): [p for p in programmes
                                if p.stop > now - 6 * 3600000 and p.start < now + 7 * 86400000]
            for source, guide in guides.items() for guide_id, programmes in guide.programmes.items()
        }

        # Keep each successful source separate. A failed source is absent, so Java
        # retains its last published data instead of replacing a synthetic aggregate.
        required = set()
        seen_mappings = set()
        for match in matches:
            if not match.guide_channel_id or not match.source_id:
                continue
            equivalent = {match.source_id}
            if match.match_class.value in ("MANUAL", "EXACT_ID"):
                equivalent.update(c.source_id for c in match.candidates
                                  if c.guide_channel_id == match.guide_channel_id
                                  and c.match_class.value in ("MANUAL", "EXACT_ID"))
            for source_id in sorted(equivalent, key=lambda source: (source_configs.get(source, {}).get("priority", 100), source)):
                guide = guides.get(source_id)
                if not guide or not any(p.stop > now for p in schedules.get((source_id, match.guide_channel_id), [])):
                    continue
                key = (match.channel.account_id, match.channel.provider_channel_id, source_id)
                if key in seen_mappings:
                    continue
                seen_mappings.add(key)
                required.add((source_id, match.guide_channel_id))
                bundle.mappings.append(BundleChannelMapping(
                    account_id=match.channel.account_id, provider_channel_id=match.channel.provider_channel_id,
                    db_id=match.channel.db_id, signature=match.channel.signature,
                    guide_channel_id=match.guide_channel_id, guide_channel_name=match.guide_channel_name,
                    source_id=source_id, match_class=match.match_class.value, confidence=match.confidence,
                    reason=match.reason, manual_override=match.manual_override))
        for source_id, guide_id in sorted(required, key=lambda key: (source_configs.get(key[0], {}).get("priority", 100), key)):
            for prog in schedules[source_id, guide_id]:
                bundle.programmes.append(BundleProgramme(source_id, guide_id, prog.start, prog.stop, prog.title, prog.description))

        # Add validation summary
        if validation_summary:
            bundle.validation_summary = validation_summary
        else:
            bundle.validation_summary = {
                "total_channels": len(matches),
                "matched_channels": sum(1 for m in matches if m.guide_channel_id),
                "unmatched_channels": sum(1 for m in matches if not m.guide_channel_id),
                "total_programmes": len(bundle.programmes),
                "total_sources": len(bundle.sources),
            }

        return bundle