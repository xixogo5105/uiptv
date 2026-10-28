(function () {
    const getSourceUrlCandidates = (url) => {
        const candidates = [];
        let current = String(url || '').trim();
        for (let depth = 0; current && depth < 3 && !candidates.includes(current); depth += 1) {
            candidates.push(current);
            try {
                const parsed = new URL(current, window.location.origin);
                if (!parsed.pathname.toLowerCase().endsWith('/proxy-stream')) break;
                const source = parsed.searchParams.get('src');
                if (!source) break;
                current = source.trim();
            } catch (_) {
                break;
            }
        }
        return candidates;
    };

    const probeStreamFormat = async (url, signal) => {
        const controller = new AbortController();
        const cancel = () => controller.abort();
        if (signal?.aborted) return '';
        signal?.addEventListener('abort', cancel, {once: true});
        const timeout = setTimeout(cancel, 4000);
        let response, reader;
        try {
            response = await fetch(url, {signal: controller.signal,
                headers: {Range: 'bytes=0-2047', Accept: '*/*'}});
            if (!response.ok) return '';
            const type = String(response.headers.get('content-type') || '').toLowerCase();
            if (type.includes('mpegurl')) return 'hls';
            if (type.includes('dash+xml')) return 'dash';
            if (type.includes('mp2t')) return 'mpegts';
            if (type.includes('matroska')) return 'mkv';
            if (type.includes('video/mp4')) return 'native';
            if (!response.body) return '';
            reader = response.body.getReader();
            const prefix = new Uint8Array(2048);
            let length = 0;
            while (length < prefix.length) {
                const {done, value} = await reader.read();
                if (done) break;
                const count = Math.min(value.length, prefix.length - length);
                prefix.set(value.subarray(0, count), length);
                length += count;
                const text = new TextDecoder().decode(prefix.subarray(0, length)).trimStart();
                if (text.startsWith('#EXTM3U')) return 'hls';
                if (text.includes('<MPD')) return 'dash';
                if (length >= 8 && prefix[4] === 102 && prefix[5] === 116 && prefix[6] === 121 && prefix[7] === 112) return 'native';
                if (length >= 4 && prefix[0] === 0x1a && prefix[1] === 0x45 && prefix[2] === 0xdf && prefix[3] === 0xa3) return 'mkv';
                if (length >= 377 && prefix[0] === 0x47 && prefix[188] === 0x47 && prefix[376] === 0x47) return 'mpegts';
            }
            return '';
        } catch (_) {
            return '';
        } finally {
            // A provider may ignore Range and send an endless live body.
            controller.abort();
            if (reader) await reader.cancel().catch(() => {});
            else if (response?.body) await response.body.cancel().catch(() => {});
            clearTimeout(timeout);
            signal?.removeEventListener('abort', cancel);
        }
    };

    const isMkvLikeUrl = url => getSourceUrlCandidates(url).some(value => {
        try {
            const parsed = new URL(value, window.location.origin);
            return /\.mkv$/i.test(parsed.pathname) || /\.mkv$/i.test(parsed.searchParams.get('stream') || '')
                || /^mkv$/i.test(parsed.searchParams.get('extension') || '');
        } catch (_) { return false; }
    });

    const isTsLikeUrl = (url, manifestType = '', mode = '') => {
        const normalizedManifestType = String(manifestType || '').trim().toLowerCase();
        const normalizedMode = String(mode || '').trim().toLowerCase();
        const candidates = getSourceUrlCandidates(url).map(value => value.toLowerCase());
        if (normalizedManifestType === 'hls' || normalizedManifestType === 'm3u8'
            || normalizedManifestType === 'dash' || normalizedManifestType === 'mpd') {
            return false;
        }
        if (candidates.some(value => /\.m3u8?(?:[?#]|$)/i.test(value))) return false;
        const explicitlyTransportStream = normalizedManifestType === 'ts'
            || normalizedManifestType === 'mpegts'
            || normalizedManifestType === 'mpeg2ts'
            || candidates.some(value => value.includes('.ts?')
                || value.endsWith('.ts')
                || value.includes('.m2ts?')
                || value.endsWith('.m2ts')
                || value.includes('extension=ts'));
        if (explicitlyTransportStream) return true;
        if (normalizedMode === 'vod' || normalizedMode === 'series') return false;
        return candidates.some(value => value.includes('/live/play/')
            || value.includes('/play/live.php')
            || /\/\d+(?:\?|$)/.test(value));
    };

    const isHlsLikeUrl = (url, manifestType = '') => {
        const normalizedManifestType = String(manifestType || '').trim().toLowerCase();
        if (normalizedManifestType === 'hls' || normalizedManifestType === 'm3u8') return true;
        return getSourceUrlCandidates(url).some(value => /\.m3u8?(?:[?#]|$)/i.test(value));
    };

    const isDashLikeUrl = (url, manifestType = '') => {
        const normalizedManifestType = String(manifestType || '').trim().toLowerCase();
        if (normalizedManifestType === 'dash' || normalizedManifestType === 'mpd') return true;
        return getSourceUrlCandidates(url).some(value => /\.mpd(?:[?#]|$)/i.test(value));
    };

    const canUseMpegts = () => {
        const engine = window.mpegts;
        if (!engine) {
            console.warn('mpegts.js is not loaded. MPEG-TS playback requires mpegts.js.');
            return false;
        }
        if (typeof engine.isSupported !== 'function') {
            console.warn('mpegts.js is loaded but isSupported() is not available.');
            return false;
        }
        const supported = engine.isSupported();
        if (!supported) {
            console.warn('mpegts.js reports that MPEG-TS is not supported in this browser.');
        }
        return supported;
    };

        const resolvePlaybackModeLabel = (url, engine = '', formatHint = '') => {
            const normalizedEngine = String(engine || '').trim().toLowerCase();
            const normalizedHint = String(formatHint || '').trim().toLowerCase();
            const sourceCandidates = getSourceUrlCandidates(url);
            const route = sourceCandidates.length > 1 ? 'proxy' : (() => {
                try {
                    const parsed = new URL(String(url || '').trim(), window.location.origin);
                    return parsed.pathname.toLowerCase() === '/proxy-stream' && parsed.searchParams.has('src')
                        ? 'proxy' : 'direct';
                } catch (_) {
                    return /\/proxy-stream(?:[?#]|$)/i.test(String(url || '')) ? 'proxy' : 'direct';
                }
            })();
            let format = '';
            if (normalizedEngine === 'mpegts' || ['mpegts', 'mpeg2ts', 'ts', 'm2ts'].includes(normalizedHint)) {
                format = 'MPEG-TS';
            } else if (['hls', 'm3u8'].includes(normalizedHint) || isHlsLikeUrl(url, formatHint)) {
                format = 'HLS';
            } else if (['dash', 'mpd'].includes(normalizedHint) || isDashLikeUrl(url, formatHint)) {
                format = 'DASH';
            } else if (normalizedEngine === 'mkv' || normalizedHint === 'mkv' || isMkvLikeUrl(url)) {
                format = 'MKV';
            } else {
                const source = sourceCandidates[sourceCandidates.length - 1] || String(url || '');
                try {
                    const path = new URL(source, window.location.origin).pathname.toLowerCase();
                    if (/\.(mp4|m4v)$/.test(path)) format = 'MP4';
                    else if (/\.(mp3|aac|m4a|ogg|opus|wav)$/.test(path)) format = 'Audio';
                } catch (_) { /* Keep the format unset for opaque URLs. */ }
                if (!format && normalizedEngine === 'videojs') format = 'Video.js';
                if (!format && normalizedEngine === 'shaka') format = 'Shaka';
            }
            return format ? `${route} ${format}` : route;
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
        try {
            const parsed = new URL(value, window.location.origin);
            if (parsed.pathname === '/proxy-stream' && parsed.searchParams.has('src')) {
                // The backend may publish its LAN address while this page uses localhost,
                // HTTPS, or a reverse proxy. Keep media on the browser's own origin.
                return window.location.origin + parsed.pathname + parsed.search;
            }
        } catch (_) { /* Retain the original URL when it cannot be parsed. */ }
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

    const resolveMpegTsPlaybackUrl = (rawUrl) => normalizeWebPlaybackUrl(rawUrl);

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

    const isEnabledSetting = value => value === true || value === 1
        || String(value || '').trim().toLowerCase() === 'true'
        || String(value || '').trim() === '1';

    const shouldUseWebPlaybackProxy = (globalEnabled, accountEnabled) =>
        isEnabledSetting(globalEnabled) || isEnabledSetting(accountEnabled);

    const canPlayMimeType = (mimeType) => {
        const value = String(mimeType || '').trim();
        if (!value) return false;
        const mediaSource = window.ManagedMediaSource || window.MediaSource;
        if (mediaSource && typeof mediaSource.isTypeSupported === 'function' && mediaSource.isTypeSupported(value)) {
            return true;
        }
        try {
            const video = document.createElement('video');
            if (video && typeof video.canPlayType === 'function' && video.canPlayType(value)) {
                return true;
            }
        } catch (_) {
            // Browser probing only; fall through to unsupported.
        }
        try {
            const audio = document.createElement('audio');
            return !!(audio && typeof audio.canPlayType === 'function' && audio.canPlayType(value));
        } catch (_) {
            return false;
        }
    };

    const normalizeCodecToken = (codec) => String(codec || '').trim().toLowerCase();

    const isAc3Codec = (codec) => {
        const normalized = normalizeCodecToken(codec);
        return normalized === 'ac-3'
            || normalized === 'ac3'
            || normalized === 'mp4a.a5'
            || normalized === 'mp4a.a6'
            || normalized === 'ec-3'
            || normalized === 'eac3'
            || normalized === 'ec3';
    };

    const browserSupportsHlsCodec = (codec) => {
        const normalized = normalizeCodecToken(codec);
        if (!normalized) return true;
        if (normalized === 'ac3') return browserSupportsHlsCodec('ac-3');
        if (normalized === 'eac3' || normalized === 'ec3') return browserSupportsHlsCodec('ec-3');
        return canPlayMimeType(`audio/mp4; codecs="${normalized}"`)
            || canPlayMimeType(`video/mp2t; codecs="${normalized}"`)
            || canPlayMimeType(`video/mp4; codecs="${normalized}"`);
    };

    const extractHlsCodecs = (manifestText) => {
        const manifest = String(manifestText || '');
        const codecs = new Set();
        const regex = /CODECS="([^"]+)"/gi;
        let match;
        while ((match = regex.exec(manifest)) !== null) {
            String(match[1] || '')
                .split(',')
                .map(normalizeCodecToken)
                .filter(Boolean)
                .forEach(codec => codecs.add(codec));
        }
        return Array.from(codecs);
    };

    const describeUnsupportedHlsManifest = (manifestText) => {
        const codecs = extractHlsCodecs(manifestText);
        const unsupportedAc3 = codecs.find(codec => isAc3Codec(codec) && !browserSupportsHlsCodec(codec));
        if (unsupportedAc3) {
            return 'This HLS stream uses AC-3 audio, which this browser cannot decode. It can play in VLC, Android, or the desktop app; web playback needs an AAC audio stream or server-side transcoding.';
        }
        return '';
    };

    const describeMpegTsFailure = (error) => {
        const message = String(error?.message || error || '').trim();
        if (!message) return 'MPEGTS playback failed.';
        if (message.includes('notsupportederror') || message.includes('no supported source') || message.includes('src_not_supported')) {
            return 'This MPEG-TS stream is not browser-compatible. Use VLC/external playback; web transcoding is no longer available.';
        }
        return `MPEGTS playback failed: ${message}`;
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
        probeStreamFormat,
        isMkvLikeUrl,
        isTsLikeUrl,
        isHlsLikeUrl,
        isDashLikeUrl,
        canUseMpegts,
        resolvePlaybackModeLabel,
        downgradeHttpsToHttpForKnownPaths,
        normalizeWebPlaybackUrl,
        buildProxyStreamUrl,
        resolveMpegTsPlaybackUrl,
        isBrowserUnsupportedMediaError,
        shouldUseWebPlaybackProxy,
        describeUnsupportedHlsManifest,
        describeMpegTsFailure,
        normalizeDisplayText,
        getShakaMaxQuality,
        setShakaMaxQuality,
        emitPlayerEvent,
        notifyPlayerClose
    };
})();
