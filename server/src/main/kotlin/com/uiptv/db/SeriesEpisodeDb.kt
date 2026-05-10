package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Channel
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class SeriesEpisodeDb private constructor() {
    companion object {
        private val instance = SeriesEpisodeDb()
        private const val EMPTY = ""

        @JvmStatic
        fun get(): SeriesEpisodeDb = instance
    }

    fun getEpisodes(account: Account, categoryId: String?, seriesId: String): List<Channel> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesEpisodeTable.selectAll()
                .where {
                    (SeriesEpisodeTable.accountId eq (account.dbId ?: EMPTY)) and
                        (SeriesEpisodeTable.categoryId eq safeCategoryId(categoryId)) and
                        (SeriesEpisodeTable.seriesId eq seriesId)
                }
                .map(ResultRow::toSeriesEpisodeChannel)
        }

    fun getEpisodesFromFreshestCategory(account: Account, seriesId: String): List<Channel> {
        val freshestCategoryId = getFreshestCategoryId(account, seriesId) ?: return emptyList()
        return getEpisodes(account, freshestCategoryId, seriesId)
    }

    fun isFresh(account: Account, categoryId: String?, seriesId: String, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0) {
            return false
        }
        return try {
            latestCachedAtForCategory(account, categoryId, seriesId)?.let { cachedAt -> isFreshEnough(cachedAt, maxAgeMs) } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isFreshInAnyCategory(account: Account, seriesId: String, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0) {
            return false
        }
        return try {
            latestCachedAtInAnyCategory(account, seriesId)?.let { cachedAt -> isFreshEnough(cachedAt, maxAgeMs) } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun saveAll(account: Account, categoryId: String?, seriesId: String, episodes: List<Channel>) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesEpisodeTable.deleteWhere {
                (accountId eq (account.dbId ?: EMPTY)) and
                    (this.categoryId eq safeCategoryId(categoryId)) and
                    (this.seriesId eq seriesId)
            }
            val cachedAt = System.currentTimeMillis()
            episodes.forEach { channel ->
                SeriesEpisodeTable.insert { row ->
                    row[accountId] = account.dbId
                    row[this.categoryId] = safeCategoryId(categoryId)
                    row[this.seriesId] = seriesId
                    row[channelId] = channel.channelId
                    row[name] = channel.name
                    row[cmd] = channel.cmd
                    row[logo] = channel.logo
                    row[season] = channel.season
                    row[episodeNum] = channel.episodeNum
                    row[description] = channel.description
                    row[releaseDate] = channel.releaseDate
                    row[rating] = channel.rating
                    row[duration] = channel.duration
                    row[extraJson] = channel.extraJson
                    row[this.cachedAt] = cachedAt
                }
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesEpisodeTable.deleteWhere { this.accountId eq accountId }
        }
    }

    private fun latestCachedAtForCategory(account: Account, categoryId: String?, seriesId: String): Long? =
        transaction(SqlConnectionRuntime.database()) {
            SeriesEpisodeTable.selectAll()
                .where {
                    (SeriesEpisodeTable.accountId eq (account.dbId ?: EMPTY)) and
                        (SeriesEpisodeTable.categoryId eq safeCategoryId(categoryId)) and
                        (SeriesEpisodeTable.seriesId eq seriesId)
                }
                .orderBy(SeriesEpisodeTable.cachedAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(SeriesEpisodeTable.cachedAt)
        }

    private fun latestCachedAtInAnyCategory(account: Account, seriesId: String): Long? =
        transaction(SqlConnectionRuntime.database()) {
            SeriesEpisodeTable.selectAll()
                .where {
                    (SeriesEpisodeTable.accountId eq (account.dbId ?: EMPTY)) and
                        (SeriesEpisodeTable.seriesId eq seriesId)
                }
                .orderBy(SeriesEpisodeTable.cachedAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
                ?.get(SeriesEpisodeTable.cachedAt)
        }

    private fun getFreshestCategoryId(account: Account, seriesId: String): String? =
        transaction(SqlConnectionRuntime.database()) {
            SeriesEpisodeTable.selectAll()
                .where {
                    (SeriesEpisodeTable.accountId eq (account.dbId ?: EMPTY)) and
                        (SeriesEpisodeTable.seriesId eq seriesId)
                }
                .orderBy(
                    SeriesEpisodeTable.cachedAt to SortOrder.DESC,
                    SeriesEpisodeTable.id to SortOrder.DESC
                )
                .limit(1)
                .firstOrNull()
                ?.get(SeriesEpisodeTable.categoryId)
                ?.trim()
                ?.ifEmpty { EMPTY }
        }

    private fun isFreshEnough(cachedAt: Long, maxAgeMs: Long): Boolean =
        cachedAt > 0 && (System.currentTimeMillis() - cachedAt) <= maxAgeMs

    private fun safeCategoryId(categoryId: String?): String = categoryId?.trim().orEmpty()
}

private object SeriesEpisodeTable : Table(DatabaseUtils.DbTable.SERIES_EPISODE_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()
    val categoryId = text("categoryId").nullable()
    val seriesId = text("seriesId").nullable()
    val channelId = text("channelId").nullable()
    val name = text("name").nullable()
    val cmd = text("cmd").nullable()
    val logo = text("logo").nullable()
    val season = text("season").nullable()
    val episodeNum = text("episodeNum").nullable()
    val description = text("description").nullable()
    val releaseDate = text("releaseDate").nullable()
    val rating = text("rating").nullable()
    val duration = text("duration").nullable()
    val extraJson = text("extraJson").nullable()
    val cachedAt = long("cachedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toSeriesEpisodeChannel(): Channel =
    Channel(
        dbId = this[SeriesEpisodeTable.id].toString(),
        channelId = this[SeriesEpisodeTable.channelId],
        name = this[SeriesEpisodeTable.name],
        cmd = this[SeriesEpisodeTable.cmd],
        logo = this[SeriesEpisodeTable.logo],
        season = this[SeriesEpisodeTable.season],
        episodeNum = this[SeriesEpisodeTable.episodeNum],
        description = this[SeriesEpisodeTable.description],
        releaseDate = this[SeriesEpisodeTable.releaseDate],
        rating = this[SeriesEpisodeTable.rating],
        duration = this[SeriesEpisodeTable.duration],
        extraJson = this[SeriesEpisodeTable.extraJson]
    )
