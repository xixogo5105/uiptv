"""XMLTV parsing for UIPTV EPG Builder."""

from __future__ import annotations

import gzip
import lzma
from dataclasses import dataclass, field
from datetime import datetime, timezone
from io import BytesIO
from typing import Any

from .normalize import normalize_name, normalize_quality_name


@dataclass
class EpgProgramme:
    """EPG programme entry."""
    title: str
    description: str
    start: int  # UTC epoch milliseconds
    stop: int   # UTC epoch milliseconds

    def to_dict(self) -> dict[str, Any]:
        return {
            "title": self.title,
            "description": self.description,
            "start": self.start,
            "stop": self.stop,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> EpgProgramme:
        return cls(
            title=data.get("title", ""),
            description=data.get("description", ""),
            start=data.get("start", 0),
            stop=data.get("stop", 0),
        )


@dataclass
class XmltvGuide:
    """Parsed XMLTV guide."""
    programmes: dict[str, list[EpgProgramme]] = field(default_factory=dict)
    names: dict[str, str] = field(default_factory=dict)  # normalized name -> channel id
    quality_names: dict[str, str] = field(default_factory=dict)  # normalized quality name -> channel id
    icons: dict[str, str] = field(default_factory=dict)  # channel id -> icon URL

    def get_channel_programmes(self, channel_id: str) -> list[EpgProgramme]:
        """Get programmes for a channel."""
        return self.programmes.get(channel_id, [])

    def find_channel_by_name(self, name: str) -> str | None:
        """Find channel ID by normalized name."""
        return self.names.get(normalize_name(name))

    def find_channel_by_quality_name(self, name: str) -> str | None:
        """Find channel ID by normalized quality name."""
        return self.quality_names.get(normalize_quality_name(name))

    def to_dict(self) -> dict[str, Any]:
        return {
            "programmes": {k: [p.to_dict() for p in v] for k, v in self.programmes.items()},
            "names": self.names,
            "quality_names": self.quality_names,
            "icons": self.icons,
        }

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> XmltvGuide:
        guide = cls()
        guide.programmes = {
            k: [EpgProgramme.from_dict(p) for p in v]
            for k, v in data.get("programmes", {}).items()
        }
        guide.names = data.get("names", {})
        guide.quality_names = data.get("quality_names", {})
        guide.icons = data.get("icons", {})
        return guide


MAX_XML_BYTES = 64 * 1024 * 1024


def parse_xmltv(xml_content: bytes, now: datetime | None = None, bridge=None) -> XmltvGuide:
    """Decompress bounded input, then use Java core's XMLTV parser and time rules."""
    from .bridge import JavaBridge
    if xml_content[:2] == b"\x1f\x8b":
        opener = lambda stream: gzip.GzipFile(fileobj=stream)
    elif xml_content[:6] == b"\xfd7zXZ\x00":
        opener = lzma.LZMAFile
    else:
        opener = None
    if opener:
        with opener(BytesIO(xml_content)) as stream:
            xml_content = stream.read(MAX_XML_BYTES + 1)
    if len(xml_content) > MAX_XML_BYTES:
        raise ValueError("XMLTV exceeds decompressed size limit")
    return XmltvGuide.from_dict((bridge or JavaBridge()).parse_xmltv(xml_content, now or datetime.now(timezone.utc)))
