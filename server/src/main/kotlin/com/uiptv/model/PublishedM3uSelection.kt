package com.uiptv.model

import com.uiptv.shared.BaseJson

class PublishedM3uSelection() : BaseJson() {
    var dbId: String? = null
    var accountId: String? = null

    constructor(accountId: String?) : this() {
        this.accountId = accountId
    }
}
