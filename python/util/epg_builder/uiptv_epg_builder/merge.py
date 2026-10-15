"""Schedule merging and validation for UIPTV EPG Builder."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any

from .xmltv import EpgProgramme, XmltvGuide
from .matching import ChannelMatch


@dataclass
class MergedSchedule:
    """Merged schedule for a channel."""
    channel_match: ChannelMatch
    programmes: list[EpgProgramme] = None
    source_contributions: dict[str, int] = None  # source_id -> programme count

    def __post_init__(self):
        if self.programmes is None:
            self.programmes = []
        if self.source_contributions is None:
            self.source_contributions = {}

    def to_dict(self) -> dict[str, Any]:
        return {
            "channel_match": self.channel_match.to_dict(),
            "programmes": [p.to_dict() for p in self.programmes],
            "source_contributions": self.source_contributions,
        }


class ScheduleMerger:
    """Merges schedules from multiple sources with priority handling."""

    def __init__(
        self,
        past_hours: int = 6,
        future_days: int = 7,
        max_programmes_per_channel: int = 2000,
        bridge=None,
    ):
        from .bridge import JavaBridge
        self.bridge = bridge or JavaBridge()
        self.past_cutoff_hours = past_hours
        self.future_days = future_days
        self.max_programmes_per_channel = max_programmes_per_channel

    def merge_schedules(
        self,
        matches: list[ChannelMatch],
        guides: dict[str, XmltvGuide],
        source_priorities: dict[str, int],
    ) -> list[MergedSchedule]:
        """Merge schedules for all matched channels."""
        # Guide IDs are source-local. Cross-source candidates must have independently
        # established the same exact ID; names alone never establish equivalence.
        requests = []
        selected = []
        for match in matches:
            sources = sorted(
                [(source_priorities.get(source, 999), source, guide.programmes[match.guide_channel_id])
                 for source, guide in guides.items()
                 if match.guide_channel_id and match.guide_channel_id in guide.programmes
                 and (source == match.source_id or any(c.source_id == source and c.guide_channel_id == match.guide_channel_id
                      and c.match_class.value in ("EXACT_ID", "MANUAL") for c in match.candidates))],
                key=lambda entry: (entry[0], entry[1]),
            ) if match.source_id else []
            selected.append(sources)
            requests.append([[p.to_dict() for p in programmes] for _, _, programmes in sources])
        merged = self.bridge.merge_schedules(requests)
        now = int(datetime.now(timezone.utc).timestamp() * 1000)
        results = []
        for match, sources, entries in zip(matches, selected, merged, strict=True):
            programmes = [EpgProgramme.from_dict(p) for p in entries
                          if p["stop"] > now - self.past_cutoff_hours * 3600000
                          and p["start"] < now + self.future_days * 86400000
                          and p["title"].strip()][:self.max_programmes_per_channel]
            # These are input counts; merged provenance is not inferred from title equality.
            contributions = {source: len(entries) for _, source, entries in sources}
            results.append(MergedSchedule(match, programmes, contributions))
        return results

    def validate_schedule(self, programmes: list[EpgProgramme]) -> list[str]:
        """Validate a merged schedule, return list of warnings."""
        warnings = []

        if not programmes:
            warnings.append("No programmes in schedule")
            return warnings

        # Check for gaps
        for i in range(len(programmes) - 1):
            gap = programmes[i + 1].start - programmes[i].stop
            if gap > 300000:  # 5 minutes
                warnings.append(f"Gap of {gap // 60000} minutes between programmes")

        # Check for overlaps
        for i in range(len(programmes) - 1):
            if programmes[i].stop > programmes[i + 1].start:
                overlap = programmes[i].stop - programmes[i + 1].start
                warnings.append(f"Overlap of {overlap // 60000} minutes between programmes")

        # Check for very short programmes
        for p in programmes:
            duration = p.stop - p.start
            if duration < 60000:  # Less than 1 minute
                warnings.append(f"Very short programme: {p.title} ({duration // 1000}s)")

        return warnings