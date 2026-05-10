package com.uiptv.shared

import com.uiptv.util.StringUtils.safeGetString

data class AudioInfo @JvmOverloads constructor(
    var codecName: String? = null,
    var codecLongName: String? = null,
    var codecType: String? = null,
    var codecTagString: String? = null,
    var sampleRate: String? = null,
    var channels: String? = null,
    var bitRate: String? = null,
    var bitsPerSample: String? = null,
    var channelLayout: String? = null
) : BaseJson() {
    constructor(map: Map<*, *>?) : this() {
        if (map == null) {
            return
        }
        codecName = safeGetString(map, "codec_name")
        codecLongName = safeGetString(map, "codec_long_name")
        codecType = safeGetString(map, "codec_type")
        codecTagString = safeGetString(map, "codec_tag_string")
        sampleRate = safeGetString(map, "sample_rate")
        channels = safeGetString(map, "channels")
        channelLayout = safeGetString(map, "channel_layout")
        bitsPerSample = safeGetString(map, "bits_per_sample")
        bitRate = safeGetString(map, "bit_rate")
    }
}
