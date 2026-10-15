"""Pytest configuration for UIPTV EPG Builder tests."""

import pytest
import tempfile
from pathlib import Path


@pytest.fixture
def temp_dir():
    """Provide a temporary directory."""
    with tempfile.TemporaryDirectory() as tmpdir:
        yield Path(tmpdir)


@pytest.fixture
def sample_xmltv():
    """Provide sample XMLTV content."""
    return b"""<?xml version="1.0" encoding="UTF-8"?>
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


@pytest.fixture
def sample_gzipped_xmltv(sample_xmltv):
    """Provide gzipped sample XMLTV."""
    import gzip
    return gzip.compress(sample_xmltv)

@pytest.fixture(autouse=True)
def isolate_user_home(tmp_path, monkeypatch):
    """Tests must never write to the user's actual builder configuration/cache."""
    monkeypatch.setattr(Path, "home", classmethod(lambda cls: tmp_path))
    monkeypatch.setenv("JAVA_TOOL_OPTIONS", "-Duser.home=" + str(tmp_path))
