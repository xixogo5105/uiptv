const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');

test('NATIVE_PROXY strategy uses native playback before adaptive-engine fallbacks', () => {
    const hint = source.indexOf("strategyHint === 'NATIVE_PROXY'");
    const native = source.indexOf("await loadNative({...channel, url: proxyUrl}, requestId);", hint);
    const override = source.indexOf("if (override === 'proxy')", hint);

    assert.ok(hint >= 0, 'SPA must recognize the server proxy strategy hint');
    assert.ok(native > hint && native < override, 'proxy strategy must select native playback first');
});

test('playback request ownership lets a newer stream survive a failed older request', () => {
    const start = source.indexOf('        const playbackRequestState = {');
    const end = source.indexOf('        const isCurrentPlaybackRequest', start);
    assert.ok(start >= 0 && end > start, 'SPA must define the playback request coordinator');

    const context = {AbortController};
    vm.createContext(context);
    vm.runInContext(`${source.slice(start, end)}\nthis.playbackRequestState = playbackRequestState;`, context);

    const first = context.playbackRequestState.begin();
    const second = context.playbackRequestState.begin();
    assert.equal(first.controller.signal.aborted, true, 'starting a new request must abort the old fetch');
    assert.equal(context.playbackRequestState.isCurrent(first.id), false, 'the failed request must become stale');
    assert.equal(context.playbackRequestState.finish(first.id, first.controller), false, 'stale cleanup must not clear the active request');
    assert.equal(context.playbackRequestState.isCurrent(second.id), true, 'the newer request remains active');

    assert.equal(context.playbackRequestState.finish(second.id, second.controller), true, 'the active request can finish normally');
    const recovery = context.playbackRequestState.begin();
    assert.ok(recovery.id > second.id, 'a later valid stream receives a fresh request token');
    assert.equal(context.playbackRequestState.isCurrent(recovery.id), true);
});

test('playback startup fails fast on an HTTP player-resolution error', () => {
    const fetchStart = source.indexOf("const response = await fetch(url, {signal: controller.signal});");
    const fetchFailure = source.indexOf("throw new Error(`Playback request failed (${response.status})`);", fetchStart);
    assert.ok(fetchStart >= 0, 'SPA must fetch the player resolution response');
    assert.ok(fetchFailure > fetchStart, 'SPA must reject non-success player responses before parsing media data');
});

test('native startup observes media element errors instead of waiting for the timeout', () => {
    const helper = source.indexOf('        const withPlaybackMediaError =');
    const native = source.indexOf('withPlaybackMediaError(video, () => playWithGestureFallback(() => video.play()), \'Native\')', helper);
    assert.ok(helper >= 0, 'SPA must expose media-error startup handling');
    assert.ok(native > helper, 'native playback must use media-error startup handling');
});

test('SPA recording assets match the desktop recording-dot geometry and cache version', () => {
    const on = fs.readFileSync('api-server/src/main/resources/web/images/broadcast-on.svg', 'utf8');
    const off = fs.readFileSync('api-server/src/main/resources/web/images/broadcast-off.svg', 'utf8');
    const html = fs.readFileSync('api-server/src/main/resources/web/index.html', 'utf8');
    const sw = fs.readFileSync('api-server/src/main/resources/web/sw.js', 'utf8');

    assert.match(on, /viewBox="0 0 16 16"/);
    assert.match(off, /viewBox="0 0 16 16"/);
    assert.match(on, /circle cx="8" cy="8" r="4" fill="#e53935"/);
    assert.match(off, /circle cx="8" cy="8" r="4" fill="#8a97a5"/);
    assert.match(source, /broadcast-on\.svg\?v=20261017b/);
    assert.match(source, /broadcast-off\.svg\?v=20261017b/);
    assert.match(html, /spa\.js\?v=20261017playbackRecovery2/);
    assert.match(sw, /CACHE_NAME = 'uiptv-cache-v81'/);
});

