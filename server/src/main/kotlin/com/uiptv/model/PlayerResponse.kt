package com.uiptv.model

class PlayerResponse(url: String) {
    var url: String? = url
    var drmType: String? = null
    var drmLicenseUrl: String? = null
    var clearKeysJson: String? = null
    var inputstreamaddon: String? = null
    var manifestType: String? = null
    var ffmpegMode: String? = null
    var account: Account? = null
    var channel: Channel? = null

    fun setFromChannel(channel: Channel?, account: Account?) {
        this.channel = channel
        if (channel == null) {
            clearDrmMetadata()
            return
        }
        this.account = account
        drmType = channel.drmType
        drmLicenseUrl = channel.drmLicenseUrl
        clearKeysJson = channel.clearKeysJson
        inputstreamaddon = channel.inputstreamaddon
        manifestType = channel.manifestType
    }

    fun setFromBookmark(bookmark: Bookmark?, account: Account?) {
        if (bookmark == null) {
            clearDrmMetadata()
            return
        }
        this.account = account
        drmType = bookmark.drmType
        drmLicenseUrl = bookmark.drmLicenseUrl
        clearKeysJson = bookmark.clearKeysJson
        inputstreamaddon = bookmark.inputstreamaddon
        manifestType = bookmark.manifestType
    }

    private fun clearDrmMetadata() {
        drmType = null
        drmLicenseUrl = null
        clearKeysJson = null
        inputstreamaddon = null
        manifestType = null
    }
}
