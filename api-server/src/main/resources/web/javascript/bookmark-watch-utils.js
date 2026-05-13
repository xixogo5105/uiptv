(function() {
    'use strict';

    const normalizeBookmark = (bookmark, resolveLogoUrl) => {
        if (!bookmark || typeof bookmark !== 'object') return bookmark;
        return {
            ...bookmark,
            logo: typeof resolveLogoUrl === 'function' ? resolveLogoUrl(bookmark.logo) : bookmark.logo
        };
    };

    const loadBookmarksPaged = async ({
        origin,
        pageSize,
        normalize,
        onReset,
        onBatch,
        onAfterBatch,
        onError,
        onDone,
        nextTick
    }) => {
        try {
            if (typeof onReset === 'function') {
                onReset();
            }
            let offset = 0;
            while (true) {
                const response = await fetch(`${origin}/bookmarks?offset=${offset}&limit=${pageSize}`);
                const data = await response.json();
                const batch = Array.isArray(data) ? data.map(item => normalize ? normalize(item) : item) : [];
                if (batch.length === 0) {
                    break;
                }
                if (typeof onBatch === 'function') {
                    onBatch(batch);
                }
                if (typeof onAfterBatch === 'function') {
                    onAfterBatch();
                }
                offset += batch.length;
                if (batch.length < pageSize) {
                    break;
                }
                if (typeof nextTick === 'function') {
                    await nextTick();
                }
            }
        } catch (e) {
            if (typeof onError === 'function') {
                onError(e);
            }
        } finally {
            if (typeof onDone === 'function') {
                onDone();
            }
        }
    };

    const loadBookmarkCategories = async (origin) => {
        const response = await fetch(`${origin}/bookmarks?view=categories`);
        return await response.json();
    };

    const normalizeWatchingNowVodRow = (row, resolveLogoUrl, normalizeChannel) => {
        const normalized = {
            ...row,
            key: String(row?.key || `${row?.accountId || ''}|${row?.categoryId || ''}|${row?.vodId || ''}`),
            vodLogo: typeof resolveLogoUrl === 'function' ? resolveLogoUrl(row?.vodLogo) : row?.vodLogo
        };
        if (row?.playItem) {
            normalized.playItem = typeof normalizeChannel === 'function'
                ? normalizeChannel(row.playItem)
                : row.playItem;
        } else {
            normalized.playItem = null;
        }
        return normalized;
    };

    const fetchWatchingNowVod = async (origin) => {
        const response = await fetch(`${origin}/watchingNowVod`);
        return await response.json();
    };

    window.UIPTVBookmarkWatchUtils = {
        normalizeBookmark,
        loadBookmarksPaged,
        loadBookmarkCategories,
        normalizeWatchingNowVodRow,
        fetchWatchingNowVod
    };
})();
