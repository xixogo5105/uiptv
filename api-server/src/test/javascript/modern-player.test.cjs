const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('api-server/src/main/resources/web/javascript/spa.js', 'utf8');
const functionSource = (start, end) => source.slice(source.indexOf(start), source.indexOf(end, source.indexOf(start)));
function harness() {
    const calls = [];
    const ref = value => ({value});
    const store = {videoRenditionList: [{id:'low',height:360,bitrate:100000,selected:false},{id:'high',height:1080,bitrate:500000,selected:false}], activeVideoRendition:{id:'high'},
        audioTrackList:[{id:'en',language:'en',enabled:true}], textTrackList:[{id:'sub',kind:'subtitles',language:'en',mode:'showing'},{id:'chap',kind:'chapters'}],
        selectVideoRendition(id) { calls.push(['quality',id]); }, selectAudioTrack(id) { calls.push(['audio',id]); }, selectSubtitlesTrack(id) { calls.push(['text',id]); }};
    const media = {addEventListener(){}, play: async()=>calls.push(['play'])};
    const player = {store,dispose(){calls.push(['dispose']);}};
    const context = {window:{UIPTVModernPlayer:{ready:async()=>{}, mount(container,tag){calls.push(['mount',tag]);return media;},connect(){return player;}}},
        document:{getElementById:()=>({})}, nextTick:async()=>{}, videoJsPlayer:ref(null),videoPlayer:ref(null),playbackError:ref(''),playbackMode:ref(''),
        playbackRequestState:{currentId:()=>0},
        videoTracks:ref([]),audioTracks:ref([]),textTracks:ref([]),selectedTextTrackId:ref('off'),
        bindPlaybackEvents(){},normalizeWebPlaybackUrl:s=>s,playWithGestureFallback:fn=>fn(),resolvePlaybackModeLabel:()=> 'Video.js',ensureVideoElement(){},
        throwIfPlaybackSuperseded(){},withPlaybackStartupTimeout:promise=>promise,withPlaybackMediaError:(video, playAction)=>playAction(),
        isMaxQualityEnabled:()=>false, setMaxQualityEnabled(value){calls.push(['max',value]);},console};
    vm.createContext(context);
    vm.runInContext(functionSource('        const loadVideoJs =','        const loadShaka =')+'\nthis.loadVideoJs=loadVideoJs;this.refreshVideoJsTracks=refreshVideoJsTracks;',context);
    for(const [name,end] of [['switchVideoTrack','switchVideoAuto'],['switchVideoAuto','switchVideoMax'],['switchVideoMax','switchAudioTrack'],['switchAudioTrack','switchTextTrack'],['switchTextTrack','refreshNativeTracks']]) {
        const start=source.indexOf('        const '+name+' =');
        // Exercise the modern store branch, independent of other engines.
        const branchEnd=source.indexOf('                return;\n            }',start)+'                return;\n            }'.length;
        vm.runInContext(source.slice(start,branchEnd)+'\n};\nthis.'+name+'='+name+';',context);
    }
    return {context,calls,store,player,media};
}
test('Video.js 10 chooses HLS, DASH and progressive media adapters',async()=>{
    const {context,calls}=harness();
    for(const [url,tag] of [['https://example.test/live.m3u8?token=x','hlsjs-video'],['https://example.test/movie.mpd','dash-video'],['https://example.test/movie.mp4','video']]) {
        await context.loadVideoJs({url}); assert.ok(calls.some(call=>call[0]==='mount'&&call[1]===tag));
    }
    assert.equal(calls.filter(call=>call[0]==='dispose').length,2);
});
test('quality, audio and caption controls use v10 store actions and IDs',async()=>{
    const {context,calls}=harness(); await context.loadVideoJs({url:'stream.m3u8'});
    assert.equal(context.videoTracks.value[1].active,true);
    assert.equal(context.audioTracks.value[0].active,true);
    assert.equal(context.textTracks.value.length,1);
    context.switchVideoTrack('low'); context.switchVideoAuto(); context.switchVideoMax(); context.switchAudioTrack('en'); await context.switchTextTrack('off');
    for(const expected of [['quality','low'],['quality','auto'],['quality','high'],['audio','en'],['text',null]]) assert.ok(calls.some(call=>JSON.stringify(call)===JSON.stringify(expected)));
});
test('late track events from disposed players cannot replace current track menus',async()=>{
    const {context}=harness(); await context.loadVideoJs({url:'stream.m3u8'});
    context.refreshVideoJsTracks({store:{videoRenditionList:[]}});
    assert.equal(context.videoTracks.value.length,2);
});

test('time-only store notifications retain the existing menu arrays',async()=>{
    const {context,player}=harness(); await context.loadVideoJs({url:'stream.m3u8'});
    const video=context.videoTracks.value, audio=context.audioTracks.value, text=context.textTracks.value;
    context.refreshVideoJsTracks(player);
    assert.equal(context.videoTracks.value,video); assert.equal(context.audioTracks.value,audio); assert.equal(context.textTracks.value,text);
});

test('startup failure disposes the failed adapter before compatibility fallback',async()=>{
    const {context,media,calls}=harness(); media.play=async()=>{throw new Error('Unsupported media');};
    await assert.rejects(context.loadVideoJs({url:'stream.m3u8'}),/Unsupported media/);
    assert.equal(context.videoJsPlayer.value,null);
    assert.ok(calls.some(call=>call[0]==='dispose'));
});

test('a saved maximum quality preference selects the highest rendition once',async()=>{
    const {context,calls,player}=harness(); context.isMaxQualityEnabled=()=>true;
    await context.loadVideoJs({url:'stream.m3u8'}); context.refreshVideoJsTracks(player);
    assert.equal(calls.filter(call=>call[0]==='quality'&&call[1]==='high').length,1);
});
