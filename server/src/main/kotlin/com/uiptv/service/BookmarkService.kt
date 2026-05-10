package com.uiptv.service

import com.uiptv.db.BookmarkDb
import com.uiptv.model.Bookmark
import com.uiptv.model.BookmarkCategory
import com.uiptv.util.AppLog
import com.uiptv.util.ServerUtils
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

object BookmarkService {
    private val changeRevision = AtomicLong(1)
    private val changeListeners = CopyOnWriteArraySet<BookmarkChangeListener>()
    @Volatile
    private var lastUpdatedEpochMs = System.currentTimeMillis()

    @JvmStatic
    fun getInstance(): BookmarkService = this
    fun isChannelBookmarked(bookmark: Bookmark): Boolean = BookmarkDb.get().getBookmarkById(bookmark) != null
    fun getBookmark(dbId: String?): Bookmark? = BookmarkDb.get().getById(dbId.orEmpty())
    fun getBookmark(bookmark: Bookmark): Bookmark? = BookmarkDb.get().getBookmarkById(bookmark)
    fun toggleBookmark(bookmark: Bookmark) {
        val dbBookmark = BookmarkDb.get().getBookmarkById(bookmark)
        if (dbBookmark != null) {
            remove(dbBookmark.dbId)
        } else {
            save(bookmark)
        }
    }
    fun save(bookmark: Bookmark) {
        val created = BookmarkDb.get().save(bookmark)
        if (created) {
            BookmarkDb.get().saveBookmarkOrder(bookmark.dbId.orEmpty(), BookmarkDb.get().getNextDisplayOrder())
        }
        touchChange()
    }
    fun read(): List<Bookmark> = BookmarkDb.get().getBookmarks()
    fun read(offset: Int, limit: Int): List<Bookmark> {
        if (limit <= 0) {
            return read()
        }
        val safeOffset = maxOf(0, offset)
        val safeLimit = maxOf(0, limit)
        return BookmarkDb.get().getBookmarksPage(safeOffset, safeLimit)
    }
    fun getBookmarksByCategory(categoryId: String?): List<Bookmark> = BookmarkDb.get().getBookmarksByCategory(categoryId)
    fun remove(id: String?) {
        try {
            BookmarkDb.get().delete(id.orEmpty())
            touchChange()
        } catch (_: Exception) {
            AppLog.addErrorLog(BookmarkService::class.java, "Error while removing the bookmark")
        }
    }
    fun removeByAccountName(accountName: String?) {
        try {
            BookmarkDb.get().deleteByAccountName(accountName.orEmpty())
            touchChange()
        } catch (_: Exception) {
            AppLog.addErrorLog(BookmarkService::class.java, "Error while removing bookmarks for account")
        }
    }
    fun readToJson(): String {
        val bookmarks = ArrayList(BookmarkDb.get().getBookmarks())
        val resolved = BookmarkResolver().resolveBookmarks(bookmarks)
        return ServerUtils.objectToJson(resolved.map { it.bookmark })
    }
    fun readToJson(offset: Int, limit: Int): String {
        if (limit <= 0) {
            return readToJson()
        }
        val safeOffset = maxOf(0, offset)
        val safeLimit = maxOf(0, limit)
        val bookmarks = ArrayList(BookmarkDb.get().getBookmarksPage(safeOffset, safeLimit))
        val resolved = BookmarkResolver().resolveBookmarks(bookmarks)
        return ServerUtils.objectToJson(resolved.map { it.bookmark })
    }
    fun getAllCategories(): List<BookmarkCategory> = BookmarkDb.get().getAllCategories()
    fun addCategory(category: BookmarkCategory) {
        BookmarkDb.get().saveCategory(category)
        touchChange()
    }
    fun removeCategory(category: BookmarkCategory) {
        BookmarkDb.get().deleteCategory(category)
        touchChange()
    }
    fun notifyBookmarksChanged() {
        touchChange()
    }
    fun saveBookmarkOrder(orderedBookmarkDbIds: List<String>) {
        val bookmarkOrders = LinkedHashMap<String, Int>()
        orderedBookmarkDbIds.forEachIndexed { index, dbId -> bookmarkOrders[dbId] = index + 1 }
        saveBookmarkOrders(bookmarkOrders)
    }
    fun saveBookmarkOrders(bookmarkOrders: Map<String, Int>) {
        BookmarkDb.get().updateBookmarkOrders(bookmarkOrders)
        touchChange()
    }
    fun getChangeRevision(): Long = changeRevision.get()
    fun getLastUpdatedEpochMs(): Long = lastUpdatedEpochMs
    fun addChangeListener(listener: BookmarkChangeListener?) {
        if (listener != null) {
            changeListeners.add(listener)
        }
    }
    fun removeChangeListener(listener: BookmarkChangeListener?) {
        if (listener != null) {
            changeListeners.remove(listener)
        }
    }

    private fun touchChange() {
        lastUpdatedEpochMs = System.currentTimeMillis()
        val revision = changeRevision.incrementAndGet()
        changeListeners.forEach { listener ->
            try {
                listener.onBookmarksChanged(revision, lastUpdatedEpochMs)
            } catch (_: Exception) {
                // Listener failures must never break bookmark mutation flow.
            }
        }
    }
}
