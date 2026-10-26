const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const controlsSource = fs.readFileSync('api-server/src/main/resources/web/javascript/player-controls.js', 'utf8');
const sharedPlayerSource = fs.readFileSync('api-server/src/main/resources/web/javascript/shared-player.js', 'utf8');
const spaSource = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');

function loadControls() {
    const context = {window: {}};
    vm.createContext(context);
    vm.runInContext(controlsSource, context);
    return context.window.UIPTVControls;
}

test('playback navigation preserves visible order and skips unplayable items', () => {
    const controls = loadControls();
    const played = [];
    const items = [
        {id: 'one', cmd: 'one'},
        {id: 'hidden', cmd: ''},
        {id: 'two', cmd: 'two'},
        {id: 'three', cmd: 'three'}
    ];
    const navigation = controls.createPlaybackNavigation(items, items[2], item => played.push(item.id));

    assert.equal(navigation.currentIndex, 1);
    assert.deepEqual(Array.from(navigation.items, item => item.id), ['one', 'two', 'three']);
    const previous = controls.movePlaybackNavigation(navigation, -1);
    assert.equal(previous.currentIndex, 0);
    previous.play(previous.items[previous.currentIndex]);
    const next = controls.movePlaybackNavigation(navigation, 1);
    assert.equal(next.currentIndex, 2);
    next.play(next.items[next.currentIndex]);
    assert.deepEqual(played, ['one', 'three']);
    assert.equal(controls.movePlaybackNavigation(navigation, -2), null);
    assert.equal(controls.movePlaybackNavigation(navigation, 2), null);
});

test('navigation is hidden unless a multi-item list contains the current playable item', () => {
    const controls = loadControls();
    const playable = item => !!item.cmd;

    assert.equal(controls.createPlaybackNavigation([{cmd: 'one'}], {cmd: 'one'}, () => {}, playable), null);
    assert.equal(controls.createPlaybackNavigation([{cmd: 'one'}, {cmd: 'two'}], {cmd: 'outside'}, () => {}, playable), null);
});

test('shared player exposes previous and next actions in its external controls', () => {
    assert.match(sharedPlayerSource, /data-action="previous-video"/);
    assert.match(sharedPlayerSource, /data-action="next-video"/);
    assert.match(sharedPlayerSource, /setNavigationAvailability/);
});

test('web queues are built from the visible channel, episode, bookmark and Watching Now lists', () => {
    assert.match(spaSource, /filteredSeriesEpisodes\.value\s*:\s*filteredChannels\.value/);
    assert.match(spaSource, /setPlaybackNavigation\(filteredBookmarks\.value, bookmark/);
    assert.match(spaSource, /filteredWatchingNowVodRows\.value/);
    assert.match(spaSource, /playChannelFromNavigation\(target, contextSnapshot\)/);
});
