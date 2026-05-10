package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Configuration
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.update

class ConfigurationDb private constructor() : ExposedCrudRepository<String, Configuration>() {
    companion object {
        private val instance = ConfigurationDb()

        @JvmStatic
        fun get(): ConfigurationDb = instance
    }

    override fun findAll(): List<Configuration> = query {
        ConfigurationTable.selectAll()
            .map(ResultRow::toConfiguration)
    }

    override fun findById(id: String): Configuration? = query {
        id.toIntOrNull()
            ?.let { dbId -> ConfigurationTable.selectAll().where { ConfigurationTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toConfiguration()
    }

    override fun save(entity: Configuration): Configuration {
        query {
            val existingId = entity.dbId?.toIntOrNull()
                ?: ConfigurationTable.selectAll().limit(1).firstOrNull()?.get(ConfigurationTable.id)
            if (existingId == null) {
                val insertedId = ConfigurationTable.insert { row -> row.write(entity) }[ConfigurationTable.id]
                entity.dbId = insertedId.toString()
            } else {
                ConfigurationTable.update({ ConfigurationTable.id eq existingId }) { row -> row.write(entity) }
                entity.dbId = existingId.toString()
            }
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            ConfigurationTable.deleteWhere { ConfigurationTable.id eq dbId }
        }
    }

    fun getConfiguration(): Configuration = findAll().firstOrNull() ?: Configuration()

    fun clearAllCache() {
        try {
            query {
                CacheableTables.forEach { table -> table.deleteWhere { Op.TRUE } }
                AccountCacheTable.update({ Op.TRUE }) { row -> row[serverPortalUrl] = "" }
            }
        } catch (_: Exception) {
            // Cache clearing is best-effort; keep app startup resilient if a stale table or DB handle fails here.
        }
    }

    fun clearCache(account: Account?) {
        val accountId = account?.dbId ?: return
        val accountDbId = accountId.toIntOrNull() ?: return
        try {
            query {
                val liveCategoryIds = LiveCategoryCacheTable.selectAll()
                    .where { LiveCategoryCacheTable.accountId eq accountId }
                    .map { it[LiveCategoryCacheTable.id].toString() }
                if (liveCategoryIds.isNotEmpty()) {
                    LiveChannelCacheTable.deleteWhere { LiveChannelCacheTable.categoryId inList liveCategoryIds }
                }
                LiveCategoryCacheTable.deleteWhere { LiveCategoryCacheTable.accountId eq accountId }
                VodChannelCacheTable.deleteWhere { VodChannelCacheTable.accountId eq accountId }
                VodCategoryCacheTable.deleteWhere { VodCategoryCacheTable.accountId eq accountId }
                SeriesEpisodeCacheTable.deleteWhere { SeriesEpisodeCacheTable.accountId eq accountId }
                SeriesChannelCacheTable.deleteWhere { SeriesChannelCacheTable.accountId eq accountId }
                SeriesCategoryCacheTable.deleteWhere { SeriesCategoryCacheTable.accountId eq accountId }
                AccountCacheTable.update({ AccountCacheTable.id eq accountDbId }) { row -> row[serverPortalUrl] = "" }
            }
        } catch (_: Exception) {
            // Cache clearing is best-effort; preserve current app flow even if one table cannot be cleared.
        }
    }
}

private object ConfigurationTable : Table(DatabaseUtils.DbTable.CONFIGURATION_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val playerPath1 = text("playerPath1").nullable()
    val playerPath2 = text("playerPath2").nullable()
    val playerPath3 = text("playerPath3").nullable()
    val defaultPlayerPath = text("defaultPlayerPath").nullable()
    val filterCategoriesList = text("filterCategoriesList").nullable()
    val filterChannelsList = text("filterChannelsList").nullable()
    val pauseFiltering = text("pauseFiltering").nullable()
    val darkTheme = text("darkTheme").nullable()
    val serverPort = text("serverPort").nullable()
    val embeddedPlayer = text("embeddedPlayer").nullable()
    val enableFfmpegTranscoding = text("enableFfmpegTranscoding").nullable()
    val cacheExpiryDays = text("cacheExpiryDays").nullable()
    val enableThumbnails = text("enableThumbnails").nullable()
    val wideView = text("wideView").nullable()
    val languageLocale = text("languageLocale").nullable()
    val tmdbReadAccessToken = text("tmdbReadAccessToken").nullable()
    val filterLockHash = text("filterLockHash").nullable()
    val uiZoomPercent = text("uiZoomPercent").nullable()
    val enableLitePlayerFfmpeg = text("enableLitePlayerFfmpeg").nullable()
    val autoRunServerOnStartup = text("autoRunServerOnStartup").nullable()
    val vlcNetworkCachingMs = text("vlcNetworkCachingMs").nullable()
    val vlcLiveCachingMs = text("vlcLiveCachingMs").nullable()
    val publishedM3uCategoryMode = text("publishedM3uCategoryMode").nullable()
    val enableVlcHttpUserAgent = text("enableVlcHttpUserAgent").nullable()
    val enableVlcHttpForwardCookies = text("enableVlcHttpForwardCookies").nullable()
    val resolveChainAndDeepRedirects = text("resolveChainAndDeepRedirects").nullable()
    val filterLockUnlockDurationMinutes = text("filterLockUnlockDurationMinutes").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object AccountCacheTable : Table(DatabaseUtils.DbTable.ACCOUNT_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val serverPortalUrl = text("serverPortalUrl").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object LiveCategoryCacheTable : Table(DatabaseUtils.DbTable.CATEGORY_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object LiveChannelCacheTable : Table(DatabaseUtils.DbTable.CHANNEL_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val categoryId = text("categoryId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object VodCategoryCacheTable : Table(DatabaseUtils.DbTable.VOD_CATEGORY_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object VodChannelCacheTable : Table(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object SeriesCategoryCacheTable : Table(DatabaseUtils.DbTable.SERIES_CATEGORY_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object SeriesChannelCacheTable : Table(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private object SeriesEpisodeCacheTable : Table(DatabaseUtils.DbTable.SERIES_EPISODE_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()

    override val primaryKey = PrimaryKey(id)
}

private val CacheableTables = listOf(
    LiveChannelCacheTable,
    LiveCategoryCacheTable,
    VodChannelCacheTable,
    VodCategoryCacheTable,
    SeriesEpisodeCacheTable,
    SeriesChannelCacheTable,
    SeriesCategoryCacheTable
)

private fun ResultRow.toConfiguration(): Configuration =
    Configuration(
        dbId = this[ConfigurationTable.id].toString(),
        playerPath1 = this[ConfigurationTable.playerPath1],
        playerPath2 = this[ConfigurationTable.playerPath2],
        playerPath3 = this[ConfigurationTable.playerPath3],
        defaultPlayerPath = this[ConfigurationTable.defaultPlayerPath],
        filterCategoriesList = this[ConfigurationTable.filterCategoriesList],
        filterChannelsList = this[ConfigurationTable.filterChannelsList],
        serverPort = this[ConfigurationTable.serverPort],
        cacheExpiryDays = this[ConfigurationTable.cacheExpiryDays],
        languageLocale = this[ConfigurationTable.languageLocale],
        tmdbReadAccessToken = this[ConfigurationTable.tmdbReadAccessToken],
        filterLockHash = this[ConfigurationTable.filterLockHash],
        filterLockUnlockDurationMinutes = this[ConfigurationTable.filterLockUnlockDurationMinutes],
        uiZoomPercent = this[ConfigurationTable.uiZoomPercent],
        vlcNetworkCachingMs = this[ConfigurationTable.vlcNetworkCachingMs],
        vlcLiveCachingMs = this[ConfigurationTable.vlcLiveCachingMs],
        publishedM3uCategoryMode = this[ConfigurationTable.publishedM3uCategoryMode],
        darkTheme = this[ConfigurationTable.darkTheme].asBoolean(),
        pauseFiltering = this[ConfigurationTable.pauseFiltering].asBoolean(),
        embeddedPlayer = this[ConfigurationTable.embeddedPlayer].asBoolean(),
        wideView = this[ConfigurationTable.wideView].asBoolean(),
        enableFfmpegTranscoding = this[ConfigurationTable.enableFfmpegTranscoding].asBoolean(),
        enableLitePlayerFfmpeg = this[ConfigurationTable.enableLitePlayerFfmpeg].asBoolean(),
        autoRunServerOnStartup = this[ConfigurationTable.autoRunServerOnStartup].asBoolean(),
        enableThumbnails = this[ConfigurationTable.enableThumbnails].asBoolean(),
        enableVlcHttpUserAgent = this[ConfigurationTable.enableVlcHttpUserAgent].asMissingOrTrue(),
        enableVlcHttpForwardCookies = this[ConfigurationTable.enableVlcHttpForwardCookies].asMissingOrTrue(),
        resolveChainAndDeepRedirects = this[ConfigurationTable.resolveChainAndDeepRedirects].asBoolean()
    )

private fun <T : UpdateBuilder<*>> T.write(configuration: Configuration) {
    this[ConfigurationTable.playerPath1] = configuration.playerPath1
    this[ConfigurationTable.playerPath2] = configuration.playerPath2
    this[ConfigurationTable.playerPath3] = configuration.playerPath3
    this[ConfigurationTable.defaultPlayerPath] = configuration.defaultPlayerPath
    this[ConfigurationTable.filterCategoriesList] = configuration.filterCategoriesList
    this[ConfigurationTable.filterChannelsList] = configuration.filterChannelsList
    this[ConfigurationTable.pauseFiltering] = configuration.pauseFiltering.asDbBoolean()
    this[ConfigurationTable.darkTheme] = configuration.darkTheme.asDbBoolean()
    this[ConfigurationTable.serverPort] = configuration.serverPort
    this[ConfigurationTable.embeddedPlayer] = configuration.embeddedPlayer.asDbBoolean()
    this[ConfigurationTable.enableFfmpegTranscoding] = configuration.enableFfmpegTranscoding.asDbBoolean()
    this[ConfigurationTable.cacheExpiryDays] = configuration.cacheExpiryDays
    this[ConfigurationTable.enableThumbnails] = configuration.enableThumbnails.asDbBoolean()
    this[ConfigurationTable.wideView] = configuration.wideView.asDbBoolean()
    this[ConfigurationTable.languageLocale] = configuration.languageLocale
    this[ConfigurationTable.tmdbReadAccessToken] = configuration.tmdbReadAccessToken
    this[ConfigurationTable.filterLockHash] = configuration.filterLockHash
    this[ConfigurationTable.uiZoomPercent] = configuration.uiZoomPercent
    this[ConfigurationTable.enableLitePlayerFfmpeg] = configuration.enableLitePlayerFfmpeg.asDbBoolean()
    this[ConfigurationTable.autoRunServerOnStartup] = configuration.autoRunServerOnStartup.asDbBoolean()
    this[ConfigurationTable.vlcNetworkCachingMs] = configuration.vlcNetworkCachingMs
    this[ConfigurationTable.vlcLiveCachingMs] = configuration.vlcLiveCachingMs
    this[ConfigurationTable.publishedM3uCategoryMode] = configuration.publishedM3uCategoryMode
    this[ConfigurationTable.enableVlcHttpUserAgent] = configuration.enableVlcHttpUserAgent.asDbBoolean()
    this[ConfigurationTable.enableVlcHttpForwardCookies] = configuration.enableVlcHttpForwardCookies.asDbBoolean()
    this[ConfigurationTable.resolveChainAndDeepRedirects] = configuration.resolveChainAndDeepRedirects.asDbBoolean()
    this[ConfigurationTable.filterLockUnlockDurationMinutes] = configuration.filterLockUnlockDurationMinutes
}

private fun String?.asBoolean(defaultValue: Boolean = false): Boolean {
    if (this == null) {
        return defaultValue
    }
    return this.trim() == "1"
}

private fun String?.asMissingOrTrue(): Boolean =
    this == null || this.isBlank() || this.trim() != "0"
