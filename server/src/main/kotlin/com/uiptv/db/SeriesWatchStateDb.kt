package com.uiptv.db

import com.uiptv.model.SeriesWatchState
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object SeriesWatchStateDb {
    private const val MODE_SERIES = "series"

    @JvmStatic
    fun get(): SeriesWatchStateDb = this

    fun getBySeries(accountId: String, categoryId: String, seriesId: String): SeriesWatchState? =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.selectAll()
                .where {
                    (SeriesWatchStateTable.accountId eq accountId) and
                        (SeriesWatchStateTable.mode eq MODE_SERIES) and
                        (SeriesWatchStateTable.categoryId eq categoryId) and
                        (SeriesWatchStateTable.seriesId eq seriesId)
                }
                .limit(1)
                .firstOrNull()
                ?.toSeriesWatchState()
        }

    fun getBySeries(accountId: String, seriesId: String): List<SeriesWatchState> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.selectAll()
                .where {
                    (SeriesWatchStateTable.accountId eq accountId) and
                        (SeriesWatchStateTable.mode eq MODE_SERIES) and
                        (SeriesWatchStateTable.seriesId eq seriesId)
                }
                .orderBy(SeriesWatchStateTable.id to SortOrder.ASC)
                .map(ResultRow::toSeriesWatchState)
        }

    fun getByAccount(accountId: String, categoryId: String): List<SeriesWatchState> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.selectAll()
                .where {
                    (SeriesWatchStateTable.accountId eq accountId) and
                        (SeriesWatchStateTable.mode eq MODE_SERIES) and
                        (SeriesWatchStateTable.categoryId eq categoryId)
                }
                .orderBy(SeriesWatchStateTable.id to SortOrder.ASC)
                .map(ResultRow::toSeriesWatchState)
        }

    fun getByAccount(accountId: String): List<SeriesWatchState> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.selectAll()
                .where {
                    (SeriesWatchStateTable.accountId eq accountId) and
                        (SeriesWatchStateTable.mode eq MODE_SERIES)
                }
                .orderBy(SeriesWatchStateTable.id to SortOrder.ASC)
                .map(ResultRow::toSeriesWatchState)
        }

    fun upsert(state: SeriesWatchState) {
        transaction(SqlConnectionRuntime.database()) {
            val updated = SeriesWatchStateTable.update({
                (SeriesWatchStateTable.accountId eq state.accountId) and
                    (SeriesWatchStateTable.mode eq state.mode) and
                    (SeriesWatchStateTable.categoryId eq state.categoryId) and
                    (SeriesWatchStateTable.seriesId eq state.seriesId)
            }) { row ->
                row[episodeId] = state.episodeId
                row[episodeName] = state.episodeName
                row[season] = state.season
                row[episodeNum] = state.episodeNum
                row[updatedAt] = state.updatedAt
                row[sourceValue] = state.source
                row[seriesCategorySnapshot] = state.seriesCategorySnapshot
                row[seriesChannelSnapshot] = state.seriesChannelSnapshot
                row[seriesEpisodeSnapshot] = state.seriesEpisodeSnapshot
            }
            if (updated == 0) {
                SeriesWatchStateTable.insert { row ->
                    row[accountId] = state.accountId
                    row[mode] = state.mode
                    row[categoryId] = state.categoryId
                    row[seriesId] = state.seriesId
                    row[episodeId] = state.episodeId
                    row[episodeName] = state.episodeName
                    row[season] = state.season
                    row[episodeNum] = state.episodeNum
                    row[updatedAt] = state.updatedAt
                    row[sourceValue] = state.source
                    row[seriesCategorySnapshot] = state.seriesCategorySnapshot
                    row[seriesChannelSnapshot] = state.seriesChannelSnapshot
                    row[seriesEpisodeSnapshot] = state.seriesEpisodeSnapshot
                }
            }
        }
    }

    fun clear(accountId: String, categoryId: String, seriesId: String) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.deleteWhere {
                (this.accountId eq accountId) and
                    (mode eq MODE_SERIES) and
                    (this.categoryId eq categoryId) and
                    (this.seriesId eq seriesId)
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.deleteWhere { this.accountId eq accountId }
        }
    }

    fun clearAllSeries() {
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchStateTable.deleteWhere { SeriesWatchStateTable.mode eq MODE_SERIES }
        }
    }
}

private object SeriesWatchStateTable : Table(DatabaseUtils.DbTable.SERIES_WATCH_STATE_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()
    val mode = text("mode").nullable()
    val categoryId = text("categoryId").nullable()
    val seriesId = text("seriesId").nullable()
    val episodeId = text("episodeId").nullable()
    val episodeName = text("episodeName").nullable()
    val season = text("season").nullable()
    val episodeNum = integer("episodeNum").nullable()
    val updatedAt = long("updatedAt").nullable()
    val sourceValue = text("source").nullable()
    val seriesCategorySnapshot = text("seriesCategorySnapshot").nullable()
    val seriesChannelSnapshot = text("seriesChannelSnapshot").nullable()
    val seriesEpisodeSnapshot = text("seriesEpisodeSnapshot").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toSeriesWatchState(): SeriesWatchState =
    SeriesWatchState(
        dbId = this[SeriesWatchStateTable.id].toString(),
        accountId = this[SeriesWatchStateTable.accountId],
        mode = this[SeriesWatchStateTable.mode],
        categoryId = this[SeriesWatchStateTable.categoryId],
        seriesId = this[SeriesWatchStateTable.seriesId],
        episodeId = this[SeriesWatchStateTable.episodeId],
        episodeName = this[SeriesWatchStateTable.episodeName],
        season = this[SeriesWatchStateTable.season],
        episodeNum = this[SeriesWatchStateTable.episodeNum] ?: 0,
        updatedAt = this[SeriesWatchStateTable.updatedAt] ?: 0L,
        source = this[SeriesWatchStateTable.sourceValue],
        seriesCategorySnapshot = this[SeriesWatchStateTable.seriesCategorySnapshot],
        seriesChannelSnapshot = this[SeriesWatchStateTable.seriesChannelSnapshot],
        seriesEpisodeSnapshot = this[SeriesWatchStateTable.seriesEpisodeSnapshot]
    )
