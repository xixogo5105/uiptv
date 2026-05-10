package com.uiptv.model

import com.uiptv.shared.BaseJson

data class PublishedM3uCategorySelection @JvmOverloads constructor(
    var dbId: String? = null,
    var accountId: String? = null,
    var categoryName: String? = null,
    @get:JvmName("isSelected")
    var selected: Boolean = false
) : BaseJson() {
    constructor(accountId: String?, categoryName: String?, selected: Boolean) : this(null, accountId, categoryName, selected)
}
