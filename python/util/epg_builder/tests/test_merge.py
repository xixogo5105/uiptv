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


def test_repeated_account_channels_merge_one_unique_timeline():
    from uiptv_epg_builder.bridge import JavaBridge
    class RecordingBridge(JavaBridge):
        def merge_schedules(self, schedules):
            self.count = len(schedules)
            return super().merge_schedules(schedules)
    bridge = RecordingBridge()
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    programme = EpgProgramme("Shared programme", "", now, now + 3600000)
    matches = [ChannelMatch(ChannelIdentity(str(i), "provider", "row", "Station", "Station"),
                            "guide", "Station", "source", MatchClass.EXACT_ID, 1.0)
               for i in range(100)]
    matches.append(ChannelMatch(ChannelIdentity("empty", "missing", "row", "Missing", "Missing"),
                                match_class=MatchClass.UNMATCHED, confidence=0.0))
    result = ScheduleMerger(bridge=bridge).merge_schedules(matches,
        {"source": XmltvGuide(programmes={"guide": [programme]})}, {"source": 1})
    assert bridge.count == 1
    assert len(result) == 101
    assert all(r.programmes == [programme] for r in result[:100])
    assert result[-1].programmes == []
    assert result[0].programmes is not result[1].programmes


def test_identical_guide_ids_in_distinct_sources_do_not_share_timelines():
    now = int(datetime.now(timezone.utc).timestamp() * 1000)
    matches = [ChannelMatch(ChannelIdentity(s, "provider", "row", "Station", "Station"),
                            "same-id", "Station", s, MatchClass.EXACT_ID, 1.0) for s in ("a", "b")]
    guides = {s: XmltvGuide(programmes={"same-id": [EpgProgramme(s, "", now, now+3600000)]}) for s in ("a", "b")}
    result = ScheduleMerger().merge_schedules(matches, guides, {"a": 1, "b": 2})
    assert [r.programmes[0].title for r in result] == ["a", "b"]


def test_merge_batches_complete_timelines_with_utf8_byte_limits(monkeypatch):
    import json
    from pathlib import Path
    from types import SimpleNamespace
    from uiptv_epg_builder.bridge import JavaBridge
    bridge = JavaBridge(bridge_jar="unused.jar")
    bridge.MERGE_REQUEST_LIMIT_BYTES = 450
    requests = []
    def run(command, **kwargs):
        payload = Path(command[-1]).read_bytes()
        assert len(payload) <= bridge.MERGE_REQUEST_LIMIT_BYTES
        timelines = json.loads(payload)["schedules"]
        requests.append(timelines)
        return SimpleNamespace(returncode=0, stdout=json.dumps({"schedules": [t[0] for t in timelines]}))
    monkeypatch.setattr("uiptv_epg_builder.bridge.subprocess.run", run)
    schedules = [[[dict(title="خبر" * 20 + str(i), description="", start=i, stop=i+1)]] for i in range(7)]
    result = bridge.merge_schedules(schedules)
    assert len(requests) > 1
    assert result == [s[0] for s in schedules]
    assert [s for batch in requests for s in batch] == schedules
    assert bridge.merge_schedules([]) == []
