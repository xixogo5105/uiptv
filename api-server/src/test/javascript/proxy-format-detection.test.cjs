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

test('MPEG-TS URL normalization preserves the selected playback URL', () => {
    const sourceUrl = 'https://provider.example/live/stream.ts?token=x';
    assert.equal(utils.resolveMpegTsPlaybackUrl(sourceUrl), sourceUrl);
    assert.equal(utils.resolveMpegTsPlaybackUrl(proxy(sourceUrl)), proxy(sourceUrl));
});

test('playback mode labels show the actual route and uppercase media format', () => {
    assert.equal(utils.resolvePlaybackModeLabel('https://provider.example/live.m3u8', 'videojs'), 'direct HLS');
    assert.equal(utils.resolvePlaybackModeLabel(proxy('https://provider.example/live.m3u8'), 'videojs'), 'proxy HLS');
    assert.equal(utils.resolvePlaybackModeLabel('https://provider.example/opaque', 'native', 'hls'), 'direct HLS');
    assert.equal(utils.resolvePlaybackModeLabel('https://provider.example/live.php?id=1', 'mpegts'), 'direct MPEG-TS');
    assert.equal(utils.resolvePlaybackModeLabel(proxy('https://provider.example/live/stream.ts'), 'mpegts'), 'proxy MPEG-TS');
    assert.equal(utils.resolvePlaybackModeLabel('https://provider.example/manifest.mpd', 'shaka'), 'direct DASH');
    assert.equal(utils.resolvePlaybackModeLabel(proxy('https://provider.example/movie.mkv'), 'mkv'), 'proxy MKV');
    assert.equal(utils.resolvePlaybackModeLabel('https://provider.example/play?src=%2Fproxy-stream%3Fsrc%3Dx', 'native'), 'direct');
});

test('global or account proxy setting independently selects proxy routing', () => {
    assert.equal(utils.shouldUseWebPlaybackProxy(true, false), true);
    assert.equal(utils.shouldUseWebPlaybackProxy(false, true), true);
    assert.equal(utils.shouldUseWebPlaybackProxy(false, false), false);
    assert.equal(utils.shouldUseWebPlaybackProxy('false', '1'), true);
    assert.match(spa, /const webPlaybackProxyEnabled = ref\(false\)/,
        'the UI should default to direct playback until the server setting is loaded');
    assert.match(spa, /const proxyUrl = buildProxyStreamUrl\(channel\.url, channel\.accountId\)/);
    assert.match(spa, /if \(proxyUrl\) channel = \{\.\.\.channel, url: proxyUrl\}/);
});

test('player selection classifies the proxied source using the requested media mode', () => {
    assert.match(spa, /let isTs = isTsLikeUrl\(uri, manifestType, channel\.mode \|\| currentChannel\.value\?\.mode\)/);
    assert.match(spa, /let isHls = playbackUtils\.isHlsLikeUrl\(uri, manifestType\)/);
    assert.match(spa, /let isDash = playbackUtils\.isDashLikeUrl\(uri, manifestType\)/);
});

test('generic VOD and series routing consumes the backend format classification', () => {
    assert.match(spa, /let mediaStrategy = String\(channel\?\.mediaInfo\?\.strategy \|\| ''\)/);
    assert.match(spa, /mediaStrategy === 'mpegts'/);
    assert.match(spa, /mediaStrategy === 'hls'/);
    assert.match(spa, /mediaStrategy === 'dash'/);
    assert.match(spa, /mediaStrategy === 'unsupported'/);
    assert.doesNotMatch(spa, /probeProxyMediaType/);
});

function probeHarness(response) {
    let request, cancelled = false;
    const scope = {window:{location:{origin:'http://fixture'}},URL,AbortController,TextDecoder,Uint8Array,setTimeout,clearTimeout,
        fetch:async(url,options)=>{request={url,options};return response;}};
    vm.createContext(scope);vm.runInContext(source,scope);
    return {probe:scope.window.UIPTVPlaybackUtils.probeStreamFormat,request:()=>request};
}

test('an extensionless redirect is recognized as HLS from response headers and its body is cancelled',async()=>{
    let cancelled=false;
    const h=probeHarness({ok:true,headers:{get:()=> 'application/vnd.apple.mpegurl'},body:{cancel:async()=>{cancelled=true;}}});
    assert.equal(await h.probe(proxy('https://jmp2.uk/stvp-IN2100004C4')),'hls');
    assert.equal(h.request().options.headers.Range,'bytes=0-2047');
    assert.equal(h.request().options.signal.aborted,true);
    assert.equal(cancelled,true);
});

test('generic content types are detected from split playlist bytes',async()=>{
    let cancelled=false;
    const chunks=[new TextEncoder().encode('#EXT'),new TextEncoder().encode('M3U\n#EXT-X-VERSION:3\n')];
    const h=probeHarness({ok:true,headers:{get:()=> 'application/octet-stream'},body:{getReader:()=>({read:async()=>({done:!chunks.length,value:chunks.shift()}),cancel:async()=>{cancelled=true;}})}});
    assert.equal(await h.probe('http://fixture/opaque'),'hls');
    assert.equal(cancelled,true);
});

test('format probes stop at 2 KB even when a provider ignores Range',async()=>{
    let reads=0,cancelled=false;
    const h=probeHarness({ok:true,headers:{get:()=> ''},body:{getReader:()=>({read:async()=>{reads++;return {done:false,value:new Uint8Array(8192)};},cancel:async()=>{cancelled=true;}})}});
    assert.equal(await h.probe('http://fixture/opaque'),'');
    assert.equal(reads,1);assert.equal(cancelled,true);
});

test('HTTP errors and cancelled playback do not select a guessed format',async()=>{
    const h=probeHarness({ok:false,headers:{get:()=> 'application/vnd.apple.mpegurl'},body:{cancel:async()=>{}}});
    assert.equal(await h.probe('http://fixture/opaque'),'');
    const controller=new AbortController();controller.abort();
    assert.equal(await h.probe('http://fixture/opaque',controller.signal),'');
});

test('opaque HLS routes pass detected manifest metadata to the adaptive player',()=>{
    const start=spa.indexOf('// Short provider links');
    const end=spa.indexOf('// Follow the server',start);
    const routing=spa.slice(start,end);
    assert.match(routing,/override === 'auto'/);
    assert.match(routing,/!hasDRM/);
    assert.match(routing,/!isTs && !isHls && !isDash/);
    assert.match(routing,/await playbackUtils\.probeStreamFormat/);
    assert.match(routing,/throwIfPlaybackSuperseded\(requestId\)/);
    assert.match(routing,/manifestType: isHls \? 'm3u8'/);
    assert.match(routing,/const probeUrl = uri/);
    assert.match(routing,/detectedMediaStrategy: detected, url: uri/);
});

test('backend LAN proxy URLs are rebased to the page origin without nesting or changing the source',()=>{
    const upstream='http://edge_test.example/path/mono.m3u8?token=a%2Bb&stream=7';
    const published='http://192.168.0.168:8080/proxy-stream?src='+encodeURIComponent(upstream);
    const expected=proxy(upstream);
    assert.equal(utils.normalizeWebPlaybackUrl(published),expected);
    assert.equal(utils.buildProxyStreamUrl(published),expected);
    assert.equal(utils.buildProxyStreamUrl(expected),expected);
    assert.equal(new URL(expected).searchParams.get('src'),upstream);
});

test('proxy rebasing follows HTTPS and remote browser origins, retaining provider URL encoding',()=>{
    const scope={window:{location:{origin:'https://player.example'}},URL};
    vm.createContext(scope);vm.runInContext(source,scope);
    const url='http://192.168.0.168:8080/proxy-stream?src=https%3A%2F%2Fcdn.example%2Fmono.m3u8%3Ftoken%3Da%252Bb';
    assert.equal(scope.window.UIPTVPlaybackUtils.normalizeWebPlaybackUrl(url),url.replace('http://192.168.0.168:8080','https://player.example'));
    assert.equal(scope.window.UIPTVPlaybackUtils.normalizeWebPlaybackUrl('https://cdn.example/live.m3u8'),'https://cdn.example/live.m3u8');
});
