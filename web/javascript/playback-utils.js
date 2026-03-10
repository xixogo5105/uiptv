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
        if (normalizedEngine === 'youtube') return 'youtube';
        if (lowerUrl.includes('/proxy-stream')) return 'proxy';
        if (lowerUrl.includes('/hls/stream.m3u8')) return 'hls';
        if (normalizedEngine === 'mpegts') return 'mpegts';
        if (normalizedEngine === 'shaka') {
            if (lowerUrl.includes('.mpd')) return 'dash';
            if (lowerUrl.includes('.m3u8')) return 'hls';
            return 'shaka';
        }
        return 'direct';
    };

    const downgradeHttpsToHttpForKnownPaths = (url) => {
        const value = String(url || '').trim();
        const lower = value.toLowerCase();
        if (lower.startsWith('https://') && (lower.includes('/live/play/') || lower.includes('/play/movie.php'))) {
            return `http://${value.slice('https://'.length)}`;
        }
        return value;
    };

    const normalizeWebPlaybackUrl = (rawUrl) => {
        const value = String(rawUrl || '').trim();
        if (!value) return value;
        return downgradeHttpsToHttpForKnownPaths(value);
    };

    const buildForcedHlsPlaybackRequestUrl = (rawUrl) => {
        const baseUrl = String(rawUrl || '').trim();
        if (!baseUrl) return '';
        try {
            const parsed = new URL(baseUrl, window.location.origin);
            if (String(parsed.searchParams.get('preferHls') || '').trim() === '1') {
                return '';
            }
            parsed.searchParams.set('preferHls', '1');
            return parsed.toString();
        } catch (_) {
            return '';
        }
    };

    window.UIPTVPlaybackUtils = {
        isTsLikeUrl,
        canUseMpegts,
        resolvePlaybackModeLabel,
        downgradeHttpsToHttpForKnownPaths,
        normalizeWebPlaybackUrl,
        buildForcedHlsPlaybackRequestUrl
    };
})();
