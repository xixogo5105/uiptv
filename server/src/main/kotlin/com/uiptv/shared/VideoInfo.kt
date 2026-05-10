package com.uiptv.shared

import com.uiptv.util.StringUtils.safeGetString

data class VideoInfo @JvmOverloads constructor(
    var index: String? = null,
    var codecName: String? = null,
    var codecLongName: String? = null,
    var profile: String? = null,
    var codecType: String? = null,
    var codecTagString: String? = null,
    var codecTag: String? = null,
    var width: String? = null,
    var height: String? = null,
    var codedWidth: String? = null,
    var codedHeight: String? = null,
    var closedCaptions: String? = null,
    var filmGrain: String? = null,
    var hasBFrames: String? = null,
    var sampleAspectRatio: String? = null,
    var displayAspectRatio: String? = null,
    var pixFmt: String? = null,
    var avgFrameRate: String? = null,
    var rFrameRate: String? = null,
    var startTime: String? = null,
    var durationTs: String? = null,
    var duration: String? = null,
    var bitRate: String? = null,
    var bitsPerRawSample: String? = null,
    var nbFrames: String? = null,
    var extradataSize: String? = null
) : BaseJson() {
    constructor(map: Map<*, *>?) : this() {
        if (map == null) {
            return
        }
        index = safeGetString(map, "index")
        codecName = safeGetString(map, "codec_name")
        codecLongName = safeGetString(map, "codec_long_name")
        profile = safeGetString(map, "profile")
        codecType = safeGetString(map, "codec_type")
        codecTagString = safeGetString(map, "codec_tag_string")
        codecTag = safeGetString(map, "codec_tag")
        width = safeGetString(map, "width")
        height = safeGetString(map, "height")
        codedWidth = safeGetString(map, "coded_width")
        codedHeight = safeGetString(map, "coded_height")
        closedCaptions = safeGetString(map, "closed_captions")
        filmGrain = safeGetString(map, "film_grain")
        hasBFrames = safeGetString(map, "has_b_frames")
        sampleAspectRatio = safeGetString(map, "sample_aspect_ratio")
        displayAspectRatio = safeGetString(map, "display_aspect_ratio")
        pixFmt = safeGetString(map, "pix_fmt")
        avgFrameRate = safeGetString(map, "avg_frame_rate")
        rFrameRate = safeGetString(map, "r_frame_rate")
        startTime = safeGetString(map, "start_time")
        durationTs = safeGetString(map, "duration_ts")
        duration = safeGetString(map, "duration")
        bitRate = safeGetString(map, "bit_rate")
        bitsPerRawSample = safeGetString(map, "bits_per_raw_sample")
        nbFrames = safeGetString(map, "nb_frames")
        extradataSize = safeGetString(map, "extradata_size")
    }
}
