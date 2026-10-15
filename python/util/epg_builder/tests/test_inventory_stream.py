"""Streaming exports must be complete before any matching or import can proceed."""
import io
import json
import pytest
from uiptv_epg_builder.inventory import InventoryExport, create_test_inventory


def records():
    data = json.loads(create_test_inventory().to_json())
    channels = data.pop("channels")
    revision = data.pop("revision")
    return [dict(data, record_type="header"),
            *(dict(channel, record_type="channel") for channel in channels),
            dict(record_type="end", revision=revision, channel_count=len(channels))]


def parse(items):
    return InventoryExport.from_json_lines(io.StringIO("\n".join(json.dumps(item) for item in items)))


def test_stream_matches_original_inventory():
    assert parse(records()) == create_test_inventory()


@pytest.mark.parametrize("mutation", ["truncated", "wrong_count", "trailing", "no_header"])
def test_partial_or_invalid_stream_is_rejected(mutation):
    items = records()
    if mutation == "truncated":
        items.pop()
    elif mutation == "wrong_count":
        items[-1]["channel_count"] += 1
    elif mutation == "trailing":
        items.append(items[-1])
    else:
        items.pop(0)
    with pytest.raises(ValueError):
        parse(items)


def test_empty_stream_is_rejected():
    with pytest.raises(ValueError, match="Missing inventory header"):
        parse([])
