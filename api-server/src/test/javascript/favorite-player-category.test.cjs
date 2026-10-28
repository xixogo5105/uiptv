const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const webRoot = 'api-server/src/main/resources/web';
const utilitySource = fs.readFileSync(`${webRoot}/javascript/bookmark-watch-utils.js`, 'utf8');
const spaSource = fs.readFileSync(`${webRoot}/javascript/spa.js`, 'utf8');
const playerSource = fs.readFileSync(`${webRoot}/javascript/shared-player.js`, 'utf8');
const indexSource = fs.readFileSync(`${webRoot}/index.html`, 'utf8');
const browser = {window: {}};
vm.runInNewContext(utilitySource, browser);

test('favorite category loading returns categories and rejects failed requests', async () => {
    const originalFetch = browser.fetch;
    try {
        browser.fetch = async () => ({ok: true, json: async () => [{id: '4', name: 'Sports'}]});
        const categories = await browser.window.UIPTVBookmarkWatchUtils.loadBookmarkCategories('http://localhost');
        assert.deepEqual(Array.from(categories, category => [category.id, category.name]), [['4', 'Sports']]);

        browser.fetch = async () => ({ok: false, status: 503, json: async () => ({})});
        await assert.rejects(
            browser.window.UIPTVBookmarkWatchUtils.loadBookmarkCategories('http://localhost'),
            /Unable to load favourite categories \(503\)/
        );
    } finally {
        if (originalFetch === undefined) delete browser.fetch;
        else browser.fetch = originalFetch;
    }
});

test('player favorite toggles optimistically and requires an explicit destination category', () => {
    const addPath = spaSource.indexOf('const savePlayerFavorite = async category => {');
    const togglePath = spaSource.indexOf('const toggleFavorite = async () => {', addPath);
    const saveFlow = spaSource.slice(addPath, togglePath);
    const pickerMarkup = indexSource.slice(indexSource.indexOf('id="favorite-category-title"'));

    assert.match(spaSource, /setPlayerFavoriteOverride\(channel, true\);\s*favoritePickerChannel\.value = channel;\s*favoriteCategoryPickerOpen\.value = true;/);
    assert.match(saveFlow, /bookmarkCategoryId:\s*category\.id/);
    assert.match(saveFlow, /if \(!response\.ok\) throw/);
    assert.match(saveFlow, /setPlayerFavoriteOverride\(channel, false\);/);
    assert.match(pickerMarkup, /v-for="category in favoriteCategoryOptions"/);
    assert.doesNotMatch(pickerMarkup.slice(0, pickerMarkup.indexOf('id="clear-recent-title"')), />\s*All\s*</);
    assert.match(playerSource, /favoriteBtn\.disabled = !!isFavoritePending/);
});

test('translated messages use favourite terminology instead of bookmark wording', () => {
    const messagesRoot = 'core/src/main/resources/i18n';
    const previousTerms = /bookmark|bookmarked|bookmarks|Lesezeichen|signets?|marcadores?|segnalibr[oi]|закладк|书签|書籤|ブックマーク|북마크|புத்தகக்குறி|புக்மார்க்|బుక్.?మార్క్|บุ๊กมาร์ก|yer işareti|yer imi|dấu trang|बुकमार्क|بک.?مارک|বুকমার্ক|બુકમાર્ક|ਬੁੱਕਮਾਰਕ/i;
    const files = fs.readdirSync(messagesRoot).filter(file => /^messages.*\.properties$/.test(file));
    const oldValues = [];
    for (const file of files) {
        const lines = fs.readFileSync(`${messagesRoot}/${file}`, 'utf8').split(/\r?\n/);
        for (const line of lines) {
            if (!line || line.startsWith('#') || line.startsWith('!')) continue;
            const separator = line.indexOf('=');
            if (separator < 0) continue;
            const value = line.slice(separator + 1);
            if (previousTerms.test(value)) oldValues.push(`${file}: ${line.slice(0, separator)}`);
        }
    }
    assert.deepEqual(oldValues, []);
});
