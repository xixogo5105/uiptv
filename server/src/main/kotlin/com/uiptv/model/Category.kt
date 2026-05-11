package com.uiptv.model

import com.uiptv.shared.BaseJson
import com.uiptv.util.StringUtils.safeGetString
import com.uiptv.util.json.KJsonObject

data class Category @JvmOverloads constructor(
    var dbId: String? = null,
    var accountId: String? = null,
    var accountType: String? = null,
    var categoryId: String? = null,
    var title: String? = null,
    var alias: String? = null,
    var extraJson: String? = null,
    @get:JvmName("isActiveSub")
    var activeSub: Boolean = false,
    var censored: Int = 0
) : BaseJson() {
    constructor(
        categoryId: String?,
        title: String?,
        alias: String?,
        activeSub: Boolean,
        censored: Int
    ) : this(null, null, null, categoryId, title, alias, null, activeSub, censored)

    companion object {
        @JvmStatic
        fun fromJson(json: String): Category? {
            return try {
                val jsonObj = KJsonObject(json)
                Category(
                    dbId = safeGetString(jsonObj, "dbId"),
                    accountId = safeGetString(jsonObj, "accountId"),
                    accountType = safeGetString(jsonObj, "accountType"),
                    categoryId = safeGetString(jsonObj, "categoryId"),
                    title = safeGetString(jsonObj, "title"),
                    alias = safeGetString(jsonObj, "alias"),
                    extraJson = safeGetString(jsonObj, "extraJson"),
                    activeSub = jsonObj.optBoolean("activeSub"),
                    censored = jsonObj.optInt("censored")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
