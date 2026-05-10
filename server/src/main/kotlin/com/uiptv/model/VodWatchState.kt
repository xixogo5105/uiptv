package com.uiptv.model

import com.uiptv.shared.BaseJson

data class VodWatchState @JvmOverloads constructor(
    var dbId: String? = null,
    var accountId: String? = null,
    var categoryId: String? = null,
    var vodId: String? = null,
    var vodName: String? = null,
    var vodCmd: String? = null,
    var vodLogo: String? = null,
    var updatedAt: Long = 0
) : BaseJson()
