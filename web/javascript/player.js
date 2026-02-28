(function () {
    const statusEl = document.getElementById('status');
    const videoEl = document.getElementById('video');
    const reloadBtn = document.getElementById('reload-btn');
    const audioSelectEl = document.getElementById('audio-select');
    const subtitleSelectEl = document.getElementById('subtitle-select');
    let shakaPlayer = null;

    const setStatus = (message) => {
        if (!statusEl) return;
        statusEl.textContent = message || '';
        statusEl.style.display = message ? 'block' : 'none';
    };

    const decodeBase64Url = (value) => {
        const raw = String(value || '').trim();
        if (!raw) return '';
        let normalized = raw.replace(/-/g, '+').replace(/_/g, '/');
        while (normalized.length % 4 !== 0) {
            normalized += '=';
        }
        try {
            return atob(normalized);
        } catch (_) {
            return '';
        }
    };

    const parseLaunchPayload = () => {
        try {
            const params = new URLSearchParams(window.location.search);
            const encoded = params.get('drmLaunch');
            if (!encoded) return null;
            const decoded = decodeBase64Url(encoded);
            if (!decoded) return null;
            return JSON.parse(decoded);
        } catch (_) {
            return null;
        }
    };

    const appendPlaybackCompatParams = (query, mode) => {
        const normalizedMode = String(mode || 'itv').toLowerCase();
        query.set('mode', normalizedMode);
        query.set('streamType', normalizedMode === 'itv' ? 'live' : 'video');
        query.set('action', normalizedMode);
    };

    const cleanValue = (value) => {
        const normalized = String(value ?? '').trim();
        if (!normalized || normalized.toLowerCase() === 'null' || normalized.toLowerCase() === 'undefined') {
            return '';
        }
        return normalized;
    };

    const buildPlayerRequestUrl = (payload) => {
        const channel = payload?.channel || {};
        const mode = String(payload?.mode || 'itv').toLowerCase();

        const query = new URLSearchParams();
        query.set('accountId', cleanValue(payload?.accountId));
        query.set('categoryId', cleanValue(payload?.categoryId));
        query.set('mode', mode);

        const channelDbId = cleanValue(channel.dbId);
        const channelIdentifier = cleanValue(channel.channelId || channel.id);
        if (channelDbId) {
            query.set('channelId', channelDbId);
        } else {
            query.set('channelId', channelIdentifier);
        }
        query.set('name', cleanValue(channel.name));
        query.set('logo', cleanValue(channel.logo));
        query.set('cmd', cleanValue(channel.cmd));
        query.set('cmd_1', cleanValue(channel.cmd_1));
        query.set('cmd_2', cleanValue(channel.cmd_2));
        query.set('cmd_3', cleanValue(channel.cmd_3));
        query.set('drmType', cleanValue(channel.drmType));
        query.set('drmLicenseUrl', cleanValue(channel.drmLicenseUrl));
        query.set('clearKeysJson', cleanValue(channel.clearKeysJson));
        query.set('inputstreamaddon', cleanValue(channel.inputstreamaddon));
        query.set('manifestType', cleanValue(channel.manifestType));
        if (mode === 'series') {
            query.set('seriesId', channelIdentifier);
        }

        appendPlaybackCompatParams(query, mode);
        return `${window.location.origin}/player?${query.toString()}`;
    };

    const loadNative = async (url) => {
        videoEl.src = url;
        await ensurePlaying();
    };

    const ensurePlaying = async () => {
        try {
            await videoEl.play();
            return;
        } catch (e) {
            // Browser may block autoplay with audio; retry muted for instant start.
            try {
                videoEl.muted = true;
                await videoEl.play();
            } catch (_) {
                throw e;
            }
        }
    };

    const resetTrackMenus = () => {
        if (audioSelectEl) {
            audioSelectEl.innerHTML = '<option value="">Audio</option>';
            audioSelectEl.disabled = true;
        }
        if (subtitleSelectEl) {
            subtitleSelectEl.innerHTML = '<option value="off">Subtitles Off</option>';
            subtitleSelectEl.disabled = true;
        }
    };

    const populateTrackMenus = () => {
        if (!shakaPlayer) return;

        if (audioSelectEl) {
            const variants = shakaPlayer.getVariantTracks ? (shakaPlayer.getVariantTracks() || []) : [];
            const audioTracks = variants
                .filter(track => !track.audioOnly)
                .filter((track, idx, arr) => arr.findIndex(other =>
                    String(other.language || '') === String(track.language || '')
                    && String(other.label || '') === String(track.label || '')
                    && String(other.audioCodec || '') === String(track.audioCodec || '')
                ) === idx);
            audioSelectEl.innerHTML = '<option value="">Audio</option>';
            for (const track of audioTracks) {
                const option = document.createElement('option');
                option.value = String(track.id);
                option.textContent = `${track.language || 'Audio'}${track.label ? ` - ${track.label}` : ''}`;
                if (track.active) option.selected = true;
                audioSelectEl.appendChild(option);
            }
            audioSelectEl.disabled = audioTracks.length === 0;
        }

        if (subtitleSelectEl) {
            const textTracks = shakaPlayer.getTextTracks ? (shakaPlayer.getTextTracks() || []) : [];
            subtitleSelectEl.innerHTML = '<option value="off">Subtitles Off</option>';
            for (const track of textTracks) {
                const option = document.createElement('option');
                option.value = String(track.id);
                option.textContent = `${track.language || 'Subtitle'}${track.label ? ` - ${track.label}` : ''}`;
                subtitleSelectEl.appendChild(option);
            }
            subtitleSelectEl.disabled = textTracks.length === 0;
        }
    };

    const loadShaka = async (responseData) => {
        if (!window.shaka || !window.shaka.Player) {
            throw new Error('Shaka Player is not available.');
        }
        shaka.polyfill.installAll();
        if (!shaka.Player.isBrowserSupported()) {
            throw new Error('Browser does not support Shaka playback.');
        }
        shakaPlayer = new shaka.Player();
        await shakaPlayer.attach(videoEl);
        shakaPlayer.addEventListener('error', (event) => {
            const detail = event?.detail?.message || 'Unknown Shaka error';
            setStatus('Playback error: ' + detail);
        });

        if (responseData?.drm) {
            const drmConfig = {};
            if (responseData.drm.licenseUrl && responseData.drm.type) {
                drmConfig.servers = { [responseData.drm.type]: responseData.drm.licenseUrl };
            }
            if (responseData.drm.clearKeys) {
                drmConfig.clearKeys = responseData.drm.clearKeys;
            }
            shakaPlayer.configure({ drm: drmConfig });
        }

        await shakaPlayer.load(responseData.url);
        populateTrackMenus();
        await ensurePlaying();
    };

    const start = async () => {
        const payload = parseLaunchPayload();
        if (!payload || !payload.channel) {
            setStatus('Invalid DRM launch payload.');
            return;
        }
        videoEl.controls = true;

        try {
            setStatus('Requesting playback URL...');
            const response = await fetch(buildPlayerRequestUrl(payload));
            const responseData = await response.json();
            const playbackUrl = cleanValue(responseData?.url);
            if (!playbackUrl) {
                throw new Error('Player response did not return a URL.');
            }
            responseData.url = playbackUrl;

            setStatus('Starting playback...');
            const hasDrm = !!responseData.drm;
            const canNativeHls = !!videoEl.canPlayType('application/vnd.apple.mpegurl');
            const isHlsUrl = String(responseData.url).toLowerCase().includes('.m3u8');

            if (hasDrm) {
                await loadShaka(responseData);
            } else if (canNativeHls && isHlsUrl) {
                await loadNative(responseData.url);
            } else {
                await loadShaka(responseData);
            }

            setStatus('');
        } catch (e) {
            setStatus('Unable to play channel: ' + (e?.message || String(e)));
        }
    };

    if (videoEl) {
        videoEl.addEventListener('error', () => {
            const mediaError = videoEl.error;
            const code = mediaError?.code ? ` (code ${mediaError.code})` : '';
            setStatus(`Playback failed${code}.`);
        });
    }

    const clearWebCacheAndReload = async () => {
        try {
            if ('serviceWorker' in navigator) {
                const registrations = await navigator.serviceWorker.getRegistrations();
                await Promise.all(registrations.map(registration => registration.unregister()));
            }
        } catch (_) {
            // Ignore service worker cleanup errors.
        }

        try {
            if ('caches' in window) {
                const keys = await caches.keys();
                await Promise.all(keys.map(key => caches.delete(key)));
            }
        } catch (_) {
            // Ignore cache cleanup errors.
        }

        try {
            localStorage.clear();
        } catch (_) {}
        try {
            sessionStorage.clear();
        } catch (_) {}

        try {
            if (window.indexedDB && typeof indexedDB.databases === 'function') {
                const dbs = await indexedDB.databases();
                await Promise.all((dbs || []).map(db => new Promise(resolve => {
                    if (!db?.name) {
                        resolve();
                        return;
                    }
                    const request = indexedDB.deleteDatabase(db.name);
                    request.onsuccess = () => resolve();
                    request.onerror = () => resolve();
                    request.onblocked = () => resolve();
                })));
            }
        } catch (_) {
            // Ignore IndexedDB cleanup errors.
        }

        const params = new URLSearchParams(window.location.search);
        params.set('cacheReset', String(Date.now()));
        const target = `${window.location.origin}${window.location.pathname}?${params.toString()}`;
        window.location.replace(target);
    };

    if (reloadBtn) {
        reloadBtn.addEventListener('click', () => {
            clearWebCacheAndReload();
        });
    }

    if (audioSelectEl) {
        audioSelectEl.addEventListener('change', () => {
            if (!shakaPlayer) return;
            const selected = String(audioSelectEl.value || '');
            if (!selected) return;
            const track = (shakaPlayer.getVariantTracks ? shakaPlayer.getVariantTracks() : []).find(t => String(t.id) === selected);
            if (track) {
                shakaPlayer.selectVariantTrack(track, true);
            }
        });
    }

    if (subtitleSelectEl) {
        subtitleSelectEl.addEventListener('change', async () => {
            if (!shakaPlayer) return;
            const selected = String(subtitleSelectEl.value || 'off');
            if (selected === 'off') {
                await shakaPlayer.setTextTrackVisibility(false);
                return;
            }
            const track = (shakaPlayer.getTextTracks ? shakaPlayer.getTextTracks() : []).find(t => String(t.id) === selected);
            if (track) {
                shakaPlayer.selectTextTrack(track);
                await shakaPlayer.setTextTrackVisibility(true);
            }
        });
    }

    window.addEventListener('beforeunload', async () => {
        if (shakaPlayer) {
            try {
                await shakaPlayer.destroy();
            } catch (_) {
                // Ignore cleanup errors.
            }
            shakaPlayer = null;
        }
    });

    resetTrackMenus();
    start();
})();
