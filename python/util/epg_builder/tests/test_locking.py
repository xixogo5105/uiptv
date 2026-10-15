import pytest
from uiptv_epg_builder.locking import run_lock


def test_lock_refuses_overlap_and_releases_after_failure(tmp_path):
    path = tmp_path / "builder.lock"
    with pytest.raises(RuntimeError):
        with run_lock(path):
            with pytest.raises(OSError):
                with run_lock(path):
                    pytest.fail("Overlapping lock acquired")
            raise RuntimeError("Simulate failed run")
    with run_lock(path):
        pass
