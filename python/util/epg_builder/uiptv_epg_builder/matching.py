"""Channel matching logic for UIPTV EPG Builder."""

from __future__ import annotations

from dataclasses import dataclass, field
from difflib import SequenceMatcher
from enum import Enum
import re
from typing import Any

from .inventory import ChannelIdentity
from .normalize import (
    normalize_name,
    normalize_quality_name,
    get_quality_variants,
    extract_country_from_name,
    extract_language_from_name,
)
from .xmltv import XmltvGuide


class MatchClass(Enum):
    """Match confidence classes."""
    MANUAL = "MANUAL"
    EXACT_ID = "EXACT_ID"
    CANONICAL_ALIAS = "CANONICAL_ALIAS"
    EXACT_NAME_CONTEXT = "EXACT_NAME_CONTEXT"
    QUALITY_ALIAS = "QUALITY_ALIAS"
    FUZZY = "FUZZY"
    UNMATCHED = "UNMATCHED"


@dataclass
class MatchCandidate:
    """A candidate match with scoring."""
    guide_channel_id: str
    guide_channel_name: str
    score: float
    match_class: MatchClass
    reason: str
    source_id: str


@dataclass
class ChannelMatch:
    """Final match result for a channel."""
    channel: ChannelIdentity
    guide_channel_id: str | None = None
    guide_channel_name: str = ""
    source_id: str = ""
    match_class: MatchClass = MatchClass.UNMATCHED
    confidence: float = 0.0
    reason: str = ""
    candidates: list[MatchCandidate] = field(default_factory=list)
    manual_override: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "account_id": self.channel.account_id,
            "provider_channel_id": self.channel.provider_channel_id,
            "db_id": self.channel.db_id,
            "channel_name": self.channel.name,
            "guide_channel_id": self.guide_channel_id,
            "guide_channel_name": self.guide_channel_name,
            "source_id": self.source_id,
            "match_class": self.match_class.value,
            "confidence": self.confidence,
            "reason": self.reason,
            "manual_override": self.manual_override,
            "candidates": [
                {
                    "guide_channel_id": c.guide_channel_id,
                    "guide_channel_name": c.guide_channel_name,
                    "score": c.score,
                    "match_class": c.match_class.value,
                    "reason": c.reason,
                    "source_id": c.source_id,
                }
                for c in self.candidates
            ],
        }


class ChannelMatcher:
    """Matches UIPTV channels to XMLTV guide channels."""

    def __init__(
        self,
        min_confidence: float = 0.85,
        margin_threshold: float = 0.10,
        enable_fuzzy: bool = True,
        quality_aliases: bool = True,
        exact_id_first: bool = True,
        canonical_alias_first: bool = True,
    ):
        self.min_confidence = min_confidence
        self.margin_threshold = margin_threshold
        self.enable_fuzzy = enable_fuzzy
        self.quality_aliases = quality_aliases
        self.exact_id_first = exact_id_first
        self.canonical_alias_first = canonical_alias_first

    def match_channels(
        self,
        channels: list[ChannelIdentity],
        guides: dict[str, XmltvGuide],
        source_priorities: dict[str, int],
        source_context: dict | None = None,
    ) -> list[ChannelMatch]:
        """Match all channels to guide channels."""
        results = []

        for channel in channels:
            context = source_context or {}
            country = channel.country or extract_country_from_name(channel.name)
            language = channel.language or extract_language_from_name(channel.name)
            canonical_country = lambda value: {"UK": "GB", "USA": "US", "CAN": "CA", "AUS": "AU"}.get(value.upper(), value.upper())
            eligible = {source: guide for source, guide in guides.items()
                        if (not country or not context.get(source, {}).get("countries") or canonical_country(country) in
                            [canonical_country(c) for c in context[source]["countries"]])
                        and (not language or not context.get(source, {}).get("languages") or language in context[source]["languages"])}
            match = self._match_single_channel(channel, eligible, source_priorities)
            results.append(match)

        return results

    def _match_single_channel(
        self,
        channel: ChannelIdentity,
        guides: dict[str, XmltvGuide],
        source_priorities: dict[str, int],
    ) -> ChannelMatch:
        """Match a single channel."""
        candidates: list[MatchCandidate] = []

        # Stage 1: Manual override
        if channel.is_manual_override and channel.manual_override:
            for source_id, guide in guides.items():
                if channel.manual_override in guide.programmes:
                    candidates.append(MatchCandidate(
                        guide_channel_id=channel.manual_override,
                        guide_channel_name=guide.names.get(
                            normalize_name(channel.manual_override), channel.manual_override
                        ),
                        score=1.0,
                        match_class=MatchClass.MANUAL,
                        reason="Manual override",
                        source_id=source_id,
                    ))

            if not candidates:
                return ChannelMatch(channel=channel, reason="Manual override unavailable; preserved for review")
            best = min(candidates, key=lambda c: (source_priorities.get(c.source_id, 999), c.source_id))
            return ChannelMatch(channel, best.guide_channel_id, best.guide_channel_name, best.source_id,
                                MatchClass.MANUAL, 1.0, best.reason, candidates, True)

        # Stage 2: Exact ID matches
        if self.exact_id_first:
            for id_field in ["guide_id", "tvg_id", "xmltv_id", "epg_id", "epg_channel_id"]:
                id_value = getattr(channel, id_field, "")
                if id_value:
                    for source_id, guide in guides.items():
                        if id_value in guide.programmes:
                            candidates.append(MatchCandidate(
                                guide_channel_id=id_value,
                                guide_channel_name=guide.names.get(
                                    normalize_name(id_value), id_value
                                ),
                                score=1.0,
                                match_class=MatchClass.EXACT_ID,
                                reason=f"Exact {id_field} match",
                                source_id=source_id,
                            ))

        # Stage 4: Exact normalized name with context
        channel_country = channel.country or extract_country_from_name(channel.name)
        channel_language = channel.language or extract_language_from_name(channel.name)

        for source_id, guide in guides.items():
            # Check exact normalized name
            norm_name = normalize_name(channel.name)
            if norm_name in guide.names:
                guide_id = guide.names[norm_name]

                candidates.append(MatchCandidate(
                    guide_channel_id=guide_id,
                    guide_channel_name=guide_id,
                    score=0.9,
                    match_class=MatchClass.EXACT_NAME_CONTEXT,
                    reason=f"Exact name match (country={channel_country}, lang={channel_language})",
                    source_id=source_id,
                ))

            # Check display name
            norm_display = normalize_name(channel.display_name)
            if norm_display in guide.names and norm_display != norm_name:
                candidates.append(MatchCandidate(
                    guide_channel_id=guide.names[norm_display],
                    guide_channel_name=guide.names[norm_display],
                    score=0.88,
                    match_class=MatchClass.EXACT_NAME_CONTEXT,
                    reason="Exact display name match",
                    source_id=source_id,
                ))

        # Stage 5: Quality-neutral aliases
        if self.quality_aliases:
            for source_id, guide in guides.items():
                quality_norm = normalize_quality_name(channel.name)
                if quality_norm in guide.quality_names:
                    candidates.append(MatchCandidate(
                        guide_channel_id=guide.quality_names[quality_norm],
                        guide_channel_name=guide.quality_names[quality_norm],
                        score=0.8,
                        match_class=MatchClass.QUALITY_ALIAS,
                        reason="Quality-neutral name match",
                        source_id=source_id,
                    ))

                # Check quality variants
                for variant in get_quality_variants(channel.name):
                    if variant in guide.names:
                        candidates.append(MatchCandidate(
                            guide_channel_id=guide.names[variant],
                            guide_channel_name=guide.names[variant],
                            score=0.78,
                            match_class=MatchClass.QUALITY_ALIAS,
                            reason=f"Quality variant match: {variant}",
                            source_id=source_id,
                        ))

        # Stage 6: Fuzzy matching (last resort)
        if self.enable_fuzzy and not candidates:
            for source_id, guide in guides.items():
                for guide_name, guide_id in guide.names.items():
                    # Fuzzy similarity must not erase region, numbers or timeshift.
                    protected = lambda name: set(re.findall(r"\+\d+|\b\d+\b|\b(?:east|west|north|south|scotland|london|uk|us|gb)\b", normalize_name(name)))
                    if protected(channel.name) != protected(guide_name):
                        continue
                    similarity = SequenceMatcher(None, normalize_name(channel.name), guide_name).ratio()
                    if similarity >= self.min_confidence:
                        candidates.append(MatchCandidate(
                            guide_channel_id=guide_id,
                            guide_channel_name=guide_name,
                            score=similarity,
                            match_class=MatchClass.FUZZY,
                            reason=f"Fuzzy match ({similarity:.2f})",
                            source_id=source_id,
                        ))

        # Deduplicate candidates (keep highest score per guide_channel_id)
        unique_candidates: dict[str, MatchCandidate] = {}
        for c in candidates:
            key = f"{c.source_id}:{c.guide_channel_id}"
            if key not in unique_candidates or c.score > unique_candidates[key].score:
                unique_candidates[key] = c

        candidates = list(unique_candidates.values())

        # Sort by score (descending), then by source priority
        candidates.sort(key=lambda c: (-c.score, source_priorities.get(c.source_id, 999)))

        # Select best match
        result = ChannelMatch(channel=channel, candidates=candidates)

        if not candidates:
            result.match_class = MatchClass.UNMATCHED
            result.reason = "No suitable match found"
            return result

        best = candidates[0]
        result.guide_channel_id = best.guide_channel_id
        result.guide_channel_name = best.guide_channel_name
        result.source_id = best.source_id
        result.match_class = best.match_class
        result.confidence = best.score
        result.reason = best.reason
        result.manual_override = best.match_class == MatchClass.MANUAL

        # Check margin threshold for non-manual matches
        if best.match_class != MatchClass.MANUAL and len(candidates) > 1:
            runner_up = next((candidate for candidate in candidates[1:] if candidate.guide_channel_id != best.guide_channel_id), None)
            if runner_up and best.score - runner_up.score < self.margin_threshold:
                # Ambiguous - mark as unmatched but keep candidates for review
                result.match_class = MatchClass.UNMATCHED
                result.confidence = best.score
                result.reason = f"Ambiguous: top candidate {best.score:.2f}, runner-up {runner_up.score:.2f} (margin < {self.margin_threshold})"
                result.guide_channel_id = None
                result.source_id = ""

        # Check minimum confidence
        if result.confidence < self.min_confidence and best.match_class != MatchClass.MANUAL:
            result.match_class = MatchClass.UNMATCHED
            result.reason = f"Below confidence threshold ({result.confidence:.2f} < {self.min_confidence})"
            result.guide_channel_id = None
            result.source_id = ""

        return result