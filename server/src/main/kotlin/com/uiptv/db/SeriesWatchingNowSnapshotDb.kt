package com.uiptv.db

import com.uiptv.model.SeriesWatchingNowSnapshot
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

object SeriesWatchingNowSnapshotDb {
    @JvmStatic
    fun get(): SeriesWatchingNowSnapshotDb = this

    fun getBySeries(accountId: String, categoryId: String, seriesId: String): SeriesWatchingNowSnapshot? =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchingNowSnapshotTable.selectAll()
                .where {
                    (SeriesWatchingNowSnapshotTable.accountId eq accountId) and
                        (SeriesWatchingNowSnapshotTable.categoryId eq categoryId) and
                        (SeriesWatchingNowSnapshotTable.seriesId eq seriesId)
                }
                .limit(1)
                .firstOrNull()
                ?.toSeriesWatchingNowSnapshot()
        }

    fun getBySeries(accountId: String, seriesId: String): List<SeriesWatchingNowSnapshot> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchingNowSnapshotTable.selectAll()
                .where {
                    (SeriesWatchingNowSnapshotTable.accountId eq accountId) and
                        (SeriesWatchingNowSnapshotTable.seriesId eq seriesId)
                }
                .orderBy(SeriesWatchingNowSnapshotTable.id to SortOrder.ASC)
                .map(ResultRow::toSeriesWatchingNowSnapshot)
        }

    fun getByAccount(accountId: String): List<SeriesWatchingNowSnapshot> =
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchingNowSnapshotTable.selectAll()
                .where { SeriesWatchingNowSnapshotTable.accountId eq accountId }
                .orderBy(SeriesWatchingNowSnapshotTable.id to SortOrder.ASC)
                .map(ResultRow::toSeriesWatchingNowSnapshot)
        }

    fun upsert(snapshot: SeriesWatchingNowSnapshot) {
        transaction(SqlConnectionRuntime.database()) {
            val updated = SeriesWatchingNowSnapshotTable.update({
                (SeriesWatchingNowSnapshotTable.accountId eq snapshot.accountId) and
                    (SeriesWatchingNowSnapshotTable.categoryId eq snapshot.categoryId) and
                    (SeriesWatchingNowSnapshotTable.seriesId eq snapshot.seriesId)
            }) { row ->
                row[categoryDbId] = snapshot.categoryDbId
                row[seriesTitle] = snapshot.seriesTitle
                row[seriesPoster] = snapshot.seriesPoster
                row[episodesJson] = snapshot.episodesJson
                row[updatedAt] = snapshot.updatedAt
            }
            if (updated == 0) {
                SeriesWatchingNowSnapshotTable.insert { row ->
                    row[accountId] = snapshot.accountId
                    row[categoryId] = snapshot.categoryId
                    row[seriesId] = snapshot.seriesId
                    row[categoryDbId] = snapshot.categoryDbId
                    row[seriesTitle] = snapshot.seriesTitle
                    row[seriesPoster] = snapshot.seriesPoster
                    row[episodesJson] = snapshot.episodesJson
                    row[updatedAt] = snapshot.updatedAt
                }
            }
        }
    }

    fun clear(accountId: String, categoryId: String, seriesId: String) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchingNowSnapshotTable.deleteWhere {
                (this.accountId eq accountId) and
                    (this.categoryId eq categoryId) and
                    (this.seriesId eq seriesId)
            }
        }
    }

    fun clearAll() {
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchingNowSnapshotTable.deleteAll()
        }
    }

    fun deleteByAccount(accountId: String) {
        transaction(SqlConnectionRuntime.database()) {
            SeriesWatchingNowSnapshotTable.deleteWhere { this.accountId eq accountId }
        }
    }
}

private object SeriesWatchingNowSnapshotTable : Table(DatabaseUtils.DbTable.SERIES_WATCHING_NOW_SNAPSHOT_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val accountId = text("accountId").nullable()
    val categoryId = text("categoryId").nullable()
    val seriesId = text("seriesId").nullable()
    val categoryDbId = text("categoryDbId").nullable()
    val seriesTitle = text("seriesTitle").nullable()
    val seriesPoster = text("seriesPoster").nullable()
    val episodesJson = text("episodesJson").nullable()
    val updatedAt = long("updatedAt").nullable()

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toSeriesWatchingNowSnapshot(): SeriesWatchingNowSnapshot =
    SeriesWatchingNowSnapshot(
        dbId = this[SeriesWatchingNowSnapshotTable.id].toString(),
        accountId = this[SeriesWatchingNowSnapshotTable.accountId],
        categoryId = this[SeriesWatchingNowSnapshotTable.categoryId],
        seriesId = this[SeriesWatchingNowSnapshotTable.seriesId],
        categoryDbId = this[SeriesWatchingNowSnapshotTable.categoryDbId],
        seriesTitle = this[SeriesWatchingNowSnapshotTable.seriesTitle],
        seriesPoster = this[SeriesWatchingNowSnapshotTable.seriesPoster],
        episodesJson = this[SeriesWatchingNowSnapshotTable.episodesJson],
        updatedAt = this[SeriesWatchingNowSnapshotTable.updatedAt] ?: 0L
    )
