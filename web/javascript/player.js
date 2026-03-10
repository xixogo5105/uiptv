(function () {
    const statusEl = document.getElementById('status');
    const videoEl = document.getElementById('video');
    const mediaTitleEl = document.getElementById('media-title');
    const mediaSubtitleEl = document.getElementById('media-subtitle');
    const playlistPanelEl = document.getElementById('playlist-panel');
    const playlistItemsEl = document.getElementById('playlist-items');
    const reloadBtn = document.getElementById('reload-btn');
    const repeatBtn = document.getElementById('repeat-btn');
    const audioSelectEl = document.getElementById('audio-select');
    const subtitleSelectEl = document.getElementById('subtitle-select');
    const qualitySelectEl = document.getElementById('quality-select');
    const APP_TITLE = 'UIPTV Player';
    let shakaPlayer = null;
    let mpegtsPlayer = null;
    let activeLaunch = null;
    let activeBingeWatch = null;
    let mediaBaseTitle = '';
    let playbackMode = '';
    let repeatEnabled = false;
    let repeatReloadInFlight = false;
    let stallMonitorTimer = null;
    let lastProgressWallClock = 0;
    let lastProgressMediaTime = 0;
    let stallRecoveryInFlight = false;
    let lastStallRecoveryAt = 0;
    const AUTOPLAY_BLOCK_RE = /notallowederror|user (didn't|did not) interact with the document first|user gesture|request is not allowed/i;
    const STATUS_AUTOPLAY_BLOCKED = 'Autoplay blocked by browser. Press Play to start.';
    const PLAYBACK_STRATEGY_CACHE_KEY = 'uiptv.playback.strategy.v1';
    const DEFAULT_STARTUP_TIMEOUT_MS = 12000;
    const STALL_MONITOR_INTERVAL_MS = 3000;
    const STALL_TRIGGER_MS = 9000;
    const STALL_RECOVERY_COOLDOWN_MS = 12000;

    const setStatus = (message) => {
        if (!statusEl) return;
        statusEl.textContent = message || '';
        statusEl.style.display = message ? 'block' : 'none';
    };

    const renderMediaTitle = () => {
        if (!mediaTitleEl) return;
        const title = cleanValue(mediaBaseTitle);
        const mode = cleanValue(playbackMode);
        mediaTitleEl.textContent = title && mode ? `${title} [${mode}]` : title;
    };

    const updateDocumentTitle = () => {
        const title = cleanValue(mediaBaseTitle);
        const mode = cleanValue(playbackMode);
        if (!title) {
            document.title = APP_TITLE;
            return;
        }
        document.title = mode ? `${title} [${mode}] | ${APP_TITLE}` : `${title} | ${APP_TITLE}`;
    };

    const resolvePlaybackModeLabel = (url, strategy = '') => {
        const lowerUrl = cleanValue(url).toLowerCase();
        const normalizedStrategy = cleanValue(strategy).toLowerCase();
        if (normalizedStrategy === 'youtube') return 'youtube';
        if (lowerUrl.includes('/proxy-stream')) return 'proxy';
        if (lowerUrl.includes('/hls/stream.m3u8')) return 'hls';
        if (normalizedStrategy === 'mpegts') return 'mpegts';
        if (normalizedStrategy === 'shaka') {
            if (lowerUrl.includes('.mpd')) return 'dash';
            if (lowerUrl.includes('.m3u8')) return 'hls';
            return 'shaka';
        }
        return 'direct';
    };

    const launchMode = (launch) => cleanValue(launch?.mode || 'itv').toLowerCase();

    const updateRepeatButton = () => {
        if (!repeatBtn) return;
        repeatBtn.setAttribute('aria-pressed', repeatEnabled ? 'true' : 'false');
        repeatBtn.title = repeatEnabled ? 'Auto-reload on stream end: ON' : 'Auto-reload on stream end: OFF';
        const label = repeatBtn.querySelector('span');
        if (label) {
            label.textContent = repeatEnabled ? 'Repeat On' : 'Repeat Off';
        }
    };

    const applyDefaultRepeatForLaunch = (launch) => {
        repeatEnabled = launchMode(launch) === 'itv';
        updateRepeatButton();
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
            const encoded = params.get('launch');
            if (!encoded) return null;
            const decoded = decodeBase64Url(encoded);
            if (!decoded) return null;
            return JSON.parse(decoded);
        } catch (_) {
            return null;
        }
    };

    const parseDirectLaunch = () => {
        try {
            const params = new URLSearchParams(window.location.search);
            const directUrl = cleanValue(params.get('directUrl'));
            if (!directUrl) return null;
            return {
                url: directUrl,
                mode: cleanValue(params.get('mode')) || 'series'
            };
        } catch (_) {
            return null;
        }
    };

    const setMetadata = (launch, responseData) => {
        if (!mediaTitleEl || !mediaSubtitleEl) return;
        const channel = responseData?.channel || launch?.channel || {};
        const title = cleanValue(responseData?.title) || cleanValue(channel.name) || APP_TITLE;
        const season = cleanValue(channel.season || responseData?.channel?.season);
        const episodeNum = cleanValue(channel.episodeNum || responseData?.channel?.episodeNum || responseData?.bingeWatch?.currentEpisodeId);
        const subtitleParts = [];
        if (launch?.bingeWatchToken || responseData?.bingeWatch?.token) {
            subtitleParts.push('Binge Watch');
        }
        if (season) {
            subtitleParts.push(`Season ${season}`);
        }
        if (episodeNum) {
            subtitleParts.push(`Episode ${episodeNum}`);
        }
        mediaBaseTitle = title;
        renderMediaTitle();
        mediaSubtitleEl.textContent = subtitleParts.join(' • ');
        updateDocumentTitle();
    };

    const resolveCategoryForMode = (payload, mode) => {
        const normalizedMode = String(mode || payload?.mode || 'itv').toLowerCase();
        if (normalizedMode === 'series') {
            return cleanValue(payload?.seriesCategoryId || payload?.categoryId);
        }
        return cleanValue(payload?.categoryId || payload?.seriesCategoryId);
    };

    const appendChannelMetadata = (query, launch) => {
        const channel = launch?.channel || {};
        const mode = String(launch?.mode || 'itv').toLowerCase();
        query.set('accountId', cleanValue(launch?.accountId));
        query.set('categoryId', resolveCategoryForMode(launch, mode));
        query.set('channelId', cleanValue(channel.channelId || channel.id));
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
        query.set('season', cleanValue(channel.season));
        query.set('episodeNum', cleanValue(channel.episodeNum));
    };

    const appendPlaybackCompatParams = (query, mode) => {
        const normalizedMode = String(mode || 'itv').toLowerCase();
        query.set('mode', normalizedMode);
        query.set('streamType', normalizedMode === 'itv' ? 'live' : 'video');
        query.set('action', normalizedMode);
    };

    const getPlaybackEndpoint = (mode, launch) => {
        return '/player';
    };

    const cleanValue = (value) => {
        const normalized = String(value ?? '').trim();
        if (!normalized || normalized.toLowerCase() === 'null' || normalized.toLowerCase() === 'undefined') {
            return '';
        }
        return normalized;
    };

    const safeParseJson = (rawValue, fallbackValue) => {
        try {
            return JSON.parse(rawValue);
        } catch (_) {
            return fallbackValue;
        }
    };

    const extractHost = (value) => {
        const normalized = cleanValue(value);
        if (!normalized || normalized.startsWith('/')) {
            return '';
        }
        try {
            return new URL(normalized, window.location.origin).host || '';
        } catch (_) {
            return '';
        }
    };

    const buildStrategyCacheKey = (launch, responseData) => {
        const mode = cleanValue(launch?.mode || 'itv').toLowerCase();
        const accountId = cleanValue(launch?.accountId);
        const channel = launch?.channel || {};
        const channelId = cleanValue(channel.channelId || channel.id);
        const providerHost = extractHost(channel.cmd) || extractHost(launch?.directUrl || launch?.url);
        const responseHost = extractHost(responseData?.url);
        return [mode, accountId || 'anon', providerHost || responseHost || channelId || 'unknown'].join('|');
    };

    const readStrategyCache = () => {
        if (!window.localStorage) {
            return {};
        }
        return safeParseJson(localStorage.getItem(PLAYBACK_STRATEGY_CACHE_KEY), {}) || {};
    };

    const getCachedStrategy = (launch, responseData) => {
        const key = buildStrategyCacheKey(launch, responseData);
        const map = readStrategyCache();
        return cleanValue(map[key]).toLowerCase();
    };

    const rememberStrategy = (launch, responseData, strategy) => {
        if (!window.localStorage) {
            return;
        }
        const key = buildStrategyCacheKey(launch, responseData);
        if (!key) {
            return;
        }
        const map = readStrategyCache();
        map[key] = cleanValue(strategy).toLowerCase();
        try {
            localStorage.setItem(PLAYBACK_STRATEGY_CACHE_KEY, JSON.stringify(map));
        } catch (_) {
            // Ignore storage quota/availability errors.
        }
    };

    const buildPlayerRequestUrl = (payload) => {
        const channel = payload?.channel || {};
        const mode = String(payload?.mode || 'itv').toLowerCase();

        const query = new URLSearchParams();
        query.set('accountId', cleanValue(payload?.accountId));
        query.set('categoryId', resolveCategoryForMode(payload, mode));
        query.set('mode', mode);

        const channelIdentifier = cleanValue(channel.channelId || channel.id);
        const channelDbId = cleanValue(channel.dbId);
        if (mode === 'itv' && channelDbId) {
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
        query.set('season', cleanValue(channel.season));
        query.set('episodeNum', cleanValue(channel.episodeNum));
        if (mode === 'series') {
            query.set('seriesId', channelIdentifier);
            query.set('seriesParentId', cleanValue(payload?.seriesParentId));
        }

        appendPlaybackCompatParams(query, mode);
        return `${window.location.origin}${getPlaybackEndpoint(mode, payload)}?${query.toString()}`;
    };

    const buildDirectPlayerRequestUrl = (launch) => {
        const query = new URLSearchParams();
        const mode = String(launch?.mode || 'series').toLowerCase();
        if (cleanValue(launch?.bingeWatchToken)) {
            query.set('bingeWatchToken', cleanValue(launch.bingeWatchToken));
            query.set('episodeId', cleanValue(launch?.episodeId));
        } else {
            query.set('url', cleanValue(launch?.directUrl || launch?.url));
        }
        appendChannelMetadata(query, launch);
        appendPlaybackCompatParams(query, mode);
        return `${window.location.origin}${getPlaybackEndpoint(mode, launch)}?${query.toString()}`;
    };

    const addCacheBuster = (url, key) => {
        try {
            const parsed = new URL(url, window.location.origin);
            parsed.searchParams.set(key, String(Date.now()));
            return parsed.toString();
        } catch (_) {
            const separator = url.includes('?') ? '&' : '?';
            return `${url}${separator}${encodeURIComponent(key)}=${Date.now()}`;
        }
    };

    const addPreferHls = (url) => {
        try {
            const parsed = new URL(url, window.location.origin);
            parsed.searchParams.set('preferHls', '1');
            return parsed.toString();
        } catch (_) {
            const separator = url.includes('?') ? '&' : '?';
            return `${url}${separator}preferHls=1`;
        }
    };

    const buildLaunchRequestUrl = (launch, preferHls = false) => {
        const hasInlineLaunch = !!cleanValue(launch?.bingeWatchToken) || !!cleanValue(launch?.directUrl || launch?.url);
        const baseUrl = hasInlineLaunch
            ? buildDirectPlayerRequestUrl(launch)
            : buildPlayerRequestUrl(launch);
        return preferHls ? addPreferHls(baseUrl) : baseUrl;
    };

    const destroyPlayer = async () => {
        stopStallMonitor();
        if (mpegtsPlayer) {
            try {
                mpegtsPlayer.destroy();
            } catch (_) {
                // Ignore cleanup errors.
            }
            mpegtsPlayer = null;
        }
        if (shakaPlayer) {
            try {
                await shakaPlayer.destroy();
            } catch (_) {
                // Ignore cleanup errors.
            }
            shakaPlayer = null;
        }
        videoEl.removeAttribute('src');
        videoEl.load();
        resetTrackMenus();
    };

    const markPlaybackProgress = () => {
        lastProgressMediaTime = Number(videoEl?.currentTime || 0);
        lastProgressWallClock = Date.now();
    };

    const stopStallMonitor = () => {
        if (stallMonitorTimer !== null) {
            window.clearInterval(stallMonitorTimer);
            stallMonitorTimer = null;
        }
        stallRecoveryInFlight = false;
    };

    const recoverFromStall = async () => {
        if (!videoEl || stallRecoveryInFlight) {
            return;
        }
        const now = Date.now();
        if (now - lastStallRecoveryAt < STALL_RECOVERY_COOLDOWN_MS) {
            return;
        }
        stallRecoveryInFlight = true;
        lastStallRecoveryAt = now;
        try {
            // Retry stream fetch in Shaka first if available.
            if (shakaPlayer && typeof shakaPlayer.retryStreaming === 'function') {
                try {
                    shakaPlayer.retryStreaming();
                } catch (_) {
                    // Ignore retry API errors.
                }
            }
            // Only nudge to live edge for live channels; on series/vod this causes
            // visible frame jumps that feel like live-stream skipping.
            if (launchMode(activeLaunch) === 'itv') {
                const seekable = videoEl.seekable;
                if (seekable && seekable.length > 0) {
                    const liveEdge = seekable.end(seekable.length - 1);
                    if (Number.isFinite(liveEdge) && liveEdge > 0) {
                        const target = Math.max(0, liveEdge - 0.8);
                        if (target > videoEl.currentTime + 0.2) {
                            videoEl.currentTime = target;
                        }
                    }
                }
            }
            await videoEl.play();
            markPlaybackProgress();
            setStatus('');
        } catch (_) {
            // Ignore transient recovery failures; monitor will retry.
        } finally {
            stallRecoveryInFlight = false;
        }
    };

    const startStallMonitor = () => {
        stopStallMonitor();
        markPlaybackProgress();
        stallMonitorTimer = window.setInterval(() => {
            if (!videoEl) {
                return;
            }
            if (videoEl.paused || videoEl.ended) {
                markPlaybackProgress();
                return;
            }
            const currentTime = Number(videoEl.currentTime || 0);
            if (Math.abs(currentTime - lastProgressMediaTime) > 0.05) {
                markPlaybackProgress();
                return;
            }
            const noProgressMs = Date.now() - lastProgressWallClock;
            if (noProgressMs >= STALL_TRIGGER_MS) {
                recoverFromStall();
            }
        }, STALL_MONITOR_INTERVAL_MS);
    };

    const updateBingeWatchState = (responseData) => {
        const binge = responseData?.bingeWatch;
        if (!binge?.token || !Array.isArray(binge.items) || binge.items.length === 0) {
            activeBingeWatch = null;
            renderPlaylist();
            return;
        }
        const currentEpisodeId = cleanValue(binge.currentEpisodeId);
        const currentIndex = Math.max(0, binge.items.findIndex(item => cleanValue(item.episodeId) === currentEpisodeId));
        activeBingeWatch = {
            token: cleanValue(binge.token),
            items: binge.items,
            currentIndex
        };
        renderPlaylist();
    };

    const renderPlaylist = () => {
        if (!playlistPanelEl || !playlistItemsEl) {
            return;
        }
        if (!activeBingeWatch || !Array.isArray(activeBingeWatch.items) || activeBingeWatch.items.length === 0) {
            playlistPanelEl.hidden = true;
            playlistItemsEl.innerHTML = '';
            return;
        }
        playlistPanelEl.hidden = false;
        playlistItemsEl.innerHTML = '';
        activeBingeWatch.items.forEach((item, index) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = `playlist-item${index === activeBingeWatch.currentIndex ? ' active' : ''}`;
            button.setAttribute('role', 'listitem');
            const episodeId = cleanValue(item.episodeId);
            const title = cleanValue(item.episodeName) || `Episode ${cleanValue(item.episodeNumber || episodeId)}`;
            const meta = [];
            if (cleanValue(item.season)) {
                meta.push(`Season ${cleanValue(item.season)}`);
            }
            if (cleanValue(item.episodeNumber)) {
                meta.push(`Episode ${cleanValue(item.episodeNumber)}`);
            }
            button.innerHTML = `<span class="playlist-item-title"></span><span class="playlist-item-meta"></span>`;
            button.querySelector('.playlist-item-title').textContent = title;
            button.querySelector('.playlist-item-meta').textContent = meta.join(' • ');
            button.addEventListener('click', () => {
                if (index === activeBingeWatch.currentIndex || !activeLaunch) {
                    return;
                }
                const nextLaunch = {
                    ...activeLaunch,
                    episodeId,
                    channel: {
                        ...(activeLaunch.channel || {}),
                        channelId: episodeId,
                        name: title,
                        season: cleanValue(item.season),
                        episodeNum: cleanValue(item.episodeNumber)
                    }
                };
                destroyPlayer()
                    .then(() => requestAndStartPlayback(nextLaunch))
                    .catch((error) => {
                        setStatus('Unable to play playlist item: ' + describePlaybackError(error));
                    });
            });
            playlistItemsEl.appendChild(button);
        });
    };

    const buildProxyUrl = (url) => {
        const normalized = cleanValue(url);
        if (!normalized) {
            return '';
        }
        if (normalized.includes('/proxy-stream?src=')) {
            return normalized;
        }
        if (normalized.startsWith('/') || normalized.startsWith('blob:') || normalized.startsWith('data:')) {
            return '';
        }
        return `${window.location.origin}/proxy-stream?src=${encodeURIComponent(normalized)}`;
    };

    const isAdaptiveUrl = (url) => {
        const lower = String(url || '').toLowerCase();
        return lower.includes('.m3u8') || lower.includes('.mpd') || lower.includes('/hls/stream.m3u8');
    };

    const isShakaEligibleUrl = (url) => {
        return isAdaptiveUrl(url);
    };

    const uniqueStrategies = (values) => {
        const output = [];
        for (const value of values) {
            const normalized = cleanValue(value).toLowerCase();
            if (!normalized || output.includes(normalized)) {
                continue;
            }
            output.push(normalized);
        }
        return output;
    };

    const normalizeStrategyName = (value) => {
        const normalized = cleanValue(value).toLowerCase().replace(/_/g, '-');
        if (normalized === 'shaka' || normalized === 'native' || normalized === 'native-proxy' || normalized === 'mpegts') {
            return normalized;
        }
        return '';
    };

    const isTsLikeResponse = (responseData) => {
        const lowerUrl = String(responseData?.url || '').toLowerCase();
        const manifestType = cleanValue(responseData?.drm?.manifestType || responseData?.manifestType).toLowerCase();
        return manifestType === 'ts'
            || manifestType === 'mpegts'
            || manifestType === 'mpeg2ts'
            || lowerUrl.includes('.ts?')
            || lowerUrl.endsWith('.ts')
            || lowerUrl.includes('.m2ts?')
            || lowerUrl.endsWith('.m2ts')
            || lowerUrl.includes('extension=ts')
            || lowerUrl.includes('/live/play/');
    };

    const canUseMpegts = () => {
        const engine = window.mpegts;
        return !!engine && typeof engine.isSupported === 'function' && engine.isSupported();
    };

    const resolvePrimaryStrategy = (responseData, launch) => {
        const hasDrm = !!responseData?.drm;
        if (hasDrm) {
            return 'shaka';
        }
        const lowerUrl = String(responseData?.url || '').toLowerCase();
        const shakaEligible = isShakaEligibleUrl(lowerUrl);

        const cached = normalizeStrategyName(getCachedStrategy(launch, responseData));
        if (cached && (cached !== 'shaka' || shakaEligible)) {
            return cached;
        }
        const hint = normalizeStrategyName(responseData?.strategyHint);
        if (hint && (hint !== 'shaka' || shakaEligible)) {
            return hint;
        }
        if (isTsLikeResponse(responseData) && canUseMpegts()) {
            return 'mpegts';
        }
        if (lowerUrl.includes('.mpd') || lowerUrl.includes('/hls/stream.m3u8')) {
            return 'shaka';
        }
        const canNativeHls = Boolean(videoEl.canPlayType('application/vnd.apple.mpegurl'));
        if (lowerUrl.includes('.m3u8') && !canNativeHls) {
            return 'shaka';
        }
        return 'native';
    };

    const buildStrategyPlan = (responseData, launch) => {
        const hasDrm = !!responseData?.drm;
        if (hasDrm) {
            return ['shaka'];
        }
        const normalizedUrl = cleanValue(responseData?.url);
        const plan = [resolvePrimaryStrategy(responseData, launch)];
        if (isTsLikeResponse(responseData)) {
            plan.push('prefer-hls');
        }
        plan.push('native');
        if (buildProxyUrl(normalizedUrl)) {
            plan.push('native-proxy');
        }
        if (isAdaptiveUrl(normalizedUrl)) {
            plan.push('shaka');
        }
        return uniqueStrategies(plan);
    };

    const startupTimeoutMsForMode = (launch) => {
        const mode = cleanValue(launch?.mode || 'itv').toLowerCase();
        if (mode === 'itv') {
            return 9000;
        }
        if (mode === 'vod' || mode === 'series') {
            return 14000;
        }
        return DEFAULT_STARTUP_TIMEOUT_MS;
    };

    const waitForPlaybackStart = async (timeoutMs) => {
        await new Promise((resolve, reject) => {
            let done = false;
            let timeoutId = null;
            const finish = (fn, payload) => {
                if (done) return;
                done = true;
                cleanup();
                fn(payload);
            };
            const onPlaying = () => finish(resolve);
            const onError = () => {
                const mediaError = videoEl.error;
                const code = mediaError?.code ? `code ${mediaError.code}` : 'unknown';
                finish(reject, new Error(`media-error-${code}`));
            };
            const cleanup = () => {
                videoEl.removeEventListener('playing', onPlaying);
                videoEl.removeEventListener('error', onError);
                if (timeoutId !== null) {
                    clearTimeout(timeoutId);
                }
            };
            videoEl.addEventListener('playing', onPlaying, {once: true});
            videoEl.addEventListener('error', onError, {once: true});
            timeoutId = window.setTimeout(() => finish(reject, new Error('startup-timeout')), timeoutMs);
            if (!videoEl.paused && videoEl.readyState >= 2) {
                onPlaying();
            }
        });
    };

    const playbackUrlForStrategy = (strategy, originalUrl) => {
        const normalized = cleanValue(originalUrl);
        if (!normalized) {
            return '';
        }
        if (strategy === 'native-proxy') {
            return buildProxyUrl(normalized);
        }
        return normalized;
    };

    const attemptStrategy = async (strategy, responseData, launch, timeoutMs) => {
        let strategyResponse = responseData;
        let playbackUrl = playbackUrlForStrategy(strategy, responseData?.url);
        if (strategy === 'prefer-hls') {
            const forcedResponse = await fetch(buildLaunchRequestUrl(launch, true), {cache: 'no-store'});
            strategyResponse = await forcedResponse.json();
            playbackUrl = cleanValue(strategyResponse?.url);
            if (!playbackUrl || playbackUrl === cleanValue(responseData?.url)) {
                throw new Error('forced-hls-unavailable');
            }
            setMetadata(launch, strategyResponse);
        }
        if (!playbackUrl) {
            throw new Error('empty-playback-url');
        }
        const playbackStrategy = strategy === 'prefer-hls' ? resolvePrimaryStrategy(strategyResponse, launch) : strategy;
        playbackMode = resolvePlaybackModeLabel(playbackUrl, playbackStrategy);
        renderMediaTitle();
        updateDocumentTitle();
        const shakaPayload = {...strategyResponse, url: playbackUrl};
        let started;
        if (playbackStrategy === 'shaka') {
            started = await loadShaka(shakaPayload);
        } else if (playbackStrategy === 'mpegts') {
            started = await loadMpegTs(playbackUrl);
        } else {
            started = await loadNative(playbackUrl);
        }
        if (started === false) {
            return {autoplayBlocked: true, playbackUrl};
        }
        await waitForPlaybackStart(timeoutMs);
        if (strategy !== 'prefer-hls') {
            rememberStrategy(launch, responseData, playbackStrategy);
        }
        return {autoplayBlocked: false, playbackUrl};
    };

    const startPlaybackWithFallback = async (launch, responseData) => {
        const plan = buildStrategyPlan(responseData, launch);
        const timeoutMs = startupTimeoutMsForMode(launch);
        let lastError = null;

        for (const strategy of plan) {
            try {
                setStatus(`Starting playback (${strategy})...`);
                const result = await attemptStrategy(strategy, responseData, launch, timeoutMs);
                if (result.autoplayBlocked) {
                    return false;
                }
                return true;
            } catch (error) {
                lastError = error;
                await destroyPlayer();
            }
        }

        if (lastError) {
            throw lastError;
        }
        throw new Error('no-playback-strategy');
    };

    const playResolvedResponse = async (launch, responseData) => {
        const playbackUrl = cleanValue(responseData?.url);
        if (!playbackUrl) {
            throw new Error('Player response did not return a URL.');
        }
        responseData.url = playbackUrl;
        updateBingeWatchState(responseData);
        setMetadata(launch, responseData);

        const started = await startPlaybackWithFallback(launch, responseData);
        if (started === false) {
            setStatus(STATUS_AUTOPLAY_BLOCKED);
            stopStallMonitor();
        } else {
            setStatus('');
            startStallMonitor();
        }
    };

    const requestAndStartPlayback = async (launch, options = {}) => {
        activeLaunch = launch;
        repeatReloadInFlight = false;
        playbackMode = 'loading';
        renderMediaTitle();
        updateDocumentTitle();
        setStatus('Requesting playback URL...');
        let requestUrl = buildLaunchRequestUrl(launch, options.preferHls);
        if (options.cacheBust) {
            requestUrl = addCacheBuster(requestUrl, '_repeatTs');
        }
        const resolveAndPlay = async (url) => {
            const response = await fetch(url, {cache: 'no-store'});
            const responseData = await response.json();
            await playResolvedResponse(launch, responseData);
        };
        try {
            await resolveAndPlay(requestUrl);
        } catch (error) {
            const allowFreshRetry = options.allowFreshRetry !== false;
            if (!allowFreshRetry || launchMode(launch) !== 'series') {
                throw error;
            }
            const retryUrl = addCacheBuster(buildLaunchRequestUrl(launch), '_freshUrl');
            await destroyPlayer();
            await resolveAndPlay(retryUrl);
        }
    };

    const playNextBingeWatchEpisode = async () => {
        if (!activeBingeWatch || !activeLaunch) {
            return;
        }
        const nextIndex = activeBingeWatch.currentIndex + 1;
        if (nextIndex >= activeBingeWatch.items.length) {
            return;
        }
        const nextItem = activeBingeWatch.items[nextIndex];
        const nextLaunch = {
            ...activeLaunch,
            episodeId: cleanValue(nextItem.episodeId),
            channel: {
                ...(activeLaunch.channel || {}),
                channelId: cleanValue(nextItem.episodeId),
                name: cleanValue(nextItem.episodeName) || cleanValue((activeLaunch.channel || {}).name),
                season: cleanValue(nextItem.season),
                episodeNum: cleanValue(nextItem.episodeNumber)
            }
        };
        await destroyPlayer();
        await requestAndStartPlayback(nextLaunch);
    };

    const loadNative = async (url) => {
        videoEl.src = url;
        return await ensurePlaying();
    };

    const loadMpegTs = async (url) => {
        const engine = window.mpegts;
        if (!canUseMpegts()) {
            throw new Error('mpegts-not-supported');
        }
        const player = engine.createPlayer(
            {
                type: 'mpegts',
                isLive: launchMode(activeLaunch) === 'itv',
                url
            },
            {
                enableWorker: true,
                lazyLoad: false
            }
        );
        mpegtsPlayer = player;
        player.on(engine.Events.ERROR, (_, detail) => {
            const message = detail?.msg || detail?.message || 'MPEGTS error';
            setStatus('Playback error: ' + message);
        });
        player.attachMediaElement(videoEl);
        player.load();
        await player.play();
        return true;
    };

    const isAutoplayBlockError = (error) => {
        const name = String(error?.name || '');
        const message = String(error?.message || error || '');
        return AUTOPLAY_BLOCK_RE.test(`${name} ${message}`.toLowerCase());
    };

    const describePlaybackError = (error) => {
        const message = String(error?.message || error || '').toLowerCase();
        if (!message) {
            return 'Unable to play stream.';
        }
        if (message.includes('notallowederror') || message.includes('user gesture') || message.includes('request is not allowed')) {
            return STATUS_AUTOPLAY_BLOCKED;
        }
        if (message.includes('media-error-code 4') || message.includes('src_not_supported')) {
            return 'Unsupported stream format/codec in browser. Try proxy/transmux.';
        }
        if (message.includes('media-error-code 2') || message.includes('network')) {
            return 'Network/auth error while loading stream.';
        }
        if (message.includes('media-error-code 3') || message.includes('decode')) {
            return 'Browser failed to decode this stream.';
        }
        if (message.includes('startup-timeout') || message.includes('timeout')) {
            return 'Playback timed out while starting stream.';
        }
        return 'Unable to play stream: ' + (error?.message || String(error));
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
            } catch (mutedError) {
                if (isAutoplayBlockError(e) || isAutoplayBlockError(mutedError)) {
                    return false;
                }
                throw mutedError;
            }
        }
        return true;
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
        if (qualitySelectEl) {
            qualitySelectEl.innerHTML = '<option value="auto">Quality Auto</option>';
            qualitySelectEl.disabled = true;
        }
    };

    const qualityLabelForTrack = (track) => {
        const height = Number(track?.height || 0);
        const width = Number(track?.width || 0);
        const bandwidth = Number(track?.bandwidth || 0);
        const fps = Number(track?.frameRate || 0);
        const parts = [];
        if (height > 0) {
            parts.push(`${height}p`);
        } else if (width > 0) {
            parts.push(`${width}w`);
        } else {
            parts.push('Adaptive');
        }
        if (fps > 0) {
            parts.push(`${Math.round(fps)}fps`);
        }
        if (bandwidth > 0) {
            parts.push(`${(bandwidth / 1000000).toFixed(1)}Mbps`);
        }
        return parts.join(' ');
    };

    const populateQualityMenu = () => {
        if (!shakaPlayer || !qualitySelectEl) return;
        const variants = shakaPlayer.getVariantTracks ? (shakaPlayer.getVariantTracks() || []) : [];
        qualitySelectEl.innerHTML = '<option value="auto">Quality Auto</option>';
        const sorted = variants
            .slice()
            .sort((a, b) => (Number(b.height || 0) - Number(a.height || 0))
                || (Number(b.bandwidth || 0) - Number(a.bandwidth || 0)));
        const unique = [];
        for (const track of sorted) {
            const key = `${track.height || 0}|${track.bandwidth || 0}|${track.frameRate || 0}`;
            if (unique.some(item => item.key === key)) {
                continue;
            }
            unique.push({key, track});
        }
        for (const item of unique) {
            const track = item.track;
            const option = document.createElement('option');
            option.value = String(track.id);
            option.textContent = qualityLabelForTrack(track);
            qualitySelectEl.appendChild(option);
        }
        qualitySelectEl.disabled = unique.length === 0;
        const activeTrack = variants.find(track => track.active);
        const abrEnabled = shakaPlayer.getConfiguration ? !!shakaPlayer.getConfiguration()?.abr?.enabled : true;
        qualitySelectEl.value = abrEnabled || !activeTrack ? 'auto' : String(activeTrack.id);
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

        populateQualityMenu();
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
        shakaPlayer.addEventListener('adaptation', () => {
            populateTrackMenus();
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
        return await ensurePlaying();
    };

    const start = async () => {
        const payload = parseLaunchPayload();
        const directLaunch = parseDirectLaunch();
        videoEl.controls = true;
        updateDocumentTitle();

        try {
            const launch = directLaunch || (payload && (payload.channel || payload.directUrl || payload.bingeWatchToken) ? payload : null);
            if (!launch && !(payload && payload.channel)) {
                setStatus('Invalid launch payload.');
                return;
            }
            if (launch) {
                applyDefaultRepeatForLaunch(launch);
                await requestAndStartPlayback(launch);
            } else {
                activeLaunch = payload;
                repeatReloadInFlight = false;
                applyDefaultRepeatForLaunch(payload);
                const requestUrl = buildPlayerRequestUrl(payload);
                const response = await fetch(requestUrl);
                const responseData = await response.json();
                await playResolvedResponse(payload, responseData);
            }
        } catch (e) {
            setStatus('Unable to play channel: ' + describePlaybackError(e));
        }
    };

    if (videoEl) {
        videoEl.addEventListener('error', () => {
            const mediaError = videoEl.error;
            const code = mediaError?.code ? `media-error-code ${mediaError.code}` : 'media-error-code unknown';
            setStatus(describePlaybackError(new Error(code)));
        });
        videoEl.addEventListener('ended', () => {
            if (activeBingeWatch) {
                playNextBingeWatchEpisode().catch((error) => {
                    setStatus('Unable to continue binge watch: ' + (error?.message || String(error)));
                });
                return;
            }
            if (!repeatEnabled || repeatReloadInFlight || !activeLaunch) {
                return;
            }
            repeatReloadInFlight = true;
            destroyPlayer()
                .then(() => requestAndStartPlayback(activeLaunch, {cacheBust: true}))
                .catch((error) => {
                    setStatus('Unable to reload stream: ' + describePlaybackError(error));
                })
                .finally(() => {
                    repeatReloadInFlight = false;
                });
        });
        videoEl.addEventListener('timeupdate', markPlaybackProgress);
        videoEl.addEventListener('playing', markPlaybackProgress);
        videoEl.addEventListener('stalled', () => {
            recoverFromStall();
        });
        videoEl.addEventListener('waiting', () => {
            recoverFromStall();
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

    if (repeatBtn) {
        repeatBtn.addEventListener('click', () => {
            repeatEnabled = !repeatEnabled;
            updateRepeatButton();
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

    if (qualitySelectEl) {
        qualitySelectEl.addEventListener('change', () => {
            if (!shakaPlayer) return;
            const selected = String(qualitySelectEl.value || 'auto');
            if (selected === 'auto') {
                shakaPlayer.configure({abr: {enabled: true}});
                populateTrackMenus();
                return;
            }
            const variants = shakaPlayer.getVariantTracks ? (shakaPlayer.getVariantTracks() || []) : [];
            const track = variants.find(t => String(t.id) === selected);
            if (!track) {
                return;
            }
            shakaPlayer.configure({abr: {enabled: false}});
            shakaPlayer.selectVariantTrack(track, true);
            populateTrackMenus();
        });
    }

    window.addEventListener('beforeunload', async () => {
        await destroyPlayer();
    });

    resetTrackMenus();
    updateRepeatButton();
    start();
})();
