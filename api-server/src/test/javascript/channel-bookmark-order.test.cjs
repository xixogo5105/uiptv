const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const utilitySource = fs.readFileSync('api-server/src/main/resources/web/javascript/bookmark-watch-utils.js', 'utf8');
const spaSource = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const htmlSource = fs.readFileSync('api-server/src/main/resources/web/index.html', 'utf8');
const serviceWorkerSource = fs.readFileSync('api-server/src/main/resources/web/sw.js', 'utf8');
const utilityContext = {window: {}};
vm.runInNewContext(utilitySource, utilityContext);
const utils = utilityContext.window.UIPTVBookmarkWatchUtils;

test('bookmark matching scopes channels by account, identity, and category like desktop', () => {
    const bookmarks = [
        {accountName: 'Sports', channelId: 'provider-1', cmd: 'stream-1', channelName: 'News', categoryJson: '{"categoryId":"cat-1","title":"Sports"}'},
        {accountName: 'Other', channelId: 'provider-2', cmd: 'stream-2', channelName: 'Movie', categoryJson: '{"categoryId":"cat-1","title":"Sports"}'},
        {accountName: 'Sports', channelId: '', cmd: '', channelName: 'Name Only', categoryJson: '{"categoryId":"cat-1","title":"Sports"}'}
    ];
    const index = utils.buildChannelBookmarkIndex(bookmarks, 'Sports');

    assert.equal(utils.isChannelBookmarked(
        {channelId: 'provider-1', cmd: 'different', name: 'News'}, index,
        {categoryId: 'cat-1', categoryTitle: 'Sports'}
    ), true);
    assert.equal(utils.isChannelBookmarked(
        {channelId: 'provider-1', cmd: 'different', name: 'News'}, index,
        {categoryId: 'cat-2', categoryTitle: 'Movies'}
    ), false);
    assert.equal(utils.isChannelBookmarked(
        {channelId: 'provider-2', cmd: 'stream-2', name: 'Movie'}, index,
        {categoryId: 'cat-1', categoryTitle: 'Sports', allCategories: true}
    ), false, 'bookmarks from another account are excluded from the channel list');
    assert.equal(utils.isChannelBookmarked(
        {channelId: '', cmd: '', name: 'name only'}, index,
        {categoryId: 'cat-1', categoryTitle: 'sports'}
    ), true, 'name matching is retained for legacy items without IDs or commands');
});

test('bookmark category resolution prefers local IDs when they collide with provider IDs', () => {
    const start = spaSource.indexOf('        const resolveChannelBookmarkCategory = channel => {');
    const end = spaSource.indexOf('        const isChannelBookmarked = channel => {', start);
    assert.ok(start >= 0 && end > start, 'SPA must resolve the selected channel category');

    const context = {
        currentContext: {value: {categoryId: '8', accountType: 'STALKER_PORTAL'}},
        categories: {value: [
            {dbId: '6', categoryId: '8', title: 'URDU'},
            {dbId: '8', categoryId: '6', title: 'SPORTS'}
        ]},
        isAllCategory: category => String(category?.title || '').toLowerCase() === 'all'
    };
    vm.createContext(context);
    vm.runInContext(`${spaSource.slice(start, end)}\nthis.resolveChannelBookmarkCategory = resolveChannelBookmarkCategory;`, context);

    const category = context.resolveChannelBookmarkCategory({categoryId: '8'});
    assert.equal(category.categoryId, '6');
    assert.equal(category.categoryTitle, 'SPORTS');

    const bookmarkIndex = utils.buildChannelBookmarkIndex([{
        accountName: 'Sports account',
        channelId: '3078',
        cmd: 'bookmark-command',
        channelName: 'Sky Sports Cricket HD',
        categoryJson: '{"categoryId":"6","title":"SPORTS"}'
    }], 'Sports account');
    assert.equal(utils.isChannelBookmarked({
        channelId: '3078',
        cmd: 'catalog-command',
        name: 'Sky Sports Cricket HD'
    }, bookmarkIndex, category), true);
});

test('bookmarked live channels sort first while preserving order within each group', () => {
    const start = spaSource.indexOf('        const filteredChannels = computed(() =>');
    const end = spaSource.indexOf('        const filteredEpisodes = computed(() =>', start);
    assert.ok(start >= 0 && end > start, 'SPA must define the filtered channel list');

    const context = {
        channels: {value: [
            {id: 'first', name: 'First'},
            {id: 'saved-1', name: 'Saved One'},
            {id: 'second', name: 'Second'},
            {id: 'saved-2', name: 'Saved Two'}
        ]},
        contentMode: {value: 'itv'},
        searchQuery: {value: ''},
        isChannelBookmarked: channel => channel.id.startsWith('saved'),
        computed: getter => ({get value() { return getter(); }})
    };
    vm.createContext(context);
    vm.runInContext(`${spaSource.slice(start, end)}\nthis.filteredChannels = filteredChannels;`, context);
    assert.deepEqual(Array.from(context.filteredChannels.value, channel => channel.id), [
        'saved-1', 'saved-2', 'first', 'second'
    ]);

    context.contentMode.value = 'vod';
    assert.deepEqual(Array.from(context.filteredChannels.value, channel => channel.id), [
        'first', 'saved-1', 'second', 'saved-2'
    ]);
});

test('channel cards show a bookmark marker and cache-bust the updated matcher', () => {
    assert.match(htmlSource, /channel-bookmark-indicator[^>]+v-if="isChannelBookmarked\(channel\)"/);
    assert.match(htmlSource, /bi bi-heart-fill uiptv-favorite-icon uiptv-favorite-on/,
        'bookmarked channels use the same red heart as the web player favorite control');
    assert.doesNotMatch(htmlSource, /bi-bookmarks?/, 'web navigation and empty states should not use bookmark glyphs');
    assert.match(htmlSource, /bookmark-watch-utils\.js\?v=20261028channelBookmarks1/);
    assert.match(htmlSource, /spa\.js\?v=20261028channelBookmarks2/);
    assert.match(serviceWorkerSource, /CACHE_NAME = 'uiptv-cache-v128'/);
});
