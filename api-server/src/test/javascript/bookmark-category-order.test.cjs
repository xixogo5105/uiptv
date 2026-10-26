const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const utilitySource = fs.readFileSync('api-server/src/main/resources/web/javascript/bookmark-watch-utils.js', 'utf8');
const spaSource = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const browser = {window: {}};
vm.runInNewContext(utilitySource, browser);

test('bookmark category tabs keep Recent first and All last', () => {
    const recent = {id: '__recently_played_bookmarks__', name: 'Recent'};
    const all = {id: '', name: 'All'};
    const tabs = browser.window.UIPTVBookmarkWatchUtils.buildBookmarkCategoryTabs(
        recent,
        [
            {id: 3, name: 'Movies'},
            {id: null, name: 'Ignored synthetic category'},
            {id: 7, name: '  Cricket  '},
            {id: 9, name: '   '}
        ],
        all,
        value => value
    );

    assert.deepEqual(Array.from(tabs, tab => [tab.id, tab.name]), [
        [recent.id, recent.name],
        ['3', 'Movies'],
        ['7', 'Cricket'],
        [all.id, all.name]
    ]);
    assert.match(spaSource, /bookmarkWatchUtils\.buildBookmarkCategoryTabs\(/);
});
