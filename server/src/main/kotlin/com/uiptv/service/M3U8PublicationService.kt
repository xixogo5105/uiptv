package com.uiptv.service

import com.uiptv.db.PublishedM3uCategorySelectionDb
import com.uiptv.db.PublishedM3uChannelSelectionDb
import com.uiptv.db.PublishedM3uSelectionDb
import com.uiptv.model.Account
import com.uiptv.model.CategoryType
import com.uiptv.model.PublishedM3uCategorySelection
import com.uiptv.model.PublishedM3uChannelSelection
import com.uiptv.model.PublishedM3uSelection
import com.uiptv.util.AccountType
import com.uiptv.util.AppLog
import com.uiptv.util.M3uPlaylistUtils.escapeAttributeValue
import com.uiptv.util.M3uPlaylistUtils.parseAttribute
import com.uiptv.util.M3uPlaylistUtils.splitGroupTitles
import com.uiptv.util.ServerUrlUtil
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.HexFormat
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.stream.Collectors
import org.jetbrains.exposed.sql.transactions.transaction

object M3U8PublicationService {
    private val publishedSelectionDb = PublishedM3uSelectionDb.get()
    private val publishedCategorySelectionDb = PublishedM3uCategorySelectionDb.get()
    private val publishedChannelSelectionDb = PublishedM3uChannelSelectionDb.get()
    private val accountService = AccountService.getInstance()
    private val bookmarkService = BookmarkService.getInstance()
    private val configurationService = ConfigurationService.getInstance()

    private const val COMMENT_PREFIX = "#"
    private const val EXTM3U = "#EXTM3U"
    private const val EXTINF = "#EXTINF"
    const val BOOKMARKS_PLAYLIST_ACCOUNT_ID: String = "__bookmarks__"
    const val BOOKMARKS_PLAYLIST_NAME: String = "Bookmarks"
    private const val GROUP_TITLE_ATTR = "group-title"
    private const val PLAYLIST_LINE_SPLIT_REGEX = "\\r?\\n"

    @JvmStatic
    fun getInstance(): M3U8PublicationService = this
    fun getSelectedAccountIds(): Set<String> = getSelections().accountIds
    fun setSelectedAccountIds(accountIds: Set<String>?) {
        saveSelections(PublicationSelections(accountIds ?: emptySet(), emptyMap(), emptyMap()))
    }
    fun getSelections(): PublicationSelections {
        val accountIds = LinkedHashSet(
            publishedSelectionDb.getAllSelections().mapNotNull(PublishedM3uSelection::accountId)
        )

        val categorySelections = LinkedHashMap<CategorySelectionKey, Boolean>()
        publishedCategorySelectionDb.getAllSelections().forEach { selection ->
            categorySelections[CategorySelectionKey(selection.accountId, selection.categoryName)] = selection.selected
        }

        val channelSelections = LinkedHashMap<ChannelSelectionKey, Boolean>()
        publishedChannelSelectionDb.getAllSelections().forEach { selection ->
            channelSelections[
                ChannelSelectionKey(selection.accountId, selection.categoryName, selection.channelId)
            ] = selection.selected
        }

        return PublicationSelections(accountIds, categorySelections, channelSelections)
    }
    fun saveSelections(selections: PublicationSelections?) {
        val normalized = selections?.normalized() ?: PublicationSelections(emptySet(), emptyMap(), emptyMap())
        try {
            transaction(com.uiptv.db.SqlConnectionRuntime.database()) {
                publishedSelectionDb.replaceSelectionsInTransaction(normalized.accountIds)
                publishedCategorySelectionDb.replaceSelectionsInTransaction(
                    toCategorySelections(normalized.categorySelections)
                )
                publishedChannelSelectionDb.replaceSelectionsInTransaction(
                    toChannelSelections(normalized.channelSelections)
                )
            }
        } catch (e: Exception) {
            throw PublicationPersistenceException("Unable to save published M3U selections", e)
        }
    }
    fun getAvailableAccounts(): List<PlaylistAccountSummary> {
        val availableAccounts = ArrayList<PlaylistAccountSummary>()
        availableAccounts.add(PlaylistAccountSummary(BOOKMARKS_PLAYLIST_ACCOUNT_ID, BOOKMARKS_PLAYLIST_NAME))
        availableAccounts.addAll(
            getPublishableAccounts().map { account ->
                PlaylistAccountSummary(account.dbId.orEmpty(), account.accountName.orEmpty())
            }
        )
        return availableAccounts
    }
    fun getPlaylist(accountId: String?): PlaylistAccount? {
        if (isBlank(accountId)) return null
        if (isBookmarksPlaylistAccountId(accountId)) {
            return PlaylistAccount(BOOKMARKS_PLAYLIST_ACCOUNT_ID, BOOKMARKS_PLAYLIST_NAME, emptyList())
        }
        val account = accountService.getById(accountId)
        if (!isPublishableAccount(account)) return null
        return try {
            toPlaylistAccount(account!!, parsePlaylistEntries(account))
        } catch (e: IOException) {
            AppLog.addErrorLog(M3U8PublicationService::class.java, "Failed to load playlist for account '${account!!.accountName}'")
            AppLog.addErrorLog(M3U8PublicationService::class.java, e.message)
            null
        }
    }
    fun getPublishedM3u8(): String = getPublishedM3u8("")
    fun getPublishedM3u8(requestHost: String?): String {
        val selections = getSelections()
        if (selections.accountIds.isEmpty()) return ""
        val categoryMode = configurationService.getPublishedM3uCategoryMode()

        val result = StringBuilder()
        result.append(EXTM3U).append("\n")
        appendSelectedBookmarkPlaylist(result, selections.accountIds, requestHost.orEmpty(), categoryMode)
        getSelectedAccounts(selections.accountIds).forEach { account ->
            appendSelectedAccountPlaylist(result, account, selections, categoryMode)
        }
        return result.toString()
    }
    fun isBookmarksPlaylistAccountId(accountId: String?): Boolean = BOOKMARKS_PLAYLIST_ACCOUNT_ID == accountId

    private fun getPublishableAccounts(): List<Account> =
        accountService.getAll().values.filter { isPublishableAccount(it) }

    private fun getSelectedAccounts(accountIds: Set<String>): List<Account> =
        accountIds.asSequence()
            .filter { !isBookmarksPlaylistAccountId(it) }
            .mapNotNull(accountService::getById)
            .filter { isPublishableAccount(it) }
            .toList()

    private fun isPublishableAccount(account: Account?): Boolean =
        account != null && (account.type == AccountType.M3U8_LOCAL || account.type == AccountType.M3U8_URL)

    private fun appendSelectedAccountPlaylist(
        result: StringBuilder,
        account: Account,
        selections: PublicationSelections,
        categoryMode: PublishedCategoryMode
    ) {
        try {
            val selectedEntries = ArrayList<PlaylistChannelEntry>()
            parsePlaylistEntries(account).forEach { entry ->
                if (isChannelSelected(account.dbId, entry.categoryName, entry.channelId, selections)) {
                    selectedEntries.add(entry)
                }
            }
            val singleCategorySource = hasSingleEffectiveCategory(selectedEntries.flatMap { it.lines }, null)
            selectedEntries.forEach { entry ->
                appendPlaylistBlock(
                    result,
                    entry.lines,
                    account.accountName.orEmpty(),
                    entry.categoryName,
                    categoryMode,
                    singleCategorySource,
                    entry.splitFromMultiCategory
                )
            }
        } catch (e: IOException) {
            AppLog.addErrorLog(M3U8PublicationService::class.java, "Failed to append playlist for account '${account.accountName}'")
            AppLog.addErrorLog(M3U8PublicationService::class.java, e.message)
        }
    }

    private fun appendSelectedBookmarkPlaylist(
        result: StringBuilder,
        accountIds: Set<String>,
        requestHost: String,
        categoryMode: PublishedCategoryMode
    ) {
        if (!accountIds.contains(BOOKMARKS_PLAYLIST_ACCOUNT_ID)) return
        val host = resolveBookmarkPlaylistHost(requestHost)
        val bookmarkPlaylist = buildBookmarkPlaylist(host)
        val bookmarkPlaylistLines = splitPlaylistLines(bookmarkPlaylist)
        val singleCategorySource = hasSingleEffectiveCategory(bookmarkPlaylistLines, null)
        appendPlaylistBlock(
            result,
            bookmarkPlaylistLines,
            BOOKMARKS_PLAYLIST_NAME,
            null,
            categoryMode,
            singleCategorySource,
            false
        )
    }

    private fun resolveBookmarkPlaylistHost(requestHost: String): String =
        if (isNotBlank(requestHost)) requestHost.trim() else ServerUrlUtil.getLocalServerUrl().replaceFirst("^https?://".toRegex(), "")

    fun buildBookmarkPlaylist(host: String): String {
        val allTabName = com.uiptv.util.I18n.tr("commonAll")
        val bookmarks = bookmarkService.read()
        val categoryNameById = LinkedHashMap<String, String>()
        bookmarkService.getAllCategories().forEach { category ->
            val categoryId = category?.id
            val categoryName = category?.name
            if (isNotBlank(categoryId) && isNotBlank(categoryName)) {
                categoryNameById[categoryId!!] = categoryName!!
            }
        }

        val response = StringBuilder("#EXTM3U\n")
        bookmarks.forEach { bookmark ->
            if (isUncategorizedBookmark(bookmark, categoryNameById, allTabName)) {
                appendBookmarkPlaylistEntry(response, bookmark, host, "Misc")
            }
        }
        bookmarks.forEach { bookmark ->
            if (!isUncategorizedBookmark(bookmark, categoryNameById, allTabName)) {
                val categoryName = categoryNameById[bookmark.categoryId]
                if (isNotBlank(categoryName)) {
                    appendBookmarkPlaylistEntry(response, bookmark, host, categoryName!!)
                }
            }
        }
        return response.toString()
    }

    private fun isUncategorizedBookmark(
        bookmark: com.uiptv.model.Bookmark?,
        categoryNameById: Map<String, String>,
        allTabName: String
    ): Boolean {
        if (bookmark == null) return true
        val categoryId = bookmark.categoryId
        if (!isNotBlank(categoryId)) return true
        val categoryName = categoryNameById[categoryId]
        return !isNotBlank(categoryName) || categoryName.equals(allTabName, ignoreCase = true)
    }

    private fun appendBookmarkPlaylistEntry(
        response: StringBuilder,
        bookmark: com.uiptv.model.Bookmark,
        host: String,
        groupTitle: String
    ) {
        val requestedUrl = "http://$host/bookmarkEntry.ts?bookmarkId=${bookmark.dbId}"
        val channelName = com.uiptv.util.M3uPlaylistUtils.sanitizeTitle(bookmark.channelName)
        response.append("#EXTINF:-1 tvg-id=\"")
            .append(escapeAttributeValue(bookmark.dbId))
            .append("\" tvg-name=\"")
            .append(escapeAttributeValue(channelName))
            .append("\" group-title=\"")
            .append(escapeAttributeValue(groupTitle))
            .append("\",")
            .append(channelName)
            .append("\n")
            .append(requestedUrl)
            .append("\n")
    }

    private fun splitPlaylistLines(playlist: String): List<String> = playlist.split(Regex(PLAYLIST_LINE_SPLIT_REGEX))

    private fun appendPlaylistBlock(
        result: StringBuilder,
        lines: List<String>,
        sourceName: String,
        fallbackCategoryName: String?,
        categoryMode: PublishedCategoryMode,
        singleCategorySource: Boolean,
        splitFromMultiCategory: Boolean
    ) {
        lines.forEach { line ->
            if (!line.trim().startsWith(EXTM3U)) {
                result.append(
                    rewritePublishedLine(
                        line,
                        sourceName,
                        fallbackCategoryName,
                        categoryMode,
                        singleCategorySource,
                        splitFromMultiCategory
                    )
                ).append("\n")
            }
        }
    }

    private fun rewritePublishedLine(
        line: String,
        sourceName: String,
        fallbackCategoryName: String?,
        categoryMode: PublishedCategoryMode,
        singleCategorySource: Boolean,
        splitFromMultiCategory: Boolean
    ): String {
        if (!line.startsWith(EXTINF)) return line
        if (categoryMode == PublishedCategoryMode.ORIGINAL_CATEGORY) {
            if (splitFromMultiCategory && isNotBlank(fallbackCategoryName)) {
                return replaceOrAppendQuotedAttribute(line, GROUP_TITLE_ATTR, fallbackCategoryName!!.trim())
            }
            return line
        }
        if (singleCategorySource) {
            return replaceOrAppendQuotedAttribute(line, GROUP_TITLE_ATTR, sourceName)
        }
        val originalCategory = normalizePublishedCategory(parseQuotedAttribute(line, GROUP_TITLE_ATTR), fallbackCategoryName)
        val rewrittenCategory = categoryMode.format(sourceName, originalCategory)
        return replaceOrAppendQuotedAttribute(line, GROUP_TITLE_ATTR, rewrittenCategory)
    }

    private fun hasSingleEffectiveCategory(lines: List<String>, fallbackCategoryName: String?): Boolean {
        val categories = LinkedHashSet<String>()
        for (line in lines) {
            if (!line.startsWith(EXTINF)) continue
            val parsedCategories = splitGroupTitles(parseQuotedAttribute(line, GROUP_TITLE_ATTR))
            if (parsedCategories.isEmpty()) {
                categories.add(normalizePublishedCategory("", fallbackCategoryName))
            } else {
                parsedCategories.forEach { category ->
                    categories.add(normalizePublishedCategory(category, fallbackCategoryName))
                }
            }
            if (categories.size > 1) return false
        }
        return true
    }

    private fun normalizePublishedCategory(categoryName: String?, fallbackCategoryName: String?): String {
        if (!isBlank(categoryName)) return categoryName!!.trim()
        if (!isBlank(fallbackCategoryName)) return fallbackCategoryName!!.trim()
        return CategoryType.UNCATEGORIZED.displayName()
    }

    private fun replaceOrAppendQuotedAttribute(line: String, key: String, value: String): String {
        val escapedValue = escapeAttributeValue(value)
        val marker = "$key=\""
        val markerIndex = line.indexOf(marker)
        if (markerIndex >= 0) {
            val valueStart = markerIndex + marker.length
            val valueEnd = line.indexOf('"', valueStart)
            if (valueEnd >= 0) {
                return line.substring(0, valueStart) + escapedValue + line.substring(valueEnd)
            }
        }
        val commaIndex = line.lastIndexOf(',')
        if (commaIndex < 0) {
            return "$line $key=\"$escapedValue\""
        }
        return line.substring(0, commaIndex) + " $key=\"$escapedValue\"" + line.substring(commaIndex)
    }

    private fun isChannelSelected(
        accountId: String?,
        categoryName: String?,
        channelId: String?,
        selections: PublicationSelections
    ): Boolean {
        val channelKey = ChannelSelectionKey(accountId, categoryName, channelId)
        val channelSelection = selections.channelSelections[channelKey]
        if (channelSelection != null) return channelSelection

        val categoryKey = CategorySelectionKey(accountId, categoryName)
        val categorySelection = selections.categorySelections[categoryKey]
        if (categorySelection != null) return categorySelection

        return selections.accountIds.contains(accountId)
    }

    private fun toPlaylistAccount(account: Account, entries: List<PlaylistChannelEntry>): PlaylistAccount {
        val channelsByCategory = LinkedHashMap<String, CategoryBucket>()
        entries.forEach { entry ->
            val categoryKey = normalizeCategoryKey(entry.categoryName)
            val bucket = channelsByCategory.computeIfAbsent(categoryKey) {
                CategoryBucket(entry.categoryName, ArrayList())
            }
            bucket.channels.add(PlaylistChannel(entry.channelId, entry.title))
        }
        val categories = channelsByCategory.values.map { value ->
            PlaylistCategory(value.displayName, value.channels.toList())
        }
        return PlaylistAccount(account.dbId.orEmpty(), account.accountName.orEmpty(), categories)
    }

    private fun normalizeCategoryKey(categoryName: String?): String =
        if (isBlank(categoryName)) "" else categoryName!!.trim().lowercase(Locale.ROOT)

    private fun toCategorySelections(selections: Map<CategorySelectionKey, Boolean>): List<PublishedM3uCategorySelection> {
        val models = ArrayList<PublishedM3uCategorySelection>()
        selections.forEach { (key, value) ->
            if (key != null) {
                models.add(PublishedM3uCategorySelection(key.accountId, key.categoryName, value == true))
            }
        }
        return models
    }

    private fun toChannelSelections(selections: Map<ChannelSelectionKey, Boolean>): List<PublishedM3uChannelSelection> {
        val models = ArrayList<PublishedM3uChannelSelection>()
        selections.forEach { (key, value) ->
            if (key != null) {
                models.add(PublishedM3uChannelSelection(key.accountId, key.categoryName, key.channelId, value == true))
            }
        }
        return models
    }

    @Throws(IOException::class)
    private fun parsePlaylistEntries(account: Account): List<PlaylistChannelEntry> = parsePlaylistEntries(readPlaylistContent(account))

    fun parsePlaylistEntries(content: String): List<PlaylistChannelEntry> {
        val lines = Arrays.asList(*content.split(Regex(PLAYLIST_LINE_SPLIT_REGEX)).toTypedArray())
        val entries = ArrayList<PlaylistChannelEntry>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith(EXTINF)) {
                index++
                continue
            }
            val entry = parsePlaylistEntry(lines, index)
            entry.channelEntries?.let(entries::addAll)
            index = entry.nextIndex
        }
        return entries
    }

    private fun parsePlaylistEntry(lines: List<String>, startIndex: Int): ParsedPlaylistEntry {
        val extinfLine = lines[startIndex]
        val entryLines = ArrayList<String>()
        entryLines.add(extinfLine)

        val categoryNames = parseEffectiveCategoryNames(parseQuotedAttribute(extinfLine, GROUP_TITLE_ATTR))
        val title = parseEntryTitle(extinfLine)
        var sourceUrl = ""
        var index = startIndex + 1
        while (index < lines.size && !lines[index].startsWith(EXTINF)) {
            val nextLine = lines[index]
            entryLines.add(nextLine)
            if (isPlaylistMediaLine(nextLine)) {
                sourceUrl = nextLine.trim()
                index++
                break
            }
            index++
        }
        if (isBlank(sourceUrl)) {
            return ParsedPlaylistEntry(null, maxOf(index, startIndex + 1))
        }

        val channelId = buildChannelId(parseQuotedAttribute(extinfLine, "tvg-id"), title, sourceUrl)
        val channelEntries = ArrayList<PlaylistChannelEntry>()
        val splitFromMultiCategory = categoryNames.size > 1
        categoryNames.forEach { categoryName ->
            channelEntries.add(PlaylistChannelEntry(categoryName, channelId, title, entryLines.toList(), splitFromMultiCategory))
        }
        return ParsedPlaylistEntry(channelEntries, maxOf(index, startIndex + 1))
    }

    private fun isPlaylistMediaLine(line: String?): Boolean {
        val trimmed = line?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.startsWith(COMMENT_PREFIX)) return false
        if (trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) return true
        if (trimmed.startsWith("//") || trimmed.startsWith("/") || trimmed.startsWith("./") || trimmed.startsWith("../") || trimmed.matches(Regex("^[a-zA-Z]:\\\\.*"))) return true
        return trimmed.matches(Regex("(?i)^.+\\.(m3u8|mpd|ts|aac|mp3|mp4|m4s)(\\?.*)?$"))
    }

    private fun normalizeCategoryName(categoryName: String?): String =
        if (isBlank(categoryName)) CategoryType.UNCATEGORIZED.displayName() else categoryName!!.trim()

    private fun parseEffectiveCategoryNames(categoryName: String?): List<String> {
        val parsed = splitGroupTitles(categoryName)
        if (parsed.isEmpty()) return listOf(CategoryType.UNCATEGORIZED.displayName())
        return parsed.map(this::normalizeCategoryName)
    }

    private fun parseQuotedAttribute(line: String, key: String): String = parseAttribute(line, key)

    private fun parseEntryTitle(line: String): String {
        val lastCommaIndex = line.lastIndexOf(',')
        if (lastCommaIndex < 0 || lastCommaIndex >= line.length - 1) return ""
        return line.substring(lastCommaIndex + 1).trim()
    }

    private fun buildChannelId(tvgId: String?, title: String, sourceUrl: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update((if (isBlank(tvgId)) "" else tvgId).toString().toByteArray(StandardCharsets.UTF_8))
            digest.update('\n'.code.toByte())
            digest.update((if (isBlank(title)) "" else title).toByteArray(StandardCharsets.UTF_8))
            digest.update('\n'.code.toByte())
            digest.update((if (isBlank(sourceUrl)) "" else sourceUrl).toByteArray(StandardCharsets.UTF_8))
            HexFormat.of().formatHex(digest.digest())
        } catch (_: NoSuchAlgorithmException) {
            (if (isBlank(tvgId)) "" else tvgId) + "|" + title + "|" + sourceUrl
        }
    }

    @Throws(IOException::class)
    private fun readPlaylistContent(account: Account): String {
        return when (account.type) {
            AccountType.M3U8_LOCAL -> readFile(account.m3u8Path.orEmpty())
            AccountType.M3U8_URL -> readUrl(resolveRemotePlaylistUrl(account))
            else -> ""
        }
    }

    private fun resolveRemotePlaylistUrl(account: Account): String =
        if (!isBlank(account.m3u8Path)) account.m3u8Path.orEmpty() else account.url.orEmpty()

    @Throws(IOException::class)
    private fun readFile(path: String): String =
        BufferedReader(FileReader(path, StandardCharsets.UTF_8)).use { reader ->
            reader.lines().collect(Collectors.joining("\n"))
        }

    @Throws(IOException::class)
    private fun readUrl(urlString: String): String {
        val url = URL(urlString)
        return BufferedReader(InputStreamReader(url.openStream(), StandardCharsets.UTF_8)).use { reader ->
            reader.lines().collect(Collectors.joining("\n"))
        }
    }

    data class PublicationSelections(
        val accountIds: Set<String> = emptySet(),
        val categorySelections: Map<CategorySelectionKey, Boolean> = emptyMap(),
        val channelSelections: Map<ChannelSelectionKey, Boolean> = emptyMap()
    ) {
        fun accountIds(): Set<String> = accountIds

        fun categorySelections(): Map<CategorySelectionKey, Boolean> = categorySelections

        fun channelSelections(): Map<ChannelSelectionKey, Boolean> = channelSelections

        fun normalized(): PublicationSelections =
            PublicationSelections(
                LinkedHashSet(accountIds),
                LinkedHashMap(categorySelections),
                LinkedHashMap(channelSelections)
            )
    }

    data class CategorySelectionKey(val accountId: String?, val categoryName: String?) {
        fun accountId(): String? = accountId

        fun categoryName(): String? = categoryName
    }

    data class ChannelSelectionKey(val accountId: String?, val categoryName: String?, val channelId: String?) {
        fun accountId(): String? = accountId

        fun categoryName(): String? = categoryName

        fun channelId(): String? = channelId
    }

    data class PlaylistAccount(val accountId: String, val accountName: String, val categories: List<PlaylistCategory>) {
        fun accountId(): String = accountId

        fun accountName(): String = accountName

        fun categories(): List<PlaylistCategory> = categories
    }

    data class PlaylistAccountSummary(val accountId: String, val accountName: String) {
        fun accountId(): String = accountId

        fun accountName(): String = accountName
    }

    data class PlaylistCategory(val categoryName: String?, val channels: List<PlaylistChannel>) {
        fun categoryName(): String? = categoryName

        fun channels(): List<PlaylistChannel> = channels
    }

    data class PlaylistChannel(val channelId: String, val title: String) {
        fun channelId(): String = channelId

        fun title(): String = title
    }

    data class PlaylistChannelEntry(
        val categoryName: String,
        val channelId: String,
        val title: String,
        val lines: List<String>,
        val splitFromMultiCategory: Boolean
    ) {
        fun categoryName(): String = categoryName

        fun channelId(): String = channelId

        fun title(): String = title

        fun lines(): List<String> = lines

        fun splitFromMultiCategory(): Boolean = splitFromMultiCategory
    }

    private data class ParsedPlaylistEntry(val channelEntries: List<PlaylistChannelEntry>?, val nextIndex: Int)
    private data class CategoryBucket(val displayName: String, val channels: MutableList<PlaylistChannel>)

    private class PublicationPersistenceException(message: String, cause: Throwable) : RuntimeException(message, cause)

    enum class PublishedCategoryMode(private val persistedValue: String, private val labelKey: String) {
        ORIGINAL_CATEGORY("original", "publishM3uCategoryModeOriginal") {
            override fun format(sourceName: String, categoryName: String): String = categoryName
        },
        SOURCE_DASH_CATEGORY("source-dash-category", "publishM3uCategoryModeSourceDashCategory") {
            override fun format(sourceName: String, categoryName: String): String = "$sourceName - $categoryName"
        },
        CATEGORY_WITH_SOURCE("category-with-source", "publishM3uCategoryModeCategoryWithSource") {
            override fun format(sourceName: String, categoryName: String): String = "$categoryName [$sourceName]"
        },
        MULTI_GROUP("multi-group", "publishM3uCategoryModeMultiGroup") {
            override fun format(sourceName: String, categoryName: String): String = "$sourceName;$categoryName"
        };

        fun persistedValue(): String = persistedValue
        fun labelKey(): String = labelKey
        abstract fun format(sourceName: String, categoryName: String): String

        companion object {
            fun fromPersistedValue(raw: String?): PublishedCategoryMode {
                if (isBlank(raw)) return SOURCE_DASH_CATEGORY
                return entries.firstOrNull { it.persistedValue.equals(raw!!.trim(), true) } ?: SOURCE_DASH_CATEGORY
            }
        }
    }
}
