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

    const buildBookmarkCategoryTabs = (recentTab, categories, allTab, normalizeName) => {
        const tabs = [recentTab];
        for (const category of (Array.isArray(categories) ? categories : [])) {
            const id = String(category?.id || '').trim();
            const rawName = category?.name || '';
            const name = String(typeof normalizeName === 'function' ? normalizeName(rawName) : rawName).trim();
            if (!id || !name) continue;
            tabs.push({id, name});
        }
        tabs.push(allTab);
        return tabs;
    };

    const normalizeExact = value => String(value || '').trim();
    const normalizeLower = value => normalizeExact(value).toLowerCase();

    const readBookmarkSourceCategory = bookmark => {
        try {
            const category = JSON.parse(String(bookmark?.categoryJson || ''));
            if (category && typeof category === 'object') return category;
        } catch (_) { /* Older bookmarks may not carry source category JSON. */ }
        return {};
    };

    const buildChannelBookmarkIndex = (bookmarks, accountName) => {
        const account = normalizeExact(accountName);
        const index = {byChannelId: new Map(), byCommand: new Map(), byName: new Map()};
        if (!account) return index;
        for (const bookmark of (Array.isArray(bookmarks) ? bookmarks : [])) {
            if (normalizeExact(bookmark?.accountName) !== account) continue;
            const sourceCategory = readBookmarkSourceCategory(bookmark);
            const record = {
                channelId: normalizeExact(bookmark.channelId),
                command: normalizeExact(bookmark.cmd),
                name: normalizeLower(bookmark.channelName),
                categoryId: normalizeExact(sourceCategory.categoryId || bookmark.categoryId),
                categoryTitle: normalizeLower(sourceCategory.title || bookmark.categoryTitle)
            };
            const add = (map, key) => {
                if (!key) return;
                const records = map.get(key) || [];
                records.push(record);
                map.set(key, records);
            };
            add(index.byChannelId, record.channelId);
            add(index.byCommand, record.command);
            if (!record.channelId && !record.command) add(index.byName, record.name);
        }
        return index;
    };

    const isChannelBookmarked = (channel, index, {
        categoryId = '', categoryTitle = '', allCategories = false
    } = {}) => {
        if (!channel || !index) return false;
        const channelId = normalizeExact(channel.channelId);
        const command = normalizeExact(channel.cmd);
        const name = normalizeLower(channel.name || channel.channelName);
        const candidates = new Set([
            ...(index.byChannelId?.get(channelId) || []),
            ...(index.byCommand?.get(command) || []),
            ...(!channelId && !command ? (index.byName?.get(name) || []) : [])
        ]);
        const expectedCategoryId = normalizeExact(categoryId);
        const expectedCategoryTitle = normalizeLower(categoryTitle);
        return [...candidates].some(bookmark => {
            if (!allCategories) {
                const sameCategoryById = expectedCategoryId && bookmark.categoryId
                    && expectedCategoryId === bookmark.categoryId;
                const sameCategoryByTitle = expectedCategoryTitle && bookmark.categoryTitle
                    && expectedCategoryTitle === bookmark.categoryTitle;
                if (!sameCategoryById && !sameCategoryByTitle) return false;
            }
            if ((channelId && bookmark.channelId === channelId)
                || (command && bookmark.command === command)) return true;
            return !channelId && !command && !bookmark.channelId && !bookmark.command
                && !!name && bookmark.name === name;
        });
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

    // Persist only replay metadata. Media bodies never enter playback history.
    const recentPlaybackSnapshot = (item) => {
        if (!item || typeof item !== 'object' || Array.isArray(item)) return null;
        const fields = ['accountName', 'accountId', 'accountAction', 'channelId', 'channelName', 'logo', 'cmd',
            'drmType', 'drmLicenseUrl', 'clearKeysJson', 'inputstreamaddon', 'manifestType',
            'sourceChannelDbId', 'sourceCategoryId', 'playbackQuery'];
        const snapshot = {};
        for (const field of fields) snapshot[field] = String(item[field] || '');
        return snapshot.channelId && snapshot.playbackQuery ? snapshot : null;
    };

    const recentPlaybackItems = (history, bookmarks, keyFor, legacyKeyFor, limit = 25) => {
        const saved = new Map();
        for (const bookmark of bookmarks || []) {
            for (const key of [keyFor(bookmark), legacyKeyFor(bookmark), String(bookmark.dbId || '')]) {
                if (key && !saved.has(key)) saved.set(key, bookmark);
            }
        }
        const seen = new Set();
        return (history || []).map(entry => {
            const item = saved.get(entry.key) || recentPlaybackSnapshot(entry.snapshot);
            if (!item) return null;
            const identity = keyFor(item);
            if (seen.has(identity)) return null;
            seen.add(identity);
            return {...item, dbId: saved.has(entry.key) ? item.dbId : `recent:${entry.key}`,
                isUnsavedRecent: !saved.has(entry.key), recentPlayCount: entry.playCount, recentLastPlayedAt: entry.lastPlayedAt};
        }).filter(Boolean).sort((a, b) => Number(b.recentPlayCount) - Number(a.recentPlayCount)
            || Number(b.recentLastPlayedAt) - Number(a.recentLastPlayedAt)
            || String(a.channelName).localeCompare(String(b.channelName))).slice(0, limit);
    };

    window.UIPTVBookmarkWatchUtils = {
        recentPlaybackSnapshot,
        recentPlaybackItems,
        normalizeBookmark,
        loadBookmarksPaged,
        loadBookmarkCategories,
        buildBookmarkCategoryTabs,
        buildChannelBookmarkIndex,
        isChannelBookmarked,
        normalizeWatchingNowVodRow,
        fetchWatchingNowVod
    };
})();
