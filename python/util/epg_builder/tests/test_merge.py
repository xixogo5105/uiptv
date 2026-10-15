"""Integration checks against Java's actual interval resolver (build bridge first)."""
from datetime import datetime, timezone
from uiptv_epg_builder.xmltv import XmltvGuide, EpgProgramme
from uiptv_epg_builder.inventory import ChannelIdentity
from uiptv_epg_builder.matching import ChannelMatch, MatchCandidate, MatchClass
from uiptv_epg_builder.merge import ScheduleMerger


def merge(high, low=()):
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    def programmes(rows):
        return [EpgProgramme(title, description, now + start * 60000, now + stop * 60000)
                for title, description, start, stop in rows]
    channel = ChannelIdentity("account", "provider", "row", "Station", "Station")
    match = ChannelMatch(channel, "guide", "Station", "high", MatchClass.EXACT_ID, 1.0,
                         candidates=[MatchCandidate("guide", "Station", 1.0, MatchClass.EXACT_ID, "exact", "low")])
    guides = {"high": XmltvGuide(programmes={"guide": programmes(high)}),
              "low": XmltvGuide(programmes={"guide": programmes(low)})}
    result = ScheduleMerger().merge_schedules([match], guides, {"high": 1, "low": 2})[0]
    return [(p.title, p.description, (p.start-now)//60000, (p.stop-now)//60000) for p in result.programmes]


def test_specific_lower_source_replaces_generic():
    assert merge([("Movie", "", 0, 60)], [("The Matrix", "Full details", 0, 60)]) == [("The Matrix", "Full details", 0, 60)]


def test_specific_higher_source_keeps_priority():
    assert merge([("Preferred title", "", 0, 60)], [("Other title", "", 0, 60)]) == [("Preferred title", "", 0, 60)]


def test_future_title_cannot_replace_current_generic():
    assert merge([("Movie", "", 0, 60)], [("Later title", "", 60, 120)]) == [("Movie", "", 0, 60), ("Later title", "", 60, 120)]


def test_same_title_across_gap_does_not_invent_coverage():
    assert merge([("News", "", 0, 30), ("News", "", 60, 90)]) == [("News", "", 0, 30), ("News", "", 60, 90)]


def test_same_title_different_episodes_keep_descriptions():
    assert merge([("News", "First", 0, 30), ("News", "Second", 30, 60)]) == [("News", "First", 0, 30), ("News", "Second", 30, 60)]


def test_overlaps_resolve_to_specific_intervals():
    assert merge([("Movie", "", 0, 60), ("Specific title", "", 15, 45)]) == [("Movie", "", 0, 15), ("Specific title", "", 15, 45), ("Movie", "", 45, 60)]


def test_schedule_validation_reports_gap_and_overlap():
    p = lambda start, stop: EpgProgramme("Title", "", start, stop)
    warnings = ScheduleMerger().validate_schedule([p(0, 60000), p(660000, 800000), p(700000, 900000)])
    assert any("Gap" in warning for warning in warnings)
    assert any("Overlap" in warning for warning in warnings)
