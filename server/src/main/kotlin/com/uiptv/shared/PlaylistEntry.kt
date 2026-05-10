package com.uiptv.shared

import com.uiptv.model.CategoryType

import com.uiptv.util.StringUtils.isBlank

class PlaylistEntry @JvmOverloads constructor(
    var id: String? = null,
    groupTitle: String? = null,
    var title: String? = null,
    var sourceUrl: String? = null,
    var logo: String? = null,
    var drmType: String? = null,
    var drmLicenseUrl: String? = null,
    var clearKeys: Map<String, String>? = null,
    var inputstreamaddon: String? = null,
    var manifestType: String? = null
) : BaseJson() {
    var groupTitle: String? = groupTitle
        get() = if (isBlank(field)) CategoryType.ALL.displayName() else field

    fun getPlaylistEntry(): String? = sourceUrl

    fun setPlaylistEntry(playlistEntry: String?) {
        sourceUrl = playlistEntry
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is PlaylistEntry) {
            return false
        }
        return groupTitle == other.groupTitle
    }

    override fun hashCode(): Int = groupTitle?.hashCode() ?: 0
}
