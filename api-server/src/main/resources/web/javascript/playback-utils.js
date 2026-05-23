(function () {
    const isTsLikeUrl = (url, manifestType = '') => {
        const lowerUrl = String(url || '').trim().toLowerCase();
        const normalizedManifestType = String(manifestType || '').trim().toLowerCase();
        return normalizedManifestType === 'ts'
            || normalizedManifestType === 'mpegts'
            || normalizedManifestType === 'mpeg2ts'
            || lowerUrl.includes('.ts?')
            || lowerUrl.endsWith('.ts')
            || lowerUrl.includes('.m2ts?')
            || lowerUrl.endsWith('.m2ts')
            || lowerUrl.includes('extension=ts')
            || lowerUrl.includes('/live/play/')
            || /\/\d+(?:\?|$)/.test(lowerUrl);
    };

    const canUseMpegts = () => {
        const engine = window.mpegts;
        return !!engine && typeof engine.isSupported === 'function' && engine.isSupported();
    };

    const resolvePlaybackModeLabel = (url, engine = '') => {
        const lowerUrl = String(url || '').trim().toLowerCase();
        const normalizedEngine = String(engine || '').trim().toLowerCase();
        if (normalizedEngine === 'mpegts') return lowerUrl.includes('/proxy-stream') ? 'mpegts proxy' : 'mpegts';
        if (normalizedEngine === 'shaka') {
            if (lowerUrl.includes('.mpd')) return 'dash';
            if (lowerUrl.includes('.m3u8')) return 'hls';
            return 'shaka';
        }
        if (lowerUrl.includes('/proxy-stream')) return 'proxy';
        return 'direct';
    };

    const downgradeHttpsToHttpForKnownPaths = (url) => {
        const value = String(url || '').trim();
        const lower = value.toLowerCase();
        if (lower.startsWith('https://') && (lower.includes('/live/play/') || lower.includes('/play/live.php') || lower.includes('/play/movie.php'))) {
            return `http://${value.slice('https://'.length)}`;
        }
        return value;
    };

    const normalizeWebPlaybackUrl = (rawUrl) => {
        const value = String(rawUrl || '').trim();
        if (!value) return value;
        return downgradeHttpsToHttpForKnownPaths(value);
    };

    const buildProxyStreamUrl = (rawUrl) => {
        const normalized = normalizeWebPlaybackUrl(rawUrl);
        if (!normalized) {
            return '';
        }
        if (normalized.includes('/proxy-stream?src=')) {
            return normalized;
        }
        if (/^(blob:|data:|file:)/i.test(normalized)) {
            return '';
        }
        try {
            const parsed = new URL(normalized, window.location.origin);
            if (parsed.origin === window.location.origin) {
                return '';
            }
            return `${window.location.origin}/proxy-stream?src=${encodeURIComponent(normalized)}`;
        } catch (_) {
            return '';
        }
    };

    const resolveMpegTsPlaybackUrl = (rawUrl) => buildProxyStreamUrl(rawUrl) || normalizeWebPlaybackUrl(rawUrl);

    const isBrowserUnsupportedMediaError = (error) => {
        const text = `${error?.name || ''} ${error?.message || error || ''}`.toLowerCase();
        return text.includes('notsupportederror')
            || text.includes('no supported source')
            || text.includes('not browser-compatible')
            || text.includes('src_not_supported')
            || text.includes('media-error-code 4')
            || text.includes('media-error-4')
            || text.includes('media-error-code code 4');
    };

    const describeMpegTsFailure = (error) => {
        if (isBrowserUnsupportedMediaError(error)) {
            return 'This MPEG-TS stream is not browser-compatible. Use VLC/external playback; web transcoding is no longer available.';
        }
        const message = String(error?.message || error || '').trim();
        return message ? `MPEGTS playback failed: ${message}` : 'MPEGTS playback failed.';
    };

    const normalizeDisplayText = (value) => {
        const raw = String(value ?? '');
        if (!raw) return raw;
        const suspicious = /[\u00C0-\u00FF]|â|Ã|ð|Ð|þ|Þ|ý|Ý/.test(raw);
        if (!suspicious) {
            return raw;
        }
        try {
            const bytes = Uint8Array.from(raw, char => char.charCodeAt(0));
            const decoded = new TextDecoder('utf-8', {fatal: false}).decode(bytes);
            if (!decoded || decoded === raw) {
                return raw;
            }
            if (decoded.includes('\uFFFD') && !raw.includes('\uFFFD')) {
                return raw;
            }
            return decoded;
        } catch (_) {
            return raw;
        }
    };

    const SHAKA_MAX_QUALITY_KEY = 'uiptv.shaka.quality.max';
    const getShakaMaxQuality = () => {
        if (!window.localStorage) return false;
        return localStorage.getItem(SHAKA_MAX_QUALITY_KEY) === '1';
    };
    const setShakaMaxQuality = (enabled) => {
        if (!window.localStorage) return;
        localStorage.setItem(SHAKA_MAX_QUALITY_KEY, enabled ? '1' : '0');
    };

    const emitPlayerEvent = (name, detail = {}) => {
        const payload = {
            ...detail,
            ts: Date.now()
        };
        try {
            window.dispatchEvent(new CustomEvent(name, {detail: payload}));
        } catch (_) {
            // Ignore dispatch errors.
        }
        try {
            document.dispatchEvent(new CustomEvent(name, {detail: payload}));
        } catch (_) {
            // Ignore dispatch errors.
        }
        try {
            localStorage.setItem(`uiptv.player.event.${name}`, JSON.stringify(payload));
        } catch (_) {
            // Ignore storage errors.
        }
        return payload;
    };

    const notifyPlayerClose = (detail = {}) => emitPlayerEvent('uiptv:player:close', detail);

    window.UIPTVPlaybackUtils = {
        isTsLikeUrl,
        canUseMpegts,
        resolvePlaybackModeLabel,
        downgradeHttpsToHttpForKnownPaths,
        normalizeWebPlaybackUrl,
        buildProxyStreamUrl,
        resolveMpegTsPlaybackUrl,
        isBrowserUnsupportedMediaError,
        describeMpegTsFailure,
        normalizeDisplayText,
        getShakaMaxQuality,
        setShakaMaxQuality,
        emitPlayerEvent,
        notifyPlayerClose
    };
})();
