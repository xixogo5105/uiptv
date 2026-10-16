"""Tests for bundle format."""

import pytest
from datetime import datetime, timezone

from uiptv_epg_builder.inventory import InventoryExport, AccountInfo, ChannelIdentity
from uiptv_epg_builder.matching import ChannelMatch, MatchClass
from uiptv_epg_builder.merge import MergedSchedule
from uiptv_epg_builder.bundle import EpgBundle, BundleBuilder, BundleSourceInfo, BundleChannelMapping, BundleProgramme
from uiptv_epg_builder.xmltv import XmltvGuide, EpgProgramme


def create_test_inventory() -> InventoryExport:
    """Create a test inventory."""
    return InventoryExport(
        database_path="/tmp/test.db",
        database_identity="test-db-id",
        generation=1,
        revision="2024-01-15T12:00:00Z",
        accounts=[
            AccountInfo(db_id="1", name="Test Account", type="M3U8_URL"),
        ],
        channels=[
            ChannelIdentity(
                account_id="1",
                provider_channel_id="BBC1.uk",
                db_id="100",
                name="BBC One",
                display_name="BBC One",
                country="GB",
                language="en",
                tvg_id="bbc1.uk",
                signature="sig123",
            ),
        ],
        config_signature="config-sig",
        global_epg_source="",
        epg_mappings_json="{}",
    )


def create_test_match() -> ChannelMatch:
    """Create a test match."""
    channel = ChannelIdentity(
        account_id="1",
        provider_channel_id="BBC1.uk",
        db_id="100",
        name="BBC One",
        display_name="BBC One",
        signature="sig123",
    )
    return ChannelMatch(
        channel=channel,
        guide_channel_id="bbc1.uk",
        guide_channel_name="BBC One",
        source_id="test-source",
        match_class=MatchClass.EXACT_ID,
        confidence=1.0,
        reason="Exact ID match",
    )


def create_test_merged() -> MergedSchedule:
    """Create a test merged schedule."""
    match = create_test_match()
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    return MergedSchedule(
        channel_match=match,
        programmes=[
            EpgProgramme(title="News", description="Latest news", start=now, stop=now + 3600000),
            EpgProgramme(title="Movie", description="Action movie", start=now + 3600000, stop=now + 7200000),
        ],
        source_contributions={"test-source": 2},
    )


def test_bundle_roundtrip():
    """Test bundle serialization roundtrip."""
    bundle = EpgBundle(
        database_path="/tmp/test.db",
        database_identity="test-db-id",
        inventory_revision="rev1",
        inventory_generation=1,
        config_signature="config-sig",
    )

    # Add source
    bundle.sources.append(BundleSourceInfo(
        source_id="test-source",
        name="Test Source",
        type="xmltv_url",
        url="http://example.com/epg.xml.gz",
        priority=50,
        fetched_at=datetime.now(timezone.utc).isoformat(),
        size_bytes=1024,
        programme_count=100,
        channel_count=50,
        content_hash="abc123",
    ))

    # Add mapping
    bundle.mappings.append(BundleChannelMapping(
        account_id="1",
        provider_channel_id="BBC1.uk",
        db_id="100",
        signature="sig123",
        guide_channel_id="bbc1.uk",
        guide_channel_name="BBC One",
        source_id="test-source",
        match_class="EXACT_ID",
        confidence=1.0,
        reason="Exact ID match",
    ))

    # Add programme
    bundle.programmes.append(BundleProgramme(
        source_id="test-source",
        guide_channel_id="bbc1.uk",
        start=1705320000000,
        stop=1705323600000,
        title="News",
        description="Latest news",
    ))

    # Serialize and deserialize
    json_str = bundle.to_json()
    loaded = EpgBundle.from_json(json_str)

    assert loaded.schema_version == bundle.schema_version
    assert loaded.database_identity == bundle.database_identity
    assert len(loaded.sources) == 1
    assert loaded.sources[0].source_id == "test-source"
    assert len(loaded.mappings) == 1
    assert loaded.mappings[0].guide_channel_id == "bbc1.uk"
    assert len(loaded.programmes) == 1
    assert loaded.programmes[0].title == "News"


def test_bundle_builder():
    """Test bundle builder."""
    inventory = create_test_inventory()
    match = create_test_match()
    merged = create_test_merged()

    # Create a simple guide
    guides = {"test-source": XmltvGuide(programmes={"bbc1.uk": merged.programmes})}

    builder = BundleBuilder(inventory)
    bundle = builder.build_bundle(
        matches=[match],
        merged_schedules=[merged],
        guides=guides,
        source_configs={"test-source": {"name": "Test", "type": "xmltv_url", "priority": 50}},
    )

    assert bundle.database_identity == "test-db-id"
    assert bundle.inventory_revision == "2024-01-15T12:00:00Z"
    assert bundle.inventory_generation == 1
    assert bundle.config_signature == "config-sig"
    assert len(bundle.sources) == 1
    assert len(bundle.mappings) == 1
    assert len(bundle.programmes) == 2
    assert bundle.validation_summary["total_channels"] == 1
    assert bundle.validation_summary["matched_channels"] == 1


def test_bundle_validation_summary():
    """Test validation summary generation."""
    inventory = create_test_inventory()
    match = create_test_match()

    # Add an unmatched channel
    unmatched_channel = ChannelIdentity(
        account_id="1",
        provider_channel_id="UNKNOWN",
        db_id="101",
        name="Unknown",
        display_name="Unknown",
        signature="sig456",
    )
    unmatched_match = ChannelMatch(
        channel=unmatched_channel,
        match_class=MatchClass.UNMATCHED,
        confidence=0.0,
        reason="No match",
    )

    merged = create_test_merged()
    guides = {"test-source": XmltvGuide(programmes={"bbc1.uk": merged.programmes})}

    builder = BundleBuilder(inventory)
    bundle = builder.build_bundle(
        matches=[match, unmatched_match],
        merged_schedules=[merged],
        guides=guides,
        source_configs={"test-source": {"name": "Test", "type": "xmltv_url", "priority": 50}},
    )

    assert bundle.validation_summary["total_channels"] == 2
    assert bundle.validation_summary["matched_channels"] == 1
    assert bundle.validation_summary["unmatched_channels"] == 1


def test_bundle_save_load(tmp_path):
    """Test saving and loading bundle to file."""
    bundle = EpgBundle(
        database_path="/tmp/test.db",
        database_identity="test-db-id",
        inventory_revision="rev1",
        inventory_generation=1,
    )

    file_path = tmp_path / "bundle.json"
    bundle.save(file_path)

    loaded = EpgBundle.load(file_path)
    assert loaded.database_identity == "test-db-id"
    assert loaded.inventory_revision == "rev1"

def test_shared_guide_is_published_once_for_distinct_account_targets():
    first = create_test_match()
    second = create_test_match()
    second.channel.account_id = "another-account"
    guide = XmltvGuide(programmes={"bbc1.uk": create_test_merged().programmes})
    bundle = BundleBuilder(create_test_inventory()).build_bundle(
        [first, second], [], {"test-source": guide}, {})
    assert len(bundle.mappings) == 2
    assert len(bundle.programmes) == 2
    assert {m.account_id for m in bundle.mappings} == {"1", "another-account"}


def test_bundle_excludes_outdated_and_far_future_source_programmes():
    match = create_test_match()
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    guide = XmltvGuide(programmes={"bbc1.uk": [
        EpgProgramme("Expired", "", now-8*3600000, now-7*3600000),
        EpgProgramme("Future", "", now+8*86400000, now+8*86400000+3600000),
        EpgProgramme("Current", "", now, now+3600000)]})
    bundle = BundleBuilder(create_test_inventory()).build_bundle([match], [], {"test-source": guide}, {})
    assert [p.title for p in bundle.programmes] == ["Current"]
    assert len(bundle.mappings) == 1


def test_shared_bundle_keeps_unmatched_guide_channels_without_any_accounts():
    inventory = create_test_inventory()
    inventory.channels = []
    inventory.accounts = []
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    guide = XmltvGuide(programmes={"future.station": [
        EpgProgramme("Future account programme", "", now - 60000, now + 3600000)
    ]}, names={"future station": "future.station"})
    bundle = BundleBuilder(inventory).build_bundle([], [], {"public": guide}, {"public": {"priority": 25}})
    assert bundle.shared_guides
    assert not bundle.mappings
    assert [p.title for p in bundle.programmes] == ["Future account programme"]
    assert bundle.sources[0].names == {"future station": "future.station"}
    loaded = EpgBundle.from_json(bundle.to_json())
    assert loaded.shared_guides
    assert loaded.sources[0].names == bundle.sources[0].names
