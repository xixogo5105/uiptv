"""Real local-file pipeline against an isolated database and the packaged Java bridge."""
from datetime import datetime, timedelta, timezone
from pathlib import Path
import sqlite3
import sys
import pytest
from uiptv_epg_builder.config import Config, SourceConfig
from uiptv_epg_builder.runner import EpgBuilderRunner


@pytest.mark.parametrize("configured_location", [False, True])
def test_dry_run_apply_and_repeated_apply(tmp_path, configured_location):
    root = Path(__file__).resolve().parents[4]
    app_home = tmp_path / ("uiptv" if sys.platform in ("darwin", "win32") else ".config/uiptv")
    app_home.mkdir(parents=True)
    database = tmp_path / "custom.db" if configured_location else app_home / "uiptv.db"
    if configured_location:
        (app_home / "uiptv.ini").write_text("db.path=" + str(database).replace("\\", "/") + "\n")
    with sqlite3.connect(database) as connection:
        connection.executescript((root / "core/src/main/resources/db/migrations/0000_baseline.sql").read_text())
        connection.execute("INSERT INTO Configuration(id, epgEnabled, epgMappingsJson) VALUES(1, '1', '{}')")
        connection.execute("INSERT INTO Account(id,accountName,type) VALUES(1,'Local test','M3U8_LOCAL')")
        connection.execute("INSERT INTO Category(id,categoryId,accountId,accountType) VALUES(10,'provider-category','1','itv')")
        connection.execute("INSERT INTO Channel(id,channelId,categoryId,name,extraJson) VALUES(100,'provider-channel','10','Station',?)", ('{"xmltv_id":"guide"}',))
    xml = tmp_path / "guide.xml"
    now = datetime.now(timezone.utc)
    xml.write_text(f'<tv><channel id="guide"><display-name>Station</display-name></channel><programme channel="guide" start="{(now-timedelta(minutes=1)):%Y%m%d%H%M%S} +0000" stop="{(now+timedelta(hours=1)):%Y%m%d%H%M%S} +0000"><title>Actual programme</title></programme><programme channel="guide" start="{now:%Y%m%d%H%M%S} +0000" stop="{now:%Y%m%d%H%M%S} +0000"><title>Invalid interval</title></programme></tv>')
    config = Config(working_dir=str(tmp_path / "work"), sources=[SourceConfig("local", "Local", "local_xmltv", str(xml))])
    config.bridge.jvm_args = ["-Duser.home=" + str(tmp_path)]
    result = EpgBuilderRunner(config).run(dry_run=True)
    assert result.success, result.error
    assert result.programmes_imported == 0
    with sqlite3.connect(database) as connection:
        assert connection.execute("SELECT count(*) FROM epg_programme").fetchone()[0] == 0
    for _ in range(2):
        result = EpgBuilderRunner(config).run(dry_run=False)
        assert result.success, result.error
    with sqlite3.connect(database) as connection:
        assert connection.execute("SELECT title FROM epg_programme").fetchall() == [("Actual programme",)]
        assert connection.execute("SELECT account_id,provider_channel_id,priority FROM epg_channel_mapping").fetchall() == [('1', 'provider-channel', 10100)]
        assert connection.execute("SELECT count(*) FROM Channel").fetchone()[0] == 1
    # A failed later build must leave the last published source and catalogue intact.
    xml.write_text('<tv><broken')
    failed = EpgBuilderRunner(config).run(dry_run=False, force_refresh=True)
    assert not failed.success
    with sqlite3.connect(database) as connection:
        assert connection.execute("SELECT title FROM epg_programme").fetchall() == [("Actual programme",)]
