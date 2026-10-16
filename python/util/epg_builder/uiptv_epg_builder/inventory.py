"""Inventory export and channel data models for UIPTV EPG Builder."""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .config import Config


@dataclass
class ChannelIdentity:
    """Identity of a channel for EPG matching."""
    account_id: str
    provider_channel_id: str
    db_id: str  # Local database row ID
    name: str
    display_name: str
    quality_name: str | None = None
    country: str = ""
    language: str = ""
    guide_id: str = ""
    tvg_id: str = ""
    xmltv_id: str = ""
    epg_id: str = ""
    epg_channel_id: str = ""
    extra_json: str = ""
    signature: str = ""
    is_bookmark: bool = False
    bookmark_id: str = ""
    manual_override: str = ""
    is_manual_override: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "account_id": self.account_id,
            "provider_channel_id": self.provider_channel_id,
            "db_id": self.db_id,
            "name": self.name,
            "display_name": self.display_name,
            "quality_name": self.quality_name,
            "country": self.country,
            "language": self.language,
            "guide_id": self.guide_id,
            "tvg_id": self.tvg_id,
            "xmltv_id": self.xmltv_id,
            "epg_id": self.epg_id,
            "epg_channel_id": self.epg_channel_id,
            "extra_json": self.extra_json,
            "signature": self.signature,
            "is_bookmark": self.is_bookmark,
            "bookmark_id": self.bookmark_id,
            "manual_override": self.manual_override,
            "is_manual_override": self.is_manual_override,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> ChannelIdentity:
        return cls(
            account_id=data.get("account_id", ""),
            provider_channel_id=data.get("provider_channel_id", ""),
            db_id=data.get("db_id", ""),
            name=data.get("name", ""),
            display_name=data.get("display_name", ""),
            quality_name=data.get("quality_name"),
            country=data.get("country", ""),
            language=data.get("language", ""),
            guide_id=data.get("guide_id", ""),
            tvg_id=data.get("tvg_id", ""),
            xmltv_id=data.get("xmltv_id", ""),
            epg_id=data.get("epg_id", ""),
            epg_channel_id=data.get("epg_channel_id", ""),
            extra_json=data.get("extra_json", ""),
            signature=data.get("signature", ""),
            is_bookmark=data.get("is_bookmark", False),
            bookmark_id=data.get("bookmark_id", ""),
            manual_override=data.get("manual_override", ""),
            is_manual_override=data.get("is_manual_override", False),
        )


@dataclass
class AccountInfo:
    """Account information for EPG matching context."""
    db_id: str
    name: str
    type: str  # STALKER_PORTAL, XTREME_API, M3U8_URL, M3U8_LOCAL
    url: str = ""
    server_portal_url: str = ""
    username: str = ""
    mac_address: str = ""
    epg_source: str = ""
    m3u8_path: str = ""
    timezone: str = "Europe/London"


@dataclass
class InventoryExport:
    """Complete inventory export from Java bridge."""
    database_path: str
    database_identity: str  # Opaque Java database identity
    generation: int
    revision: str  # Timestamp or hash of inventory generation
    accounts: list[AccountInfo] = field(default_factory=list)
    channels: list[ChannelIdentity] = field(default_factory=list)
    name_rules: list[dict[str, str]] = field(default_factory=list)
    config_signature: str = ""
    global_epg_source: str = ""
    epg_mappings_json: str = ""

    def to_json(self) -> str:
        return json.dumps({
            "database_path": self.database_path,
            "database_identity": self.database_identity,
            "generation": self.generation,
            "revision": self.revision,
            "accounts": [acc.__dict__ for acc in self.accounts],
            "channels": [ch.to_dict() for ch in self.channels],
            "name_rules": self.name_rules,
            "config_signature": self.config_signature,
            "global_epg_source": self.global_epg_source,
            "epg_mappings_json": self.epg_mappings_json,
        }, indent=2)

    @classmethod
    def from_json_lines(cls, lines) -> InventoryExport:
        lines = iter(lines)
        first = next(lines, None)
        if first is None:
            raise ValueError("Missing inventory header")
        header = json.loads(first)
        if header.pop("record_type", None) != "header":
            raise ValueError("Missing inventory header")
        inventory = cls.from_json(json.dumps(header))
        for line in lines:
            record = json.loads(line)
            kind = record.pop("record_type", None)
            if kind == "channel":
                inventory.channels.append(ChannelIdentity.from_dict(record))
            elif kind == "end":
                if record["channel_count"] != len(inventory.channels) or not record["revision"]:
                    raise ValueError("Incomplete inventory export")
                if any(line.strip() for line in lines):
                    raise ValueError("Unexpected data after inventory completion")
                inventory.revision = record["revision"]
                return inventory
            else:
                raise ValueError("Invalid inventory record")
        raise ValueError("Incomplete inventory export")

    @classmethod
    def from_json(cls, json_str: str) -> InventoryExport:
        data = json.loads(json_str)
        accounts = [AccountInfo(**acc) for acc in data.get("accounts", [])]
        channels = [ChannelIdentity.from_dict(ch) for ch in data.get("channels", [])]
        return cls(
            database_path=data.get("database_path", ""),
            database_identity=data.get("database_identity", ""),
            generation=data.get("generation", 0),
            revision=data.get("revision", ""),
            accounts=accounts,
            channels=channels,
            name_rules=data.get("name_rules", []),
            config_signature=data.get("config_signature", ""),
            global_epg_source=data.get("global_epg_source", ""),
            epg_mappings_json=data.get("epg_mappings_json", ""),
        )


class InventoryExporter:
    """Exports channel inventory from UIPTV database via Java bridge."""

    def __init__(self, config: Config):
        self.config = config
        self.bridge_config = config.bridge

    def export_inventory(self) -> InventoryExport:
        """Use UIPTV's Java resolver; no builder database configuration exists."""
        from .bridge import bridge_from_config
        return bridge_from_config(self.bridge_config).export_inventory()


def create_test_inventory() -> InventoryExport:
    """Create a test inventory for development/testing."""
    return InventoryExport(
        database_path="/tmp/test.db",
        database_identity="test-db-identity",
        generation=1,
        revision="2024-01-01T00:00:00Z",
        accounts=[
            AccountInfo(
                db_id="1",
                name="Test Account",
                type="M3U8_URL",
                url="http://example.com/playlist.m3u",
                epg_source="http://example.com/epg.xml.gz",
            ),
        ],
        channels=[
            ChannelIdentity(
                account_id="1",
                provider_channel_id="BBC1.uk",
                db_id="100",
                name="BBC One",
                display_name="BBC One HD",
                country="GB",
                language="en",
                tvg_id="BBC1.uk",
                xmltv_id="BBC1.uk",
                epg_id="bbc1",
                signature="abc123",
            ),
            ChannelIdentity(
                account_id="1",
                provider_channel_id="BBC2.uk",
                db_id="101",
                name="BBC Two",
                display_name="BBC Two HD",
                country="GB",
                language="en",
                tvg_id="BBC2.uk",
                xmltv_id="BBC2.uk",
                epg_id="bbc2",
                signature="def456",
            ),
        ],
        config_signature="config-sig-123",
        global_epg_source="",
        epg_mappings_json="{}",
    )