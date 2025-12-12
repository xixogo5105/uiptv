package com.uiptv.shared;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
public class VideoInfo extends BaseJson {
    String index, codecName, codecLongName, profile, codecType, codecTagString, codecTag, width, height,
            codedWidth, codedHeight, closedCaptions, filmGrain, hasBFrames,
            sampleAspectRatio, displayAspectRatio, pixFmt, avgFrameRate, rFrameRate,
            startTime, durationTs, duration, bitRate, bitsPerRawSample, nbFrames, extradataSize;


    public VideoInfo(Map map) {
        if (map == null) return;
        this.index = safeGetString(map, "index");
        this.codecName = safeGetString(map, "codec_name");
        this.codecLongName = safeGetString(map, "codec_long_name");
        this.profile = safeGetString(map, "profile");
        this.codecType = safeGetString(map, "codec_type");
        this.codecTagString = safeGetString(map, "codec_tag_string");
        this.codecTag = safeGetString(map, "codec_tag");
        this.width = safeGetString(map, "width");
        this.height = safeGetString(map, "height");
        this.codedWidth = safeGetString(map, "coded_width");
        this.codedHeight = safeGetString(map, "coded_height");
        this.closedCaptions = safeGetString(map, "closed_captions");
        this.filmGrain = safeGetString(map, "film_grain");
        this.hasBFrames = safeGetString(map, "has_b_frames");
        this.sampleAspectRatio = safeGetString(map, "sample_aspect_ratio");
        this.displayAspectRatio = safeGetString(map, "display_aspect_ratio");
        this.pixFmt = safeGetString(map, "pix_fmt");
        this.avgFrameRate = safeGetString(map, "avg_frame_rate");
        this.rFrameRate = safeGetString(map, "r_frame_rate");
        this.startTime = safeGetString(map, "start_time");
        this.durationTs = safeGetString(map, "duration_ts");
        this.duration = safeGetString(map, "duration");
        this.bitRate = safeGetString(map, "bit_rate");
        this.bitsPerRawSample = safeGetString(map, "bits_per_raw_sample");
        this.nbFrames = safeGetString(map, "nb_frames");
        this.extradataSize = safeGetString(map, "extradata_size");
    }
}
