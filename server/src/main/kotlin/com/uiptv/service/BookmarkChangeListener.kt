package com.uiptv.service

fun interface BookmarkChangeListener {
    fun onBookmarksChanged(revision: Long, updatedEpochMs: Long)
}
