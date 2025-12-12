package com.uiptv.shared;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
public class AudioInfo extends BaseJson {
    private String codecName, codecLongName, codecType, codecTagString, sampleRate, channels, bitRate, bitsPerSample, channelLayout;

    public AudioInfo(Map map) {
        if (map == null) return;
        this.codecName = safeGetString(map, "codec_name");
        this.codecLongName = safeGetString(map, "codec_long_name");
        this.codecType = safeGetString(map, "codec_type");
        this.codecTagString = safeGetString(map, "codec_tag_string");
        this.sampleRate = safeGetString(map, "sample_rate");
        this.channels = safeGetString(map, "channels");
        this.channelLayout = safeGetString(map, "channel_layout");
        this.bitsPerSample = safeGetString(map, "bits_per_sample");
        this.bitRate = safeGetString(map, "bit_rate");
    }
}
