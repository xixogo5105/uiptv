const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('api-server/src/main/resources/web/javascript/playback-utils.js', 'utf8');
const context = {window: {location: {origin: 'http://localhost:8080'}}, URL};
vm.createContext(context);
vm.runInContext(source, context);
const utils = context.window.UIPTVPlaybackUtils;
const proxy = value => `http://localhost:8080/proxy-stream?src=${encodeURIComponent(value)}`;
const spa = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');

test('format detection unwraps proxied HLS and DASH manifests', () => {
    assert.equal(utils.isHlsLikeUrl(proxy('https://provider.example/movie.m3u8')), true);
    assert.equal(utils.isDashLikeUrl(proxy('https://provider.example/movie.mpd')), true);
});

test('format detection routes explicit TS and live Stalker endpoints to MPEG-TS', () => {
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/episode.ts?token=x')), true);
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/play/live.php?cmd=123')), true);
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/123'), '', 'itv'), true);
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/play/movie.php?cmd=123'), '', 'vod'), false);
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/123'), '', 'vod'), false);
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/play/movie.php?cmd=123'), '', 'series'), false);
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/123'), '', 'series'), false);
});

test('an explicit HLS URL is not misclassified as MPEG-TS', () => {
    assert.equal(utils.isTsLikeUrl(proxy('https://provider.example/master.m3u8?format=ts')), false);
});

test('player selection classifies the proxied source using the requested media mode', () => {
    assert.match(spa, /let isTs = isTsLikeUrl\(uri, manifestType, channel\.mode \|\| currentChannel\.value\?\.mode\)/);
    assert.match(spa, /let isHls = playbackUtils\.isHlsLikeUrl\(uri, manifestType\)/);
    assert.match(spa, /let isDash = playbackUtils\.isDashLikeUrl\(uri, manifestType\)/);
});

test('generic VOD and series routing consumes the backend format classification', () => {
    assert.match(spa, /const mediaStrategy = String\(channel\?\.mediaInfo\?\.strategy \|\| ''\)/);
    assert.match(spa, /mediaStrategy === 'mpegts'/);
    assert.match(spa, /mediaStrategy === 'hls'/);
    assert.match(spa, /mediaStrategy === 'dash'/);
    assert.match(spa, /mediaStrategy === 'unsupported'/);
    assert.doesNotMatch(spa, /probeProxyMediaType/);
});
