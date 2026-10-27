const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const context = {window: {}};
vm.runInNewContext(fs.readFileSync('api-server/src/main/resources/web/javascript/bookmark-watch-utils.js', 'utf8'), context);
const utils = context.window.UIPTVBookmarkWatchUtils;
const key = item => JSON.stringify([item.accountName, item.accountAction, item.channelId]);
const item = (channelId, accountName = 'account', accountAction = 'itv') => ({
    channelId, accountName, accountAction, channelName: channelId, accountId: '17',
    sourceChannelDbId: '29', sourceCategoryId: '31', playbackQuery: '?accountId=17&channelId=29&categoryId=31'
});

test('unsaved channel-list playback is replayable with original local and provider identities', () => {
    const snapshot = item('provider-channel');
    const recent = utils.recentPlaybackItems([{key: key(snapshot), playCount: 3, lastPlayedAt: 42, snapshot}], [], key, () => '');
    assert.equal(recent.length, 1);
    assert.equal(recent[0].isUnsavedRecent, true);
    assert.equal(recent[0].channelId, 'provider-channel');
    assert.equal(recent[0].sourceChannelDbId, '29');
    assert.equal(recent[0].playbackQuery, snapshot.playbackQuery);
    assert.equal(recent[0].recentPlayCount, 3);
});

test('saved favourites replace history snapshots without duplicates and removing them preserves Recent', () => {
    const snapshot = item('one');
    const history = [{key: key(snapshot), snapshot, playCount: 2, lastPlayedAt: 10}];
    const saved = {...snapshot, dbId: '99', channelName: 'Updated title'};
    const recent = utils.recentPlaybackItems(history, [saved], key, () => '');
    assert.equal(recent.length, 1);
    assert.equal(recent[0].dbId, '99');
    assert.equal(recent[0].channelName, 'Updated title');
    assert.equal(recent[0].isUnsavedRecent, false);
    assert.equal(utils.recentPlaybackItems(history, [], key, () => '')[0].isUnsavedRecent, true);
});

test('ranking is bounded and provider IDs cannot collide across accounts and modes', () => {
    const snapshots = [item('same'), item('same', 'other'), item('same', 'account', 'vod')];
    const history = snapshots.map((snapshot, index) => ({key: key(snapshot), snapshot, playCount: index + 1, lastPlayedAt: index}));
    const recent = utils.recentPlaybackItems(history, [], key, () => '', 2);
    assert.equal(recent.length, 2);
    assert.equal(recent[0].accountAction, 'vod');
    assert.equal(recent[1].accountName, 'other');
});

test('legacy bookmark-ID history remains readable and media bodies are never retained', () => {
    const saved = {...item('one'), dbId: '99'};
    assert.equal(utils.recentPlaybackItems([{key: '99', playCount: 1}], [saved], key, () => '').length, 1);
    const snapshot = utils.recentPlaybackSnapshot({...saved, mediaBody: 'must not persist', epg: {}});
    assert.equal(snapshot.mediaBody, undefined);
    assert.equal(snapshot.epg, undefined);
    assert.equal(utils.recentPlaybackSnapshot({channelId: 'broken'}), null);
});

test('channel list and Recent replay route through the same local channel ID without bookmark IDs', () => {
    const spa = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
    const recorded = [], played = [];
    const fixture = {
        URL, URLSearchParams, window: {location: {origin: 'http://fixture'}},
        scrollToTop() {}, setPlaybackNavigation() {}, capturePlaybackContext() { return {}; },
        viewState: {value: 'channels'}, filteredChannels: {value: []}, filteredSeriesEpisodes: {value: []},
        filteredBookmarks: {value: []}, contentMode: {value: 'itv'}, currentContext: {value: {accountId: '17'}},
        resolvePlaybackCategoryIdForChannel() { return '31'; },
        buildPlayerUrlForChannel() { return 'http://fixture/player?accountId=17&channelId=29&categoryId=31&mode=itv'; },
        resolveAccountName() { return 'account'; }, resolveAccountId() { return '17'; },
        resolveLogoUrl(value) { return value; }, appendPlaybackCompatParams(params, mode) { params.set('mode', mode); },
        recordRecentlyPlayedBookmark(value) { recorded.push(value); },
        startPlayback(url, channel) { played.push({url, channel}); }
    };
    vm.createContext(fixture);
    vm.runInContext(spa.slice(spa.indexOf('        const playChannel ='), spa.indexOf('        const handleChannelSelection ='))
        + '\nObject.assign(this, {playChannel, playBookmark});', fixture);
    fixture.playChannel({dbId: '29', channelId: 'provider-channel', name: 'News', cmd: 'stream'});
    assert.equal(recorded[0].channelId, 'provider-channel');
    assert.equal(recorded[0].sourceChannelDbId, '29');
    const snapshot = utils.recentPlaybackSnapshot(recorded[0]);
    fixture.playBookmark({...snapshot, dbId: 'recent:key', isUnsavedRecent: true});
    assert.equal(played[1].url, played[0].url);
    assert.equal(played[1].channel.bookmarkId, '');
    assert.equal(played[1].channel.dbId, '29');
    assert.equal(recorded.length, 2);
});

test('playing a legacy bookmark keeps its play count when upgrading the history identity', () => {
    const spa = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
    const snapshot = {...item('one'), dbId: '99'};
    const history = {value: [{key: '99', accountName: 'account', mode: 'itv', channelName: 'one', playCount: 7}]};
    const fixture = {
        recentBookmarkHistory: history, getRecentBookmarkKey: key, bookmarkWatchUtils: utils,
        normalizePlaybackMode(value) { return value; },
        getLegacyRecentBookmarkNameKey(value) { return JSON.stringify([value.accountName, value.accountAction || value.mode, value.channelName]); },
        saveRecentBookmarkHistory(value) { history.value = value; }
    };
    vm.createContext(fixture);
    vm.runInContext(spa.slice(spa.indexOf('        const recordRecentlyPlayedBookmark ='), spa.indexOf('        const removeRecentlyPlayedBookmark ='))
        + '\nthis.record = recordRecentlyPlayedBookmark;', fixture);
    fixture.record(snapshot);
    assert.equal(history.value.length, 1);
    assert.equal(history.value[0].playCount, 8);
    fixture.record(snapshot);
    assert.equal(history.value[0].playCount, 9);
});
