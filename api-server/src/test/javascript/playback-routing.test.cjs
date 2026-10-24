const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');

test('NATIVE_PROXY strategy routes backend-identified formats to the matching player', () => {
    const hint = source.indexOf("strategyHint === 'NATIVE_PROXY'");
    const override = source.indexOf("if (override === 'proxy')", hint);
    const branch = source.slice(hint, override);
    assert.ok(hint >= 0 && override > hint, 'SPA must recognize the server proxy strategy hint');
    assert.match(branch, /override === 'auto'/,
        'explicit direct, proxy and MPEG-TS experiments must bypass automatic proxy routing');
    assert.match(branch, /playbackUtils\.buildProxyStreamUrl\(uri\) \|\| uri/,
        'an explicit server proxy route must use the local web proxy independently of account settings');
    assert.match(branch, /mediaStrategy === 'hls'/);
    assert.match(branch, /mediaStrategy === 'mpegts'/);
    assert.match(branch, /mediaStrategy === 'dash'/);
    assert.match(branch, /mediaStrategy === 'unsupported'/);
    assert.match(branch, /await loadVideoJs\(proxiedChannel, requestId\)/);
    assert.match(branch, /await loadMpegTs\(/);
    assert.doesNotMatch(branch, /probeProxyMediaType/);
    assert.match(source, /Promise\.race\(\[playWithGestureFallback\(playAction\), mpegTsStartupError\]\)/,
        'MPEG-TS engine errors must reject startup with the engine diagnostic instead of timing out');
    assert.match(source, /url: resolveMpegTsPlaybackUrl\(uri\)/,
        'the MPEG-TS route test must use the same local proxy transport as production');
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
    const fetchStart = source.indexOf("const channelData = await withPlaybackStartupTimeout((async () => {");
    const fetchFailure = source.indexOf("throw new Error(`Playback request failed (${response.status})`);", fetchStart);
    assert.ok(fetchStart >= 0, 'SPA must bound player resolution with the startup timeout');
    assert.ok(fetchFailure > fetchStart, 'SPA must reject non-success player responses before parsing media data');
    assert.match(source.slice(fetchStart, source.indexOf('if (!isCurrentPlaybackRequest(requestId)) return;', fetchStart)), /'Player resolution', \(\) => controller\.abort\(\)/);
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
    const css = fs.readFileSync('api-server/src/main/resources/web/css/spa.css', 'utf8');
    const sw = fs.readFileSync('api-server/src/main/resources/web/sw.js', 'utf8');

    assert.match(on, /viewBox="0 0 16 16"/);
    assert.match(off, /viewBox="0 0 16 16"/);
    assert.match(on, /circle cx="8" cy="8" r="4" fill="#e53935"/);
    assert.match(off, /circle cx="8" cy="8" r="4" fill="#8a97a5"/);
    assert.match(source, /broadcast-on\.svg\?v=20261017b/);
    assert.match(source, /broadcast-off\.svg\?v=20261017b/);
    assert.match(html, /spa\.js\?v=20261024topPlayerHeight3/);
    assert.match(html, /spa\.css\?v=20261024topPlayerHeight4/);
    assert.match(html, /class="broadcast-indicator"/);
    assert.match(css, /\.broadcast-indicator\s*\{[\s\S]*background: var\(--broadcast-indicator-bg\)/);
    assert.match(css, /--broadcast-indicator-bg: #e5e5e5/);
    assert.match(sw, /CACHE_NAME = 'uiptv-cache-v114'/);
});

