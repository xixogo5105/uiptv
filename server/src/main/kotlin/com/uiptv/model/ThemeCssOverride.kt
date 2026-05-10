package com.uiptv.model

import com.uiptv.shared.BaseJson

data class ThemeCssOverride @JvmOverloads constructor(
    var dbId: String? = null,
    var lightThemeCssName: String? = null,
    var lightThemeCssContent: String? = null,
    var darkThemeCssName: String? = null,
    var darkThemeCssContent: String? = null,
    var updatedAt: String? = null
) : BaseJson()
