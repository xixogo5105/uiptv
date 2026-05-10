package com.uiptv.model

import com.uiptv.shared.BaseJson

data class BookmarkCategory @JvmOverloads constructor(
    var id: String? = null,
    var name: String? = null
) : BaseJson()
