"""Tests for XMLTV parsing."""

import pytest
from datetime import datetime, timezone

from uiptv_epg_builder.xmltv import parse_xmltv, XmltvGuide, EpgProgramme


SAMPLE_XMLTV = b"""<?xml version="1.0" encoding="UTF-8"?>
<tv>
  <channel id="bbc1.uk">
    <display-name>BBC One</display-name>
    <icon src="https://example.com/bbc1.png"/>
  </channel>
  <channel id="bbc2.uk">
    <display-name>BBC Two</display-name>
  </channel>
  <programme channel="bbc1.uk" start="20240115120000 +0000" stop="20240115130000 +0000">
    <title>News at One</title>
    <desc>Latest news headlines</desc>
  </programme>
  <programme channel="bbc1.uk" start="20240115130000 +0000" stop="20240115140000 +0000">
    <title>Movie</title>
    <desc>Generic movie</desc>
  </programme>
  <programme channel="bbc2.uk" start="20240115120000 +0000" stop="20240115130000 +0000">
    <title>Documentary</title>
    <desc>Nature documentary</desc>
  </programme>
</tv>"""


def test_parse_xmltv():
    """Test parsing basic XMLTV."""
    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(SAMPLE_XMLTV, now)

    assert sorted(guide.programmes) == ["bbc1.uk", "bbc2.uk"]
    assert len(guide.programmes["bbc1.uk"]) == 2
    assert len(guide.programmes["bbc2.uk"]) == 1

    # Check programmes
    prog = guide.programmes["bbc1.uk"][0]
    assert prog.title == "News at One"
    assert prog.description == "Latest news headlines"
    assert prog.start == 1705320000000  # 2024-01-15 12:00:00 UTC
    assert prog.stop == 1705323600000   # 2024-01-15 13:00:00 UTC


def test_channel_names():
    """Test channel name mappings."""
    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(SAMPLE_XMLTV, now)

    assert guide.names["bbc one"] == "bbc1.uk"
    assert guide.names["bbc two"] == "bbc2.uk"
    # quality_names uses normalized quality names
    assert sorted(guide.quality_names.values()) == ["bbc1.uk", "bbc2.uk"]


def test_channel_icons():
    """Test channel icon parsing."""
    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(SAMPLE_XMLTV, now)

    assert guide.icons["bbc1.uk"] == "https://example.com/bbc1.png"
    assert "bbc2.uk" not in guide.icons


def test_timezone_handling():
    """Test various timezone formats."""
    xml = b"""<?xml version="1.0"?>
<tv>
  <channel id="test"><display-name>Test</display-name></channel>
  <programme channel="test" start="20240115120000 +0000" stop="20240115130000 +0000">
    <title>UTC</title>
  </programme>
  <programme channel="test" start="20240115120000 -0500" stop="20240115130000 -0500">
    <title>EST</title>
  </programme>
  <programme channel="test" start="20240115120000 +0100" stop="20240115130000 +0100">
    <title>CET</title>
  </programme>
</tv>"""

    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(xml, now)

    by_title = {p.title: p for p in guide.programmes["test"]}
    assert by_title["UTC"].start == 1705320000000
    assert by_title["EST"].start == 1705338000000
    assert by_title["CET"].start == 1705316400000


def test_missing_stop_time():
    """Test handling of missing stop time."""
    xml = b"""<?xml version="1.0"?>
<tv>
  <channel id="test"><display-name>Test</display-name></channel>
  <programme channel="test" start="20240115120000 +0000">
    <title>No Stop</title>
  </programme>
</tv>"""

    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(xml, now)

    # Core does not invent a one-hour stop without a following programme.
    assert guide.programmes.get("test", []) == []


def test_gzip_compression():
    """Test gzip compressed XMLTV."""
    import gzip
    compressed = gzip.compress(SAMPLE_XMLTV)

    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(compressed, now)

    assert sorted(guide.programmes) == ["bbc1.uk", "bbc2.uk"]
    assert len(guide.programmes["bbc1.uk"]) == 2


def test_lzma_compression():
    """Test LZMA compressed XMLTV."""
    import lzma
    compressed = lzma.compress(SAMPLE_XMLTV)

    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(compressed, now)

    assert sorted(guide.programmes) == ["bbc1.uk", "bbc2.uk"]


def test_ambiguous_names():
    """Test ambiguous name handling."""
    xml = b"""<?xml version="1.0"?>
<tv>
  <channel id="ch1"><display-name>Station</display-name></channel>
  <channel id="ch2"><display-name>Station</display-name></channel>
  <programme channel="ch1" start="20240115120000 +0000" stop="20240115130000 +0000">
    <title>Prog 1</title>
  </programme>
  <programme channel="ch2" start="20240115120000 +0000" stop="20240115130000 +0000">
    <title>Prog 2</title>
  </programme>
</tv>"""

    now = datetime(2024, 1, 15, 12, 30, tzinfo=timezone.utc)
    guide = parse_xmltv(xml, now)

    # Ambiguous names should be removed
    assert "station" not in guide.names
    assert "station" not in guide.quality_names


def test_decompression_limit(monkeypatch):
    import gzip
    monkeypatch.setattr("uiptv_epg_builder.xmltv.MAX_XML_BYTES", 16)
    with pytest.raises(ValueError, match="decompressed size"):
        parse_xmltv(gzip.compress(b"x" * 17))


def test_external_entities_are_rejected():
    xml = b'<!DOCTYPE tv [<!ENTITY secret SYSTEM "file:///etc/passwd">]><tv><channel id="test"><display-name>&secret;</display-name></channel></tv>'
    with pytest.raises(ValueError):
        parse_xmltv(xml)
