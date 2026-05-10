package com.uiptv.model

import com.uiptv.shared.BaseJson

class Bookmark() : BaseJson() {
    var dbId: String? = null
    var accountName: String? = null
    var categoryTitle: String? = null
    var channelId: String? = null
    var channelName: String? = null
    var logo: String? = null
    var cmd: String? = null
    var serverPortalUrl: String? = null
    var categoryId: String? = null
    var accountAction: Account.AccountAction? = null
    var drmType: String? = null
    var drmLicenseUrl: String? = null
    var clearKeysJson: String? = null
    var inputstreamaddon: String? = null
    var manifestType: String? = null
    var categoryJson: String? = null
    var channelJson: String? = null
    var vodJson: String? = null
    var seriesJson: String? = null

    constructor(
        accountName: String?,
        categoryTitle: String?,
        channelId: String?,
        channelName: String?,
        cmd: String?,
        serverPortalUrl: String?,
        categoryId: String?
    ) : this() {
        this.accountName = accountName
        this.categoryTitle = categoryTitle
        this.channelId = channelId
        this.channelName = channelName
        this.cmd = cmd
        this.serverPortalUrl = serverPortalUrl
        this.categoryId = categoryId
    }

    fun setFromChannel(channel: Channel) {
        logo = channel.logo
        drmType = channel.drmType
        drmLicenseUrl = channel.drmLicenseUrl
        clearKeysJson = channel.clearKeysJson
        manifestType = channel.manifestType
        inputstreamaddon = channel.inputstreamaddon
    }
}
