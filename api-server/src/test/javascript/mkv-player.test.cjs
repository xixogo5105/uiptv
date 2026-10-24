const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const playerSource = fs.readFileSync('api-server/src/main/resources/web/javascript/mkv-player.js', 'utf8');
const labels = JSON.parse(fs.readFileSync('api-server/src/main/resources/web/epg-labels.json', 'utf8'));
const tick = () => new Promise(resolve => setImmediate(resolve));
function harness({audio = 'aac', video = 'avc', failAppend = false, delayedLoad = false, end = null} = {}) {
    let disposed = false, cancelled = 0, videoEncoded = 0, audioEncoded = 0, closedSamples = 0, audioDecoderLoads = 0;
    const writes = [], packets = [], inputs = [], errors = [];
    const videoElement = Object.assign(new EventTarget(), {currentTime:0});
    const track = (type, codec) => ({type, getCodec: async()=>codec, getCodecParameterString:async()=>codec,
        getDecoderConfig:async()=>({codec}), getRotation:async()=>0, canDecode:async()=>true});
    class Buffer extends EventTarget {
        buffered = {length:0, start:()=>0, end:()=>40};
        appendBuffer(data) { writes.push(data); queueMicrotask(()=>this.dispatchEvent(new Event(failAppend ? 'error' : 'updateend'))); }
        remove() { this.buffered.length = 0; queueMicrotask(()=>this.dispatchEvent(new Event('updateend'))); }
    }
    class Media extends EventTarget {
        static isTypeSupported(type) { return !type.includes('ac3') && !type.includes('unknown'); }
        readyState = 'open';
        addSourceBuffer() { return new Buffer(); }
        endOfStream() { this.readyState='ended'; }
    }
    const url = {createObjectURL(media) { queueMicrotask(()=>media.dispatchEvent(new Event('sourceopen'))); return 'blob:fixture'; }, revokeObjectURL() {}};
    const inputVideo=track('video',video), inputAudio=track('audio',audio);
    class Input {
        constructor(options) { inputs.push(options); }
        getPrimaryVideoTrack=async()=>inputVideo;
        getPrimaryAudioTrack=async()=>inputAudio;
        getDurationFromMetadata=async()=>100;
        dispose() { disposed=true; }
    }
    class PacketSink {
        constructor(track) { this.track=track; }
        getKeyPacket=async time=>({timestamp:time,type:'key'});
        getFirstKeyPacket=async()=>({timestamp:0,type:'key'});
        async *packets(first) { for (let t=first.timestamp;t<(end ?? first.timestamp+3);t++) { packets.push(t); yield {timestamp:t,type:'key'}; } }
    }
    class SampleSink {
        async *samples(time) { for(let t=time;t<time+3;t++) yield {timestamp:t,close(){closedSamples++;}}; }
    }
    class Target { constructor(stream) { this.writer=stream.getWriter(); } }
    class Output {
        state='pending';
        constructor({target}) { this.target=target; }
        addVideoTrack(source) { source.writer=this.target.writer; }
        addAudioTrack(source) { source.writer=this.target.writer; }
        async start() { this.state='started'; }
        async finalize() { this.state='finalized'; }
        async cancel() { this.state='canceled'; cancelled++; }
    }
    class VideoSource { async add(packet) { videoEncoded++; await this.writer.write(new Uint8Array([packet.timestamp])); } close(){} }
    class AudioSource extends VideoSource { async add(packet) { audioEncoded++; await this.writer.write(new Uint8Array([packet.timestamp])); } }
    const lib={Input,UrlSource:class {constructor(url,options){this.url=url;this.options=options;}},MATROSKA:{},Output,
        Mp4OutputFormat:class {},AppendOnlyStreamTarget:Target,EncodedPacketSink:PacketSink,
        EncodedVideoPacketSource:VideoSource, EncodedAudioPacketSource:AudioSource,
        AudioSampleSink:SampleSink,AudioSampleSource:AudioSource,canEncodeAudio:async()=>true};
    const context={window:{MediaSource:Media}, URL:url, AbortController, DOMException, WritableStream, setTimeout,clearTimeout};
    vm.createContext(context); vm.runInContext(playerSource,context);
    let release;
    const load = delayedLoad ? ()=>new Promise(resolve=>{release=()=>resolve(lib);}) : async()=>lib;
    const player=new context.window.UIPTVMkvPlayer.MkvPlayer(videoElement,'http://fixture/proxy-stream?src=episode',{
        label:key=>labels.en[key],onError:error=>errors.push(error.message),load,
        loadAudioDecoder:async()=>{audioDecoderLoads++;}
    });
    return {player,videoElement,errors,inputs,writes,packets,release:()=>release(),
        stats:()=>({disposed,cancelled,videoEncoded,audioEncoded,closedSamples,audioDecoderLoads})};
}

test('compatible video and audio are copied through incremental fMP4 outputs', async()=>{
    const h=harness(); await h.player.start(); await h.player.pumping;
    assert.equal(h.player.lanes.length,2);
    assert.ok(h.player.lanes.every(lane=>!lane.transcode));
    assert.equal(h.writes.length,6);
    assert.equal(h.stats().audioDecoderLoads,0);
    assert.equal(h.inputs[0].source.options.maxCacheSize,8*1024*1024);
    h.player.destroy(); assert.equal(h.stats().disposed,true);
});

test('AC-3 audio is converted locally while video packets are copied unchanged', async()=>{
    const h=harness({audio:'ac3',video:'hevc'}); await h.player.start(); await h.player.pumping;
    assert.equal(h.player.lanes[0].transcode,false);
    assert.equal(h.player.lanes[1].transcode,true);
    assert.equal(h.stats().videoEncoded,3);
    assert.equal(h.stats().closedSamples,3);
    assert.equal(h.stats().audioDecoderLoads,1);
    h.player.destroy();
});

test('unsupported video is rejected before media attachment or silent video transcoding', async()=>{
    const h=harness({video:'unknown'});
    await assert.rejects(h.player.start(),/unknown/);
    assert.equal(h.videoElement.src,undefined);
    assert.equal(h.stats().disposed,true);
});

test('switching streams during library loading prevents late media attachment', async()=>{
    const h=harness({delayedLoad:true}); const start=h.player.start();
    h.player.destroy(); h.release();
    await assert.rejects(start,{name:'AbortError'});
    assert.equal(h.videoElement.src,undefined);
    assert.equal(h.inputs.length,0);
});

test('source buffer errors stop producers and report playback failure', async()=>{
    const h=harness({failAppend:true}); await h.player.start(); await h.player.pumping;
    assert.deepEqual(h.errors,[labels.en.webMkvPlaybackFailed]);
    assert.equal(h.stats().disposed,true);
    assert.ok(h.stats().cancelled>0);
});

test('unbuffered seeking restarts at the requested position and stop removes seeking listeners', async()=>{
    const h=harness(); await h.player.start(); await h.player.pumping;
    h.videoElement.currentTime=10; h.videoElement.dispatchEvent(new Event('seeking'));
    await h.player.pumping;
    assert.ok(h.packets.includes(10));
    const generation=h.player.generation; h.player.destroy();
    h.videoElement.dispatchEvent(new Event('seeking')); await tick();
    assert.equal(h.player.generation,generation+1);
});

test('paused playback backpressure is interrupted by cancellation', async()=>{
    const h=harness(); await h.player.start(); await h.player.pumping;
    const waiting=h.player.waitForPlayhead(100,h.player.generation);
    h.player.destroy(); await assert.rejects(waiting,{name:'AbortError'});
});

test('a seek interrupts paused producers without appending stale packets', async()=>{
    const h=harness({end:100}); await h.player.start(); await tick();
    assert.ok(h.packets.length <= 64, 'paused playback must stop reading beyond the buffer horizon');
    h.videoElement.currentTime=80; h.videoElement.dispatchEvent(new Event('seeking'));
    const atSeek=h.writes.length;
    await h.player.pumping;
    assert.ok(h.writes.slice(atSeek).every(data=>data[0]>=80));
    assert.deepEqual(h.errors,[]);
    h.player.destroy();
});

test('seeking to the duration does not restart an empty output', async()=>{
    const h=harness(); await h.player.start(); await h.player.pumping;
    const generation=h.player.generation; h.videoElement.currentTime=100;
    h.videoElement.dispatchEvent(new Event('seeking'));
    assert.equal(h.player.generation,generation);
    h.player.destroy();
});

test('MKV errors have real translations in every shipped locale and core bundle', ()=>{
    for(const [locale,bundle] of Object.entries(labels)) {
        for(const key of ['webMkvUnsupportedCodec','webMkvPlaybackFailed','webMkvUnavailable']) {
            assert.ok(bundle[key],`${locale}: ${key}`);
            if(!locale.startsWith('en')) assert.notEqual(bundle[key],labels.en[key],`${locale}: ${key}`);
        }
    }
    for(const name of fs.readdirSync('core/src/main/resources/i18n').filter(name=>/^messages.*\.properties$/.test(name))) {
        const text=fs.readFileSync(`core/src/main/resources/i18n/${name}`,'utf8');
        for(const key of ['webMkvUnsupportedCodec','webMkvPlaybackFailed','webMkvUnavailable']) assert.match(text,new RegExp(`^${key}=.+$`,'m'));
    }
});

test('MKV URL detection handles proxy wrappers and portal stream names without matching embedded commands',()=>{
    const context={window:{location:{origin:'http://fixture'}},URL};
    vm.createContext(context);vm.runInContext(fs.readFileSync('api-server/src/main/resources/web/javascript/playback-utils.js','utf8'),context);
    const {isMkvLikeUrl}=context.window.UIPTVPlaybackUtils;
    assert.equal(isMkvLikeUrl('http://provider/title.mkv?token=abc'),true);
    assert.equal(isMkvLikeUrl('http://fixture/proxy-stream?src='+encodeURIComponent('http://provider/play/movie.php?stream=title.mkv&type=series')),true);
    assert.equal(isMkvLikeUrl('http://provider/play/movie.php?cmd='+encodeURIComponent('http://cdn/title.mkv')),false);
    assert.equal(isMkvLikeUrl('http://provider/title.mp4'),false);
});
