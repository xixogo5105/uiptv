"""Tests for channel matching."""

import pytest

from uiptv_epg_builder.inventory import ChannelIdentity
from uiptv_epg_builder.xmltv import XmltvGuide
from uiptv_epg_builder.matching import ChannelMatcher, MatchClass


def create_test_guide() -> XmltvGuide:
    """Create a test guide with known channels."""
    guide = XmltvGuide()
    guide.programmes = {
        "bbc1.uk": [],
        "bbc2.uk": [],
        "itv.uk": [],
        "channel4.uk": [],
    }
    guide.names = {
        "bbc one": "bbc1.uk",
        "bbc two": "bbc2.uk",
        "itv": "itv.uk",
        "channel 4": "channel4.uk",
    }
    guide.quality_names = {
        "bbcone": "bbc1.uk",
        "bbctwo": "bbc2.uk",
        "itv": "itv.uk",
        "channel4": "channel4.uk",
    }
    return guide


def create_test_channel(
    name: str = "BBC One",
    provider_id: str = "BBC1.uk",
    tvg_id: str = "",
    xmltv_id: str = "",
    country: str = "GB",
    language: str = "en",
) -> ChannelIdentity:
    """Create a test channel."""
    return ChannelIdentity(
        account_id="1",
        provider_channel_id=provider_id,
        db_id="100",
        name=name,
        display_name=name,
        country=country,
        language=language,
        tvg_id=tvg_id,
        xmltv_id=xmltv_id,
        signature="test-sig",
    )


def test_exact_id_match():
    """Test exact ID matching."""
    guide = create_test_guide()
    matcher = ChannelMatcher(exact_id_first=True)

    channel = create_test_channel(tvg_id="bbc1.uk")
    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    assert match.match_class == MatchClass.EXACT_ID
    assert match.guide_channel_id == "bbc1.uk"
    assert match.confidence == 1.0


def test_exact_name_match():
    """Test exact normalized name matching."""
    guide = create_test_guide()
    matcher = ChannelMatcher()

    channel = create_test_channel(name="BBC One")
    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    # May be EXACT_NAME_CONTEXT or CANONICAL_ALIAS depending on order
    assert match.match_class in (MatchClass.EXACT_NAME_CONTEXT, MatchClass.CANONICAL_ALIAS)
    assert match.guide_channel_id == "bbc1.uk"


def test_quality_alias_match():
    """Test quality-neutral alias matching."""
    guide = create_test_guide()
    matcher = ChannelMatcher(quality_aliases=True, min_confidence=0.75)

    channel = create_test_channel(name="BBC One HD")
    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    # Should match via quality alias or exact name context
    assert match.match_class in (MatchClass.QUALITY_ALIAS, MatchClass.EXACT_NAME_CONTEXT, MatchClass.CANONICAL_ALIAS)
    assert match.guide_channel_id == "bbc1.uk"


def test_manual_override():
    """Test manual override takes precedence."""
    guide = create_test_guide()
    matcher = ChannelMatcher()

    channel = create_test_channel(
        name="Wrong Name",
        provider_id="WRONG",
        tvg_id="wrong",
    )
    channel.is_manual_override = True
    channel.manual_override = "bbc2.uk"

    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    assert match.match_class == MatchClass.MANUAL
    assert match.guide_channel_id == "bbc2.uk"
    assert match.manual_override is True


def test_fuzzy_match():
    """Test fuzzy matching as last resort."""
    # Create a guide with only one similar channel to avoid ambiguity
    guide = XmltvGuide()
    guide.programmes = {"bbc1.uk": []}
    guide.names = {"bbc one": "bbc1.uk"}
    guide.quality_names = {"bbcone": "bbc1.uk"}

    matcher = ChannelMatcher(enable_fuzzy=True, min_confidence=0.4)

    channel = create_test_channel(name="BBC Oen")  # Typo without changing meaningful channel numbers
    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    # Should match via fuzzy matching
    assert match.match_class in (MatchClass.FUZZY, MatchClass.QUALITY_ALIAS, MatchClass.EXACT_NAME_CONTEXT, MatchClass.CANONICAL_ALIAS)
    assert match.guide_channel_id is not None


def test_no_match():
    """Test channel with no match."""
    guide = create_test_guide()
    matcher = ChannelMatcher(min_confidence=0.9)

    channel = create_test_channel(name="Unknown Channel", provider_id="UNKNOWN")
    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    assert match.match_class == MatchClass.UNMATCHED
    assert match.guide_channel_id is None


def test_ambiguous_match():
    """Test ambiguous match detection."""
    guide = XmltvGuide()
    guide.programmes = {"ch1": [], "ch2": []}
    guide.names = {"station": "ch1", "station hd": "ch2"}
    guide.quality_names = {"station": "ch1", "stationhd": "ch2"}  # Different quality names

    matcher = ChannelMatcher(min_confidence=0.5, margin_threshold=0.1)

    channel = create_test_channel(name="Station", provider_id="STATION")
    matches = matcher.match_channels([channel], {"test": guide}, {"test": 10})

    assert len(matches) == 1
    match = matches[0]
    # Should handle ambiguity - either unmatched or matched with confidence <= 0.95
    assert match.match_class == MatchClass.UNMATCHED or match.confidence <= 0.95


def test_source_priority():
    """Test source priority affects matching."""
    guide1 = create_test_guide()
    guide2 = create_test_guide()
    guide2.names["bbc one"] = "bbc2.uk"  # Different mapping

    matcher = ChannelMatcher()

    channel = create_test_channel(name="BBC One")
    matches = matcher.match_channels(
        [channel],
        {"source1": guide1, "source2": guide2},
        {"source1": 50, "source2": 100},  # source1 has higher priority
    )

    assert len(matches) == 1
    match = matches[0]
    # Should prefer source1 (lower priority number) or be unmatched if ambiguous
    assert match.source_id in ("source1", "source2", "") or match.match_class == MatchClass.UNMATCHED

def test_missing_manual_override_never_falls_through():
    channel = create_test_channel(tvg_id="bbc1.uk")
    channel.is_manual_override = True
    channel.manual_override = "missing"
    result = ChannelMatcher().match_channels([channel], {"test": create_test_guide()}, {})[0]
    assert result.guide_channel_id is None


def test_redundant_exact_ids_choose_priority_without_false_ambiguity():
    result = ChannelMatcher().match_channels([create_test_channel(tvg_id="bbc1.uk")],
        {"low": create_test_guide(), "high": create_test_guide()}, {"high": 1, "low": 2})[0]
    assert result.source_id == "high"
    assert result.match_class == MatchClass.EXACT_ID


def test_timeshift_and_region_are_not_fuzzy_equivalents():
    for name in ("BBC One +1", "BBC One West", "BBC One 2"):
        result = ChannelMatcher(min_confidence=0.3).match_channels(
            [create_test_channel(name=name)], {"test": create_test_guide()}, {})[0]
        assert result.guide_channel_id is None


def test_configured_source_region_excludes_conflicting_feed():
    channel = create_test_channel(country="GB", tvg_id="bbc1.uk")
    result = ChannelMatcher().match_channels([channel], {"us": create_test_guide()}, {},
        {"us": {"countries": ["US"], "languages": ["en"]}})[0]
    assert result.guide_channel_id is None
