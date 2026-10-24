(function () {
    // Both packages must use the same Mediabunny instance (see the import map).
    const AC3_URL = 'https://cdn.jsdelivr.net/npm/@mediabunny/ac3@1.62.1/dist/bundles/mediabunny-ac3.min.mjs';
    let library, ac3;
    const loadLibrary = () => library || (library = import('mediabunny').catch(error => { library = null; throw error; }));
    const loadAc3 = () => ac3 || (ac3 = import(AC3_URL).then(module => module.registerAc3Decoder()).catch(error => { ac3 = null; throw error; }));
    const aborted = () => new DOMException('Playback superseded', 'AbortError');
    const covers = (ranges, time) => {
        for (let i = 0; i < ranges.length; i++) {
            if (ranges.start(i) <= time && time + 0.1 < ranges.end(i)) return true;
        }
        return false;
    };

    class MkvPlayer {
        constructor(video, url, {label, onError, load = loadLibrary, loadAudioDecoder = loadAc3} = {}) {
            this.video = video;
            this.url = url;
            this.label = label;
            this.onError = onError;
            this.load = load;
            this.loadAudioDecoder = loadAudioDecoder;
            this.controller = new AbortController();
            this.lanes = [];
            this.outputs = new Set();
            this.generation = 0;
            this.pumping = Promise.resolve();
            this.seekListener = () => {
                if (Number.isFinite(this.duration) && video.currentTime >= this.duration - 0.1) return;
                if (!this.lanes.every(lane => covers(lane.buffer.buffered, video.currentTime))) {
                    this.restart(video.currentTime);
                }
            };
        }

        check(generation = this.generation) {
            if (this.controller.signal.aborted || generation !== this.generation) throw aborted();
        }

        async start() {
            try {
                if (!window.MediaSource) throw new Error(this.label('webMkvUnavailable'));
                this.lib = await this.load();
                this.check();
                const {Input, UrlSource, MATROSKA} = this.lib;
                this.input = new Input({formats: [MATROSKA], source: new UrlSource(this.url, {
                    requestInit: {signal: this.controller.signal},
                    maxCacheSize: 8 * 1024 * 1024,
                    parallelism: 2,
                    getRetryDelay: () => null
                })});
                const tracks = await Promise.all([this.input.getPrimaryVideoTrack(), this.input.getPrimaryAudioTrack()]);
                this.check();
                for (const track of tracks.filter(Boolean)) {
                    const codec = await track.getCodec();
                    const parameter = await track.getCodecParameterString();
                    const mime = `${track.type}/mp4; codecs="${parameter}"`;
                    let transcode = false, outputCodec = codec, outputMime = mime;
                    if (!parameter || !window.MediaSource.isTypeSupported(mime)) {
                        if (track.type !== 'audio' || !['ac3', 'eac3'].includes(codec)) {
                            throw new Error(this.label('webMkvUnsupportedCodec').replace('{codec}', parameter || codec || track.type));
                        }
                        await this.loadAudioDecoder();
                        this.check();
                        outputCodec = null;
                        // Prefer AAC when the host has an encoder; Opus is the browser fallback.
                        for (const candidate of ['aac', 'opus']) {
                            const candidateMime = `audio/mp4; codecs="${candidate === 'aac' ? 'mp4a.40.2' : 'opus'}"`;
                            if (window.MediaSource.isTypeSupported(candidateMime)
                                && await this.lib.canEncodeAudio(candidate, {numberOfChannels: 2, sampleRate: 48000})) {
                                outputCodec = candidate;
                                outputMime = candidateMime;
                                break;
                            }
                        }
                        if (!outputCodec || !await track.canDecode()) {
                            throw new Error(this.label('webMkvUnsupportedCodec').replace('{codec}', parameter || codec));
                        }
                        transcode = true;
                    }
                    this.check();
                    this.lanes.push({track, codec: outputCodec, mime: outputMime, transcode});
                }
                if (!this.lanes.length) throw new Error(this.label('webMkvPlaybackFailed'));
                this.duration = await this.input.getDurationFromMetadata(tracks.filter(Boolean));
                this.check();
                this.media = new window.MediaSource();
                this.objectUrl = URL.createObjectURL(this.media);
                const opened = new Promise((resolve, reject) => {
                    const cleanup = () => {
                        this.media.removeEventListener('sourceopen', open);
                        this.controller.signal.removeEventListener('abort', cancel);
                    };
                    const open = () => { cleanup(); resolve(); };
                    const cancel = () => { cleanup(); reject(aborted()); };
                    this.media.addEventListener('sourceopen', open, {once: true});
                    this.controller.signal.addEventListener('abort', cancel, {once: true});
                });
                this.video.src = this.objectUrl;
                await opened;
                this.check();
                for (const lane of this.lanes) lane.buffer = this.media.addSourceBuffer(lane.mime);
                if (Number.isFinite(this.duration) && this.duration > 0) this.media.duration = this.duration;
                this.video.addEventListener('seeking', this.seekListener);
                this.restart(this.video.currentTime || 0);
            } catch (error) {
                this.destroy();
                throw error;
            }
        }

        async updateBuffer(buffer, operation, generation) {
            this.check(generation);
            await new Promise((resolve, reject) => {
                const cleanup = () => {
                    buffer.removeEventListener('updateend', done);
                    buffer.removeEventListener('error', fail);
                    this.controller.signal.removeEventListener('abort', cancel);
                };
                const done = () => { cleanup(); resolve(); };
                const fail = () => { cleanup(); reject(new Error(this.label('webMkvPlaybackFailed'))); };
                const cancel = () => { cleanup(); reject(aborted()); };
                buffer.addEventListener('updateend', done, {once: true});
                buffer.addEventListener('error', fail, {once: true});
                this.controller.signal.addEventListener('abort', cancel, {once: true});
                try { operation(); } catch (error) { cleanup(); reject(error); }
            });
            this.check(generation);
        }

        async waitForPlayhead(timestamp, generation) {
            // Backpressure bounds remuxing/decoding even while paused. Seeking changes
            // generation, so an old producer cannot append data to the new position.
            while (timestamp > this.video.currentTime + 30) {
                this.check(generation);
                await new Promise(resolve => {
                    const done = () => { clearTimeout(timer); this.controller.signal.removeEventListener('abort', done); resolve(); };
                    const timer = setTimeout(done, 100);
                    this.controller.signal.addEventListener('abort', done, {once: true});
                });
            }
            this.check(generation);
        }

        async pump(lane, time, generation) {
            const {Output, Mp4OutputFormat, AppendOnlyStreamTarget, EncodedPacketSink,
                EncodedVideoPacketSource, EncodedAudioPacketSource, AudioSampleSink, AudioSampleSource} = this.lib;
            const source = lane.transcode
                ? new AudioSampleSource({codec: lane.codec, bitrate: 128000, transform: {numberOfChannels: 2, sampleRate: 48000}})
                : lane.track.type === 'video' ? new EncodedVideoPacketSource(lane.codec) : new EncodedAudioPacketSource(lane.codec);
            const output = new Output({
                format: new Mp4OutputFormat({fastStart: 'fragmented', minimumFragmentDuration: 1}),
                target: new AppendOnlyStreamTarget(new WritableStream({write: async data => {
                    this.check(generation);
                    const behind = this.video.currentTime - 20;
                    if (behind > 0 && lane.buffer.buffered.length && lane.buffer.buffered.start(0) < behind) {
                        await this.updateBuffer(lane.buffer, () => lane.buffer.remove(0, behind), generation);
                    }
                    await this.updateBuffer(lane.buffer, () => lane.buffer.appendBuffer(data), generation);
                }}))
            });
            this.outputs.add(output);
            try {
                if (lane.track.type === 'video') output.addVideoTrack(source, {rotation: await lane.track.getRotation()});
                else output.addAudioTrack(source);
                await output.start();
                if (lane.transcode) {
                    for await (const sample of new AudioSampleSink(lane.track).samples(time)) {
                        try {
                            await this.waitForPlayhead(sample.timestamp, generation);
                            await source.add(sample);
                        } finally { sample.close(); }
                    }
                } else {
                    const sink = new EncodedPacketSink(lane.track);
                    const first = await sink.getKeyPacket(time) || await sink.getFirstKeyPacket();
                    if (first) {
                        const decoderConfig = await lane.track.getDecoderConfig();
                        for await (const packet of sink.packets(first)) {
                            await this.waitForPlayhead(packet.timestamp, generation);
                            await source.add(packet, {decoderConfig});
                        }
                    }
                }
                this.check(generation);
                source.close();
                await output.finalize();
            } finally {
                this.outputs.delete(output);
                if (output.state !== 'finalized') await output.cancel();
            }
        }

        restart(time) {
            const generation = ++this.generation;
            const previous = this.pumping;
            this.pumping = (async () => {
                await previous;
                this.check(generation);
                for (const lane of this.lanes) {
                    if (lane.buffer.buffered.length) {
                        await this.updateBuffer(lane.buffer, () => lane.buffer.remove(0, Infinity), generation);
                    }
                }
                // Each track has its own fMP4 stream, preserving original timestamps.
                // Video begins at the preceding keyframe; audio retains its own clock.
                await Promise.all(this.lanes.map(lane => this.pump(lane, time, generation)));
                this.check(generation);
                if (this.media.readyState === 'open') this.media.endOfStream();
            })().catch(error => {
                if (error.name !== 'AbortError' && !this.controller.signal.aborted && generation === this.generation) {
                    this.destroy();
                    this.onError?.(error);
                }
            });
        }

        destroy() {
            if (this.controller.signal.aborted) return;
            this.controller.abort();
            this.generation++;
            this.video.removeEventListener('seeking', this.seekListener);
            this.input?.dispose();
            for (const output of this.outputs) output.cancel().catch(() => {});
            if (this.objectUrl) URL.revokeObjectURL(this.objectUrl);
        }
    }
    window.UIPTVMkvPlayer = {MkvPlayer};
})();
