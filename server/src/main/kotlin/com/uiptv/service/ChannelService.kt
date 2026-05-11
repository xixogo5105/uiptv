package com.uiptv.service

import com.uiptv.api.LoggerCallback
import com.uiptv.db.CategoryDb
import com.uiptv.db.ChannelDb
import com.uiptv.db.SeriesChannelDb
import com.uiptv.db.VodChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.shared.Pagination
import com.uiptv.shared.PlaylistEntry
import com.uiptv.util.AccountType
import com.uiptv.util.AppLog
import com.uiptv.util.FetchAPI
import com.uiptv.util.FetchAPI.nullSafeInteger
import com.uiptv.util.FetchAPI.nullSafeString
import com.uiptv.util.RssParser
import com.uiptv.util.ServerUtils
import com.uiptv.util.StringUtils
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import com.uiptv.util.XtremeApiParser
import com.uiptv.util.json.asJsonString
import com.uiptv.util.json.optArray
import com.uiptv.util.json.optObject
import com.uiptv.util.json.parseJsonObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URI
import java.util.Collections
import java.util.Date
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

class ChannelService @JvmOverloads constructor(
    private val cacheService: CacheService = CacheServiceImpl(),
    private val contentFilterService: ContentFilterService = ContentFilterService,
    private val logoResolverService: LogoResolverService = LogoResolverService,
    private val configurationService: ConfigurationService = ConfigurationService,
    private val handshakeService: HandshakeService = HandshakeService()
) {

    @Throws(IOException::class)
    fun get(categoryId: String, account: Account, dbId: String): List<Channel> =
        get(categoryId, account, dbId, null, null, null)

    @Throws(IOException::class)
    fun get(categoryId: String, account: Account, dbId: String, logger: LoggerCallback?): List<Channel> =
        get(categoryId, account, dbId, logger, null, null)

    @Throws(IOException::class)
    fun get(categoryId: String, account: Account, dbId: String, logger: LoggerCallback?, callback: Consumer<List<Channel>>?): List<Channel> =
        get(categoryId, account, dbId, logger, callback, null)

    @Throws(IOException::class)
    fun get(
        categoryId: String,
        account: Account,
        dbId: String,
        logger: LoggerCallback?,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?
    ): List<Channel> = get(categoryId, account, dbId, logger, callback, isCancelled, null)

    @Throws(IOException::class)
    fun get(
        categoryId: String,
        account: Account,
        dbId: String,
        logger: LoggerCallback?,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        if (Account.NOT_LIVE_TV_CHANNELS.contains(account.action)) {
            return getNonLiveChannels(categoryId, account, dbId, logger, callback, isCancelled, progressCallback)
        }
        if (account.type == AccountType.RSS_FEED) {
            return publishChannels(maybeFilterChannels(rssChannels(categoryId, account), true), callback)
        }
        val channels = loadCachedLiveChannels(categoryId, dbId, account, logger)
        if (account.action == Account.AccountAction.itv && channels.isNotEmpty()) {
            val visibleChannels = maybeFilterChannels(dedupeChannels(channels), true)
            publishChannels(visibleChannels, callback)
            resolveChannelLogosAsync(visibleChannels, callback) { isCancelled != null && isCancelled.get() }
            return visibleChannels
        }
        channels.forEach(this::resolveLogoIfNeeded)
        if (account.type == AccountType.STALKER_PORTAL && account.action == Account.AccountAction.itv && channels.isEmpty()) {
            fetchAndCacheMissingLiveChannels(categoryId, account, dbId, callback, isCancelled, logger, channels)
        }
        val result = maybeFilterChannels(dedupeChannels(channels), true)
        return publishChannels(result, callback)
    }

    @Throws(IOException::class)
    private fun loadCachedLiveChannels(categoryId: String, dbId: String, account: Account, logger: LoggerCallback?): MutableList<Channel> {
        val channels = resolveCachedLiveChannels(categoryId, dbId, account).toMutableList()
        if (channels.isNotEmpty()) return channels
        if (cacheService.getChannelCountForAccount(account.dbId.orEmpty()) != 0) return channels
        cacheService.reloadCache(account, logger ?: LoggerCallback { message -> log.info(message) })
        return resolveCachedLiveChannels(categoryId, dbId, account).toMutableList()
    }

    private fun getNonLiveChannels(
        categoryId: String,
        account: Account,
        dbId: String,
        logger: LoggerCallback?,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        if (account.type == AccountType.STALKER_PORTAL) {
            ensureStalkerSession(account, logger)
        }
        return if (shouldUseVodSeriesDbCache(account)) {
            getCachedVodSeriesChannels(categoryId, account, dbId, logger, callback, isCancelled, progressCallback)
        } else {
            getVodOrSeries(categoryId, account, callback, isCancelled, logger, progressCallback)
        }
    }

    private fun getCachedVodSeriesChannels(
        categoryId: String,
        account: Account,
        dbId: String,
        logger: LoggerCallback?,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        val cachedChannels = getVodSeriesFromDbCache(account, dbId)
        if (cachedChannels.isNotEmpty() && isVodSeriesChannelsFresh(account, dbId)) {
            log(logger, "Loaded channels from local cache for category $categoryId.")
            progressCallback?.accept(PageProgress(cachedChannels.size, cachedChannels.size, 1, 1))
            return publishChannels(maybeFilterChannels(dedupeChannels(cachedChannels), true), callback)
        }
        log(logger, "No fresh cache found for category $categoryId. Fetching from portal...")
        val streamingCallback = callback != null && account.type == AccountType.STALKER_PORTAL
        val fetchedChannels = fetchVodSeriesFromProviderAllPages(categoryId, account, isCancelled, logger, callback, progressCallback)
        val cancelled = Thread.currentThread().isInterrupted || (isCancelled != null && isCancelled.get())
        if (fetchedChannels.isNotEmpty() && !cancelled) {
            try {
                saveVodSeriesToDbCache(account, dbId, fetchedChannels)
                log(logger, "Channel list complete for category $categoryId. Saved ${fetchedChannels.size} channels to local cache.")
            } catch (e: Exception) {
                log(logger, "Failed to save ${fetchedChannels.size} channels to local cache for category $categoryId. Error: ${e.message}")
            }
        } else if (cancelled) {
            log(logger, "Channel fetch cancelled before cache save for category $categoryId.")
        }
        val resolved = if (fetchedChannels.isNotEmpty()) fetchedChannels else cachedChannels
        return publishChannels(maybeFilterChannels(dedupeChannels(resolved), true), if (streamingCallback) null else callback)
    }

    private fun fetchAndCacheMissingLiveChannels(
        categoryId: String,
        account: Account,
        dbId: String,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?,
        channels: MutableList<Channel>
    ) {
        log(logger, "No cached live channels for category $categoryId. Fetching from portal...")
        val fetchedChannels = getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, false, logger)
        if (fetchedChannels.isNotEmpty()) {
            ChannelDb.get().saveAll(fetchedChannels, dbId, account)
            channels.addAll(fetchedChannels)
            log(logger, "Saved ${fetchedChannels.size} live channels to local cache.")
        }
    }

    private fun publishChannels(channels: List<Channel>, callback: Consumer<List<Channel>>?): List<Channel> {
        callback?.accept(channels)
        return channels
    }

    private fun resolveChannelLogosAsync(channels: List<Channel>?, callback: Consumer<List<Channel>>?, isCancelled: BooleanSupplier?) {
        if (channels.isNullOrEmpty()) return
        val logoThread = Thread({
            if (resolveAnyLogos(channels, isCancelled) && shouldPublishResolvedLogos(callback, isCancelled)) {
                callback!!.accept(channels)
            }
        }, "channel-logo-resolver")
        logoThread.isDaemon = true
        logoThread.start()
    }

    private fun resolveAnyLogos(channels: List<Channel>, isCancelled: BooleanSupplier?): Boolean {
        var updated = false
        for (channel in channels) {
            if (isLogoResolutionCancelled(isCancelled)) return false
            updated = resolveLogoIfChanged(channel) || updated
        }
        return updated
    }

    private fun isLogoResolutionCancelled(isCancelled: BooleanSupplier?): Boolean =
        Thread.currentThread().isInterrupted || (isCancelled != null && isCancelled.asBoolean)

    private fun resolveLogoIfChanged(channel: Channel?): Boolean {
        val before = channel?.logo
        resolveLogoIfNeeded(channel)
        return before != channel?.logo
    }

    private fun shouldPublishResolvedLogos(callback: Consumer<List<Channel>>?, isCancelled: BooleanSupplier?): Boolean =
        callback != null && (isCancelled == null || !isCancelled.asBoolean)

    private fun resolveCachedLiveChannels(categoryId: String, dbCategoryId: String, account: Account): List<Channel> {
        if (!isAllCategoryForLocalCachedProvider(categoryId, account)) {
            return dedupeChannels(ChannelDb.get().getChannels(dbCategoryId))
        }
        val directAll = dedupeChannels(ChannelDb.get().getChannels(dbCategoryId))
        if (directAll.isNotEmpty()) return directAll
        val merged = ArrayList<Channel>()
        for (category in CategoryDb.get().getCategories(account)) {
            if (isBlank(category.dbId)) continue
            merged.addAll(ChannelDb.get().getChannels(category.dbId.orEmpty()))
        }
        return dedupeChannels(merged)
    }

    private fun isAllCategoryForLocalCachedProvider(categoryId: String, account: Account?): Boolean =
        CategoryType.ALL.displayName().equals(categoryId, true) && account != null

    private fun getVodOrSeries(
        categoryId: String,
        account: Account,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        val cachedChannels = ArrayList(getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, true, logger, progressCallback))
        return maybeFilterChannels(cachedChannels, true)
    }

    private fun shouldUseVodSeriesDbCache(account: Account): Boolean =
        (account.action == Account.AccountAction.vod || account.action == Account.AccountAction.series) &&
            (account.type == AccountType.STALKER_PORTAL || account.type == AccountType.XTREME_API)

    private fun getVodSeriesFromDbCache(account: Account, dbCategoryId: String): List<Channel> {
        var channels = when (account.action) {
            Account.AccountAction.vod -> VodChannelDb.get().getChannels(account, dbCategoryId)
            Account.AccountAction.series -> SeriesChannelDb.get().getChannels(account, dbCategoryId)
            else -> emptyList()
        }
        channels = dedupeChannels(channels)
        channels.forEach { channel ->
            channel.logo = normalizeLogoUrl(account, channel.logo)
            if (isBlank(channel.logo)) {
                channel.logo = normalizeLogoUrl(account, extractLogoFromExtraJson(channel.extraJson))
            }
        }
        channels.forEach(this::resolveLogoIfNeeded)
        return channels
    }

    private fun isVodSeriesChannelsFresh(account: Account, dbCategoryId: String): Boolean {
        val cacheTtlMs = configurationService.getCacheExpiryMs()
        return when (account.action) {
            Account.AccountAction.vod -> VodChannelDb.get().isFresh(account, dbCategoryId, cacheTtlMs)
            Account.AccountAction.series -> SeriesChannelDb.get().isFresh(account, dbCategoryId, cacheTtlMs)
            else -> false
        }
    }

    private fun saveVodSeriesToDbCache(account: Account, dbCategoryId: String, channels: List<Channel>) {
        when (account.action) {
            Account.AccountAction.vod -> VodChannelDb.get().saveAll(channels, dbCategoryId, account)
            Account.AccountAction.series -> SeriesChannelDb.get().saveAll(channels, dbCategoryId, account)
            else -> {}
        }
    }

    private fun fetchVodSeriesFromProviderAllPages(
        categoryId: String,
        account: Account,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?,
        callback: Consumer<List<Channel>>?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        val channels = if (account.type == AccountType.XTREME_API) {
            val parsed = dedupeChannels(XtremeApiParser.parseChannels(categoryId, account))
            progressCallback?.accept(PageProgress(parsed.size, parsed.size, 1, 1))
            parsed
        } else {
            getStalkerPortalChOrSeries(categoryId, account, null, "0", callback, isCancelled, true, logger, progressCallback)
        }
        channels.forEach(this::resolveLogoIfNeeded)
        return channels
    }

    private fun rssChannels(category: String, account: Account): List<Channel> {
        val channels = LinkedHashSet<Channel>()
        val rssEntries = RssParser.parse(account.m3u8Path)
        rssEntries.stream()
            .filter { e ->
                CategoryType.ALL.displayName().equals(category, true) ||
                    e.groupTitle.equals(category, true) ||
                    e.id.equals(category, true)
            }
            .forEach { entry: PlaylistEntry ->
                val c = Channel(
                    entry.id,
                    entry.title,
                    null,
                    entry.getPlaylistEntry(),
                    null,
                    null,
                    null,
                    entry.logo,
                    0,
                    0,
                    0,
                    entry.drmType,
                    entry.drmLicenseUrl,
                    entry.clearKeys,
                    entry.inputstreamaddon,
                    entry.manifestType
                )
                resolveLogoIfNeeded(c)
                channels.add(c)
            }
        return channels.toList()
    }

    @Throws(IOException::class)
    fun reloadCache(account: Account, logger: LoggerCallback?) {
        cacheService.reloadCache(account, logger ?: LoggerCallback { message -> log.info(message) })
    }

    fun getChannelCountForAccount(accountId: String?): Int = cacheService.getChannelCountForAccount(accountId.orEmpty())

    fun getCachedLiveChannelsByDbCategoryId(dbCategoryId: String): List<Channel> =
        dedupeChannels(ChannelDb.get().getChannels(dbCategoryId))

    fun hasCachedLiveChannelsByDbCategoryId(dbCategoryId: String): Boolean =
        getCachedLiveChannelsByDbCategoryId(dbCategoryId).isNotEmpty()

    fun getChannelByChannelIdAndAccount(channelId: String?, accountId: String?): Channel? =
        getChannelByChannelIdAndAccount(channelId, accountId, true)

    fun getChannelByChannelIdAndAccount(channelId: String?, accountId: String?, resolveLogo: Boolean): Channel? {
        if (StringUtils.isBlank(channelId) || StringUtils.isBlank(accountId)) return null
        val channel = ChannelDb.get().getChannelByChannelIdAndAccount(channelId.orEmpty(), accountId.orEmpty())
        if (channel != null && resolveLogo) resolveLogoIfNeeded(channel)
        return channel
    }

    fun getChannelsByChannelIdsAndAccount(channelIds: Collection<String>?, accountId: String?): List<Channel> =
        getChannelsByChannelIdsAndAccount(channelIds, accountId, true)

    fun getChannelsByChannelIdsAndAccount(channelIds: Collection<String>?, accountId: String?, resolveLogo: Boolean): List<Channel> {
        if (channelIds.isNullOrEmpty() || StringUtils.isBlank(accountId)) return emptyList()
        val channels = ChannelDb.get().getChannelsByChannelIdsAndAccount(channelIds, accountId)
        if (resolveLogo) channels.forEach(this::resolveLogoIfNeeded)
        return channels
    }

    fun findCachedLiveChannel(account: Account?, channelId: String?, channelName: String?): Channel? {
        if (account == null || isBlank(account.dbId)) return null
        val byId = getChannelByChannelIdAndAccount(channelId, account.dbId)
        if (byId != null) return byId
        if (isBlank(channelName)) return null
        val targetName = channelName!!.trim()
        for (category in CategoryDb.get().getCategories(account)) {
            if (isBlank(category.dbId)) continue
            val byName = ChannelDb.get().getChannels(category.dbId.orEmpty()).firstOrNull { c ->
                isNotBlank(c.name) && targetName.equals(c.name!!.trim(), true)
            }
            if (byName != null) {
                resolveLogoIfNeeded(byName)
                return byName
            }
        }
        return null
    }

    fun findCachedVodChannel(account: Account?, categoryHint: String?, channelId: String?, channelName: String?): Channel? =
        findCachedVodOrSeriesChannel(account, categoryHint, channelId, channelName, false)

    fun findCachedSeriesChannel(account: Account?, categoryHint: String?, channelId: String?, channelName: String?): Channel? =
        findCachedVodOrSeriesChannel(account, categoryHint, channelId, channelName, true)

    private fun findCachedVodOrSeriesChannel(
        account: Account?,
        categoryHint: String?,
        channelId: String?,
        channelName: String?,
        seriesLookup: Boolean
    ): Channel? {
        if (account == null || isBlank(account.dbId)) return null
        val dbCategoryId = resolveCategoryDbId(account, categoryHint)
        if (isBlank(dbCategoryId)) return null
        val channels = if (seriesLookup) SeriesChannelDb.get().getChannels(account, dbCategoryId) else VodChannelDb.get().getChannels(account, dbCategoryId)
        if (channels.isEmpty()) return null
        if (isNotBlank(channelId)) {
            val byId = channels.firstOrNull { c -> isNotBlank(c.channelId) && channelId!!.trim() == c.channelId!!.trim() }
            if (byId != null) {
                resolveLogoIfNeeded(byId)
                return byId
            }
        }
        if (isNotBlank(channelName)) {
            val byName = channels.firstOrNull { c -> isNotBlank(c.name) && channelName!!.trim().equals(c.name!!.trim(), true) }
            if (byName != null) {
                resolveLogoIfNeeded(byName)
                return byName
            }
        }
        return null
    }

    private fun resolveCategoryDbId(account: Account?, categoryHint: String?): String {
        if (account == null || isBlank(account.dbId) || isBlank(categoryHint)) return ""
        val hint = categoryHint!!.trim()
        val categories = CategoryDb.get().getCategories(account)
        if (categories.isEmpty()) return ""
        categories.firstOrNull { c -> isNotBlank(c.dbId) && hint == c.dbId!!.trim() }?.let { return it.dbId.orEmpty() }
        categories.firstOrNull { c -> isNotBlank(c.title) && hint.equals(c.title!!.trim(), true) }?.let { return it.dbId.orEmpty() }
        return categories.firstOrNull { c -> isNotBlank(c.categoryId) && hint == c.categoryId!!.trim() }?.dbId.orEmpty()
    }

    fun getStalkerPortalChOrSeries(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?
    ): List<Channel> = getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, true)

    fun getStalkerPortalChOrSeries(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?
    ): List<Channel> = getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, true, logger, null)

    fun getStalkerPortalChOrSeries(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        censor: Boolean
    ): List<Channel> = getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, censor, null, null)

    fun getStalkerPortalChOrSeries(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        censor: Boolean,
        logger: LoggerCallback?
    ): List<Channel> = getStalkerPortalChOrSeries(category, account, movieId, seriesId, callback, isCancelled, censor, logger, null)

    fun getStalkerPortalChOrSeries(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        censor: Boolean,
        logger: LoggerCallback?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        log(logger, "Starting portal fetch for category $category.")
        val channelsFromPageZero = fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 0, logger, progressCallback)
        if (channelsFromPageZero.isNotEmpty()) {
            log(logger, "Portal fetch finished with ${channelsFromPageZero.size} channels.")
            return channelsFromPageZero
        }
        log(logger, "No channels on page 0. Retrying from page 1...")
        val fallback = dedupeChannels(fetchPagedStalkerChannels(category, account, movieId, seriesId, callback, isCancelled, censor, 1, logger, progressCallback))
        log(logger, "Portal fetch finished with ${fallback.size} channels.")
        return fallback
    }

    private fun fetchPagedStalkerChannels(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String?,
        callback: Consumer<List<Channel>>?,
        isCancelled: Supplier<Boolean>?,
        censor: Boolean,
        startPage: Int,
        logger: LoggerCallback?,
        progressCallback: Consumer<PageProgress>?
    ): List<Channel> {
        val channelList = ArrayList<Channel>()
        ensureStalkerAccountSession(account, logger)
        val throttle = resolveStalkerThrottle(account)
        val request = StalkerPageRequest(category, account, movieId, seriesId, censor)
        val firstPage = fetchInitialPage(request, startPage, isCancelled, logger, throttle) ?: return channelList
        if (isEmptyChannelPage(firstPage)) {
            log(logger, "Page $startPage returned no channels.")
            return channelList
        }
        appendFetchedPage(channelList, firstPage, startPage, callback, logger)
        val accumulator = PageAccumulator()
        accumulator.update(firstPage)
        emitProgress(progressCallback, accumulator.fetchedItems, accumulator.totalItems, startPage + 1, accumulator.pageCount)
        val plan = PaginationPlan(startPage, callback, isCancelled, logger, progressCallback, throttle, accumulator)
        paginateAdditionalPages(channelList, request, plan, firstPage)
        emitProgress(
            progressCallback,
            accumulator.fetchedItems,
            if (accumulator.totalItems > 0) accumulator.totalItems else accumulator.fetchedItems,
            if (accumulator.pageCount > 0) accumulator.pageCount else maxOf(1, startPage + 1),
            accumulator.pageCount
        )
        return dedupeChannels(channelList)
    }

    private fun fetchInitialPage(
        request: StalkerPageRequest,
        startPage: Int,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?,
        throttle: RequestThrottle
    ): PageFetchResult? {
        val attempt = fetchPageWithRetries(request, startPage, isCancelled, logger, throttle, true)
        if (attempt.page == null) return null
        return retryEmptyFirstPage(request, startPage, logger, attempt.page, throttle)
    }

    private fun paginateAdditionalPages(
        channelList: MutableList<Channel>,
        request: StalkerPageRequest,
        plan: PaginationPlan,
        firstPage: PageFetchResult
    ) {
        val maxAdditionalPages = resolveMaxAdditionalPages(firstPage)
        var stopPagination = false
        var pageNumber = plan.startPage + 1
        while (pageNumber <= plan.startPage + maxAdditionalPages && !stopPagination) {
            val attempt = fetchPageWithRetries(request, pageNumber, plan.isCancelled, plan.logger, plan.throttle)
            if (attempt.cancelled) {
                stopPagination = true
            } else {
                val page = attempt.page
                if (page == null) {
                    stopPagination = true
                } else if (isEmptyChannelPage(page)) {
                    log(plan.logger, "Page $pageNumber returned no channels. Stopping pagination.")
                    stopPagination = true
                } else {
                    appendFetchedPage(channelList, page, pageNumber, plan.callback, plan.logger)
                    plan.accumulator.update(page)
                    emitProgress(plan.progressCallback, plan.accumulator.fetchedItems, plan.accumulator.totalItems, pageNumber + 1, plan.accumulator.pageCount)
                }
            }
            pageNumber++
        }
    }

    private fun fetchPageWithRetries(
        request: StalkerPageRequest,
        pageNumber: Int,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?,
        throttle: RequestThrottle
    ): PageAttempt = fetchPageWithRetries(request, pageNumber, isCancelled, logger, throttle, false)

    private fun fetchPageWithRetries(
        request: StalkerPageRequest,
        pageNumber: Int,
        isCancelled: Supplier<Boolean>?,
        logger: LoggerCallback?,
        throttle: RequestThrottle,
        ignoreCancellation: Boolean
    ): PageAttempt {
        for (attempt in 0..STALKER_MAX_RETRIES_PER_PAGE) {
            if (!ignoreCancellation && isPageFetchCancelled(isCancelled)) {
                logFetchCancelled(logger, pageNumber)
                return PageAttempt.cancelledAttempt()
            }
            throttle.awaitPermit()
            try {
                val page = fetchStalkerPage(request.category, request.account, request.movieId, request.seriesId, request.censor, pageNumber, logger)
                val nextDelay = throttle.onSuccess()
                logNextPageDelay(logger, nextDelay)
                return PageAttempt.success(page)
            } catch (e: Exception) {
                val nextDelay = throttle.onFailure()
                logFetchFailure(logger, pageNumber, attempt + 1, nextDelay, e)
                if (attempt >= STALKER_MAX_RETRIES_PER_PAGE) {
                    logGivingUp(logger, pageNumber, attempt + 1)
                }
            }
        }
        return PageAttempt.failed()
    }

    private fun resolveMaxAdditionalPages(firstPage: PageFetchResult): Int =
        if (firstPage.pagination == null) MAX_PAGES_WITHOUT_PAGINATION else maxOf(firstPage.pagination.pageCount + 1, 2)

    private fun logNextPageDelay(logger: LoggerCallback?, nextDelay: Long) {
        log(logger, "Waiting ${nextDelay}ms before next page fetch.")
    }

    private fun logFetchFailure(logger: LoggerCallback?, pageNumber: Int, attempt: Int, nextDelay: Long, e: Exception) {
        log(logger, "Failed to fetch page $pageNumber (attempt $attempt). Backing off ${nextDelay}ms. Error: ${e.message}")
    }

    private fun logGivingUp(logger: LoggerCallback?, pageNumber: Int, attempts: Int) {
        log(logger, "Giving up on page $pageNumber after $attempts attempts.")
    }

    private fun logFetchCancelled(logger: LoggerCallback?, pageNumber: Int) {
        log(logger, "Portal fetch cancelled at page $pageNumber.")
    }

    private fun appendFetchedPage(
        channelList: MutableList<Channel>,
        page: PageFetchResult,
        pageNumber: Int,
        callback: Consumer<List<Channel>>?,
        logger: LoggerCallback?
    ) {
        channelList.addAll(page.channels)
        log(logger, "Fetched ${page.channels.size} channels from page $pageNumber.")
        callback?.accept(page.channels)
    }

    private fun ensureStalkerAccountSession(account: Account, logger: LoggerCallback?) {
        if (account.type == AccountType.STALKER_PORTAL) ensureStalkerSession(account, logger)
    }

    private fun retryEmptyFirstPage(
        request: StalkerPageRequest,
        startPage: Int,
        logger: LoggerCallback?,
        firstPage: PageFetchResult,
        throttle: RequestThrottle?
    ): PageFetchResult {
        if (!isEmptyChannelPage(firstPage) || request.account.type != AccountType.STALKER_PORTAL) return firstPage
        log(logger, "No channels returned. Refreshing Stalker session and retrying page $startPage once...")
        handshakeService.hardTokenRefresh(request.account)
        throttle?.awaitPermit()
        return try {
            val page = fetchStalkerPage(request.category, request.account, request.movieId, request.seriesId, request.censor, startPage, logger)
            if (throttle != null) {
                val nextDelay = throttle.onSuccess()
                logNextPageDelay(logger, nextDelay)
            }
            page
        } catch (e: Exception) {
            if (throttle != null) {
                val nextDelay = throttle.onFailure()
                log(logger, "Failed to retry page $startPage. Backing off ${nextDelay}ms. Error: ${e.message}")
            }
            firstPage
        }
    }

    private fun isEmptyChannelPage(page: PageFetchResult): Boolean = page.channels.isEmpty()

    private fun fetchStalkerPage(
        category: String,
        account: Account,
        movieId: String?,
        seriesId: String?,
        censor: Boolean,
        pageNumber: Int,
        logger: LoggerCallback?
    ): PageFetchResult {
        log(logger, "Fetching page $pageNumber for category $category...")
        val json = FetchAPI.fetch(getChannelOrSeriesParams(category, pageNumber, account.action, movieId, seriesId), account)
        val pagination = parsePagination(json, null)
        val channels = if (account.action == Account.AccountAction.itv) {
            parseItvChannels(json, censor)
        } else {
            parseVodChannels(account, json, censor)
        }
        return PageFetchResult(channels, pagination)
    }

    private fun isPageFetchCancelled(isCancelled: Supplier<Boolean>?): Boolean =
        Thread.currentThread().isInterrupted || (isCancelled != null && isCancelled.get())

    private fun resolveStalkerThrottle(account: Account?): RequestThrottle {
        var portalHost = ""
        if (account != null && !isBlank(account.serverPortalUrl)) {
            try {
                val uri = URI.create(account.serverPortalUrl)
                portalHost = uri.host ?: account.serverPortalUrl.orEmpty()
            } catch (_: Exception) {
                portalHost = account.serverPortalUrl.orEmpty()
            }
        }
        val key = (account?.dbId ?: "") + "|" + portalHost + "|" + (account?.action ?: "")
        return STALKER_THROTTLES.computeIfAbsent(key) {
            RequestThrottle(STALKER_BASE_DELAY_MS, STALKER_MAX_DELAY_MS, STALKER_JITTER_MS)
        }
    }

    private fun emitProgress(progressCallback: Consumer<PageProgress>?, fetchedItems: Int, totalItems: Int, pageNumber: Int, pageCount: Int) {
        progressCallback?.accept(PageProgress(maxOf(0, fetchedItems), maxOf(0, totalItems), maxOf(1, pageNumber), maxOf(0, pageCount)))
    }

    private fun ensureStalkerSession(account: Account?, logger: LoggerCallback?) {
        if (account == null || account.type != AccountType.STALKER_PORTAL) return
        if (account.isConnected() && isNotBlank(account.serverPortalUrl)) return
        log(logger, "Ensuring Stalker session...")
        handshakeService.connect(account)
    }

    private fun log(logger: LoggerCallback?, message: String) {
        logger?.log(message)
        log.info(message)
    }

    fun getSeries(categoryId: String, movieId: String?, account: Account, callback: Consumer<List<Channel>>?, isCancelled: Supplier<Boolean>?): List<Channel> =
        maybeFilterChannels(getStalkerPortalChOrSeries(categoryId, account, movieId, "0", callback, isCancelled), true)

    @Throws(IOException::class)
    fun read(category: Category, account: Account): List<Channel> {
        val categoryIdToUse = if (account.type == AccountType.STALKER_PORTAL || account.type == AccountType.XTREME_API) {
            category.categoryId
        } else {
            category.title
        }
        return get(categoryIdToUse.orEmpty(), account, category.dbId.orEmpty())
    }

    @Throws(IOException::class)
    fun readToJson(category: Category, account: Account): String {
        return ServerUtils.objectToJson(read(category, account))
    }

    fun parsePagination(json: String, logger: LoggerCallback?): Pagination? {
        return try {
            val js = parseJsonObject(json) ?: return null
            var pagination = js.optObject("pagination")
            if (pagination == null) pagination = js.optObject("js")
            if (pagination != null) {
                logger?.log("total_items " + nullSafeInteger(pagination, "total_items"))
                logger?.log("max_page_items " + nullSafeInteger(pagination, "max_page_items"))
                Pagination(nullSafeInteger(pagination, "total_items"), nullSafeInteger(pagination, "max_page_items"))
            } else {
                null
            }
        } catch (_: Exception) {
            AppLog.addErrorLog(ChannelService::class.java, "Error while processing response data")
            null
        }
    }

    fun parseItvChannels(json: String, censor: Boolean): List<Channel> {
        return try {
            val root = parseJsonObject(json) ?: return emptyList()
            val js = root.optObject("js") ?: root
            val list = js.optArray("data") ?: return emptyList()
            val channelList = ArrayList<Channel>()
            for (i in list.indices) {
                val jsonChannel = list.optObject(i) ?: continue
                val channel = Channel(
                    jsonChannel["id"]?.toString()?.trim('"'),
                    nullSafeString(jsonChannel, "name"),
                    nullSafeString(jsonChannel, "number"),
                    nullSafeString(jsonChannel, "cmd"),
                    nullSafeString(jsonChannel, "cmd_1"),
                    nullSafeString(jsonChannel, "cmd_2"),
                    nullSafeString(jsonChannel, "cmd_3"),
                    normalizeLogoUrl(null, nullSafeString(jsonChannel, "logo")),
                    nullSafeInteger(jsonChannel, FIELD_CENSORED),
                    nullSafeInteger(jsonChannel, FIELD_STATUS),
                    nullSafeInteger(jsonChannel, "hd"),
                    null,
                    null,
                    null,
                    null,
                    null
                )
                channel.categoryId = nullSafeString(jsonChannel, "tv_genre_id")
                channel.extraJson = jsonChannel.asJsonString()
                resolveLogoIfNeeded(channel)
                channelList.add(channel)
            }
            maybeFilterChannels(dedupeChannels(channelList), censor)
        } catch (_: Exception) {
            AppLog.addErrorLog(ChannelService::class.java, "Error while processing itv response data")
            emptyList()
        }
    }

    fun parseVodChannels(account: Account, json: String, censor: Boolean): List<Channel> {
        return try {
            val root = parseJsonObject(json) ?: return emptyList()
            val js = root.optObject("js") ?: root
            val list = js.optArray("data") ?: return emptyList()
            val channelList = ArrayList<Channel>()
            for (i in list.indices) {
                val jsonChannel = list.optObject(i) ?: continue
                var name = nullSafeString(jsonChannel, "name")
                if (isBlank(name)) name = nullSafeString(jsonChannel, "o_name")
                val number = nullSafeString(jsonChannel, "id")
                val cmd = nullSafeString(jsonChannel, "cmd")
                val categoryId = nullSafeString(jsonChannel, "tv_genre_id")
                val preferredLogo = preferredVodLogo(jsonChannel)
                if (account.action == Account.AccountAction.series && isNotBlank(cmd)) {
                    val seriesArray = jsonChannel.optArray("series") ?: continue
                    for (j in seriesArray.indices) {
                        val channel = Channel(
                            seriesArray[j].toString().trim('"'),
                            "$name - Episode ${seriesArray[j].toString().trim('\"')}",
                            number,
                            cmd,
                            null,
                            null,
                            null,
                            normalizeLogoUrl(account, preferredLogo),
                            nullSafeInteger(jsonChannel, FIELD_CENSORED),
                            nullSafeInteger(jsonChannel, FIELD_STATUS),
                            nullSafeInteger(jsonChannel, "hd"),
                            null,
                            null,
                            null,
                            null,
                            null
                        )
                        channel.categoryId = categoryId
                        channel.extraJson = jsonChannel.asJsonString()
                        resolveLogoIfNeeded(channel)
                        channelList.add(channel)
                    }
                } else {
                    val channel = Channel(
                        jsonChannel["id"]?.toString()?.trim('"'),
                        name,
                        number,
                        cmd,
                        null,
                        null,
                        null,
                        normalizeLogoUrl(account, preferredLogo),
                        nullSafeInteger(jsonChannel, FIELD_CENSORED),
                        nullSafeInteger(jsonChannel, FIELD_STATUS),
                        nullSafeInteger(jsonChannel, "hd"),
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                    channel.categoryId = categoryId
                    channel.extraJson = jsonChannel.asJsonString()
                    resolveLogoIfNeeded(channel)
                    channelList.add(channel)
                }
            }
            val censoredChannelList = maybeFilterChannels(dedupeChannels(channelList), censor).sortedWith(
                compareBy<Channel> { it.getCompareSeason() }.thenBy { it.getCompareEpisode() }
            )
            censoredChannelList
        } catch (_: Exception) {
            AppLog.addErrorLog(ChannelService::class.java, "Error while processing vod response data")
            emptyList()
        }
    }

    private fun preferredVodLogo(jsonChannel: kotlinx.serialization.json.JsonObject?): String {
        if (jsonChannel == null) return ""
        var logo = nullSafeString(jsonChannel, "screenshot_uri")
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "stream_icon")
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "cover")
        if (isBlank(logo)) logo = nullSafeString(jsonChannel, "movie_image")
        return logo
    }

    private fun extractLogoFromExtraJson(extraJson: String?): String {
        if (isBlank(extraJson)) return ""
        return try {
            val json = parseJsonObject(extraJson) ?: return ""
            var logo = nullSafeString(json, "screenshot_uri")
            if (isBlank(logo)) logo = nullSafeString(json, "stream_icon")
            if (isBlank(logo)) logo = nullSafeString(json, "cover")
            if (isBlank(logo)) logo = nullSafeString(json, "movie_image")
            logo
        } catch (_: Exception) {
            ""
        }
    }

    private fun normalizeLogoUrl(account: Account?, logo: String?): String {
        if (isBlank(logo)) return ""
        val value = trimWrappedLogo(logo)
        if (isBlank(value)) return ""
        if (value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) return value
        val portalAddress = resolvePortalAddress(account)
        if (value.startsWith("//")) return portalAddress.scheme + ":" + value
        if (value.startsWith("/") && isNotBlank(portalAddress.host)) return portalAddress.origin() + value
        return value
    }

    private fun trimWrappedLogo(logo: String?): String {
        var value = logo!!.trim().replace("\\/", "/")
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length - 1).trim()
        }
        return value
    }

    private fun resolvePortalAddress(account: Account?): PortalAddress {
        val portal = account?.serverPortalUrl.orEmpty()
        var scheme = "https"
        var host = ""
        var port = -1
        try {
            if (!isBlank(portal)) {
                val uri = URI.create(portal.trim())
                if (!isBlank(uri.scheme)) scheme = uri.scheme
                if (!isBlank(uri.host)) host = uri.host
                port = uri.port
            }
        } catch (_: Exception) {
        }
        return PortalAddress(scheme, host, port)
    }

    fun censor(channelList: List<Channel>): List<Channel> = contentFilterService.filterChannels(channelList) ?: channelList

    private fun maybeFilterChannels(channels: List<Channel>, applyFilter: Boolean): List<Channel> =
        if (applyFilter) contentFilterService.filterChannels(channels) ?: channels else channels

    private fun dedupeChannels(channels: List<Channel>?): List<Channel> {
        if (channels.isNullOrEmpty()) return channels ?: emptyList()
        val unique = LinkedHashMap<String, Channel>()
        for (c in channels) {
            val key = listOf(
                if (StringUtils.isBlank(c.channelId)) "" else c.channelId!!.trim(),
                if (StringUtils.isBlank(c.cmd)) "" else c.cmd!!.trim(),
                if (StringUtils.isBlank(c.name)) "" else c.name!!.trim().lowercase()
            ).joinToString("|")
            unique.putIfAbsent(key, c)
        }
        return ArrayList(unique.values)
    }

    private fun resolveLogoIfNeeded(channel: Channel?) {
        if (channel == null) return
        val currentLogo = channel.logo
        val hasAbsoluteLogo = isNotBlank(currentLogo) && (currentLogo!!.startsWith("http://") || currentLogo.startsWith("https://"))
        if (hasAbsoluteLogo) return
        val resolved = logoResolverService.resolve(channel.name, currentLogo)
        if (isNotBlank(resolved)) channel.logo = resolved
    }

    class PageProgress(
        val fetchedItems: Int,
        val totalItems: Int,
        val pageNumber: Int,
        val pageCount: Int
    ) {
        fun fetchedItems(): Int = fetchedItems
        fun totalItems(): Int = totalItems
        fun pageNumber(): Int = pageNumber
        fun pageCount(): Int = pageCount
    }

    private data class StalkerPageRequest(
        val category: String,
        val account: Account,
        val movieId: String?,
        val seriesId: String?,
        val censor: Boolean
    )

    private data class PaginationPlan(
        val startPage: Int,
        val callback: Consumer<List<Channel>>?,
        val isCancelled: Supplier<Boolean>?,
        val logger: LoggerCallback?,
        val progressCallback: Consumer<PageProgress>?,
        val throttle: RequestThrottle,
        val accumulator: PageAccumulator
    )

    class RequestThrottle(private val baseDelayMs: Long, private val maxDelayMs: Long, private val jitterMs: Long) {
        private var nextAllowedAtMs = 0L
        private var failures = 0

        fun awaitPermit() {
            val delay = synchronized(this) { nextAllowedAtMs - System.currentTimeMillis() }
            if (delay <= 0) return
            try {
                Thread.sleep(delay)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        @Synchronized
        fun onSuccess(): Long {
            failures = 0
            return scheduleNext(baseDelayMs)
        }

        @Synchronized
        fun onFailure(): Long {
            failures = minOf(failures + 1, 6)
            val backoff = baseDelayMs * (1L shl failures)
            return scheduleNext(minOf(backoff, maxDelayMs))
        }

        private fun scheduleNext(baseDelay: Long): Long {
            val jitter = if (jitterMs == 0L) 0L else ThreadLocalRandom.current().nextLong(-jitterMs, jitterMs + 1)
            val delay = maxOf(0L, baseDelay + jitter)
            nextAllowedAtMs = System.currentTimeMillis() + delay
            return delay
        }
    }

    private data class PageFetchResult(val channels: List<Channel>, val pagination: Pagination?)
    private data class PageAttempt(val page: PageFetchResult?, val cancelled: Boolean) {
        companion object {
            @JvmStatic fun success(page: PageFetchResult): PageAttempt = PageAttempt(page, false)
            @JvmStatic fun failed(): PageAttempt = PageAttempt(null, false)
            @JvmStatic fun cancelledAttempt(): PageAttempt = PageAttempt(null, true)
        }
    }

    private class PageAccumulator {
        var fetchedItems = 0
        var totalItems = 0
        var pageCount = 0

        fun update(page: PageFetchResult?) {
            if (page == null) return
            fetchedItems += page.channels.size
            if (page.pagination != null) {
                totalItems = maxOf(totalItems, page.pagination.maxPageItems)
                pageCount = maxOf(pageCount, page.pagination.pageCount)
            }
        }
    }

    private data class PortalAddress(val scheme: String, val host: String, val port: Int) {
        fun origin(): String = scheme + "://" + host + if (port > 0) ":$port" else ""
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChannelService::class.java)
        private const val FIELD_CENSORED = "censored"
        private const val FIELD_STATUS = "status"
        private const val MAX_PAGES_WITHOUT_PAGINATION = 200
        private val STALKER_BASE_DELAY_MS: Long = java.lang.Long.getLong("uiptv.stalker.page.delay.ms", 800L)
        private val STALKER_MAX_DELAY_MS: Long = java.lang.Long.getLong("uiptv.stalker.page.maxDelay.ms", 8000L)
        private val STALKER_JITTER_MS: Long = java.lang.Long.getLong("uiptv.stalker.page.jitter.ms", 200L)
        private val STALKER_MAX_RETRIES_PER_PAGE: Int = Integer.getInteger("uiptv.stalker.page.maxRetries", 2)
        private val STALKER_THROTTLES = ConcurrentHashMap<String, RequestThrottle>()
        private val defaultInstance by lazy { ChannelService() }

        @JvmStatic
        fun getChannelOrSeriesParams(
            category: String,
            pageNumber: Int,
            accountAction: Account.AccountAction,
            movieId: String?,
            seriesId: String?
        ): Map<String, String> {
            val params = HashMap<String, String>()
            params["type"] = accountAction.name
            params["action"] = "get_ordered_list"
            params["genre"] = category
            params["force_ch_link_check"] = ""
            params["fav"] = "0"
            params["sortby"] = "added"
            if (accountAction == Account.AccountAction.series) {
                params["movie_id"] = if (isBlank(movieId)) "0" else movieId!!
                params["category"] = category
                params["season_id"] = if (isBlank(seriesId)) "0" else seriesId!!
                params["episode_id"] = "0"
            }
            params["hd"] = "1"
            params["p"] = pageNumber.toString()
            params["per_page"] = "999"
            params["max_count"] = "0"
            params["JsHttpRequest"] = Date().time.toString() + "-xml"
            return params
        }
    }
}
