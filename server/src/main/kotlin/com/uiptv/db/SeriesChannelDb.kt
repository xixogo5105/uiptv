package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Channel
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder

class SeriesChannelDb private constructor() : ExposedCrudRepository<String, Channel>() {
    companion object {
        private val instance = SeriesChannelDb()

        @JvmStatic
        fun get(): SeriesChannelDb = instance
    }

    override fun findAll(): List<Channel> = query {
        SeriesChannelTable.selectAll().map(ResultRow::toSeriesChannel)
    }

    override fun findById(id: String): Channel? = query {
        id.toIntOrNull()
            ?.let { dbId -> SeriesChannelTable.selectAll().where { SeriesChannelTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toSeriesChannel()
    }

    override fun save(entity: Channel): Channel {
        val dbId = entity.dbId?.toIntOrNull()
        query {
            if (dbId == null) {
                val insertedId = SeriesChannelTable.insert { row -> row.write(entity, 0L, "", "") }[SeriesChannelTable.id]
                entity.dbId = insertedId.toString()
            } else {
                SeriesChannelTable.deleteWhere { SeriesChannelTable.id eq dbId }
                val insertedId = SeriesChannelTable.insert { row -> row.write(entity, 0L, entity.categoryId.orEmpty(), "") }[SeriesChannelTable.id]
                entity.dbId = insertedId.toString()
            }
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            SeriesChannelTable.deleteWhere { SeriesChannelTable.id eq dbId }
        }
    }

    fun getChannels(account: Account, categoryId: String): List<Channel> = query {
        SeriesChannelTable.selectAll()
            .where { (SeriesChannelTable.accountId eq account.dbId.orEmpty()) and (SeriesChannelTable.categoryId eq categoryId) }
            .map(ResultRow::toSeriesChannel)
    }

    fun getChannelsBySeriesIds(account: Account?, seriesIds: List<String?>?): List<Channel> {
        if (account == null || seriesIds.isNullOrEmpty()) {
            return emptyList()
        }
        val filtered = seriesIds.filterNotNull().filter(String::isNotBlank).distinct()
        if (filtered.isEmpty()) {
            return emptyList()
        }
        return query {
            SeriesChannelTable.selectAll()
                .where {
                    (SeriesChannelTable.accountId eq account.dbId.orEmpty()) and
                        (SeriesChannelTable.channelId inList filtered)
                }
                .map(ResultRow::toSeriesChannel)
        }
    }

    fun isFresh(account: Account, categoryId: String, maxAgeMs: Long): Boolean = query {
        if (maxAgeMs <= 0) {
            false
        } else {
            val latest = SeriesChannelTable.selectAll()
                .where { (SeriesChannelTable.accountId eq account.dbId.orEmpty()) and (SeriesChannelTable.categoryId eq categoryId) }
                .map { it[SeriesChannelTable.cachedAt] }
                .maxOrNull() ?: 0L
            latest > 0 && (System.currentTimeMillis() - latest) <= maxAgeMs
        }
    }

    fun saveAll(channels: List<Channel>, categoryId: String, account: Account) {
        val accountId = account.dbId.orEmpty()
        query {
            SeriesChannelTable.deleteWhere {
                (SeriesChannelTable.accountId eq accountId) and (SeriesChannelTable.categoryId eq categoryId)
            }
            if (channels.isNotEmpty()) {
                val cachedAt = System.currentTimeMillis()
                SeriesChannelTable.batchInsert(channels) { channel ->
                    write(channel, cachedAt, categoryId, accountId)
                }
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        query {
            SeriesChannelTable.deleteWhere { SeriesChannelTable.accountId eq accountId }
        }
    }
}

private object SeriesChannelTable : Table(DatabaseUtils.DbTable.SERIES_CHANNEL_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val channelId = text("channelId")
    val categoryId = text("categoryId").nullable()
    val accountId = text("accountId").nullable()
    val name = text("name").nullable()
    val number = text("number").nullable()
    val cmd = text("cmd").nullable()
    val cmd1 = text("cmd_1").nullable()
    val cmd2 = text("cmd_2").nullable()
    val cmd3 = text("cmd_3").nullable()
    val logo = text("logo").nullable()
    val censored = integer("censored")
    val status = integer("status")
    val hd = integer("hd")
    val drmType = text("drmType").nullable()
    val drmLicenseUrl = text("drmLicenseUrl").nullable()
    val clearKeysJson = text("clearKeysJson").nullable()
    val inputstreamaddon = text("inputstreamaddon").nullable()
    val manifestType = text("manifestType").nullable()
    val extraJson = text("extraJson").nullable()
    val cachedAt = long("cachedAt")

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toSeriesChannel(): Channel = Channel(
    dbId = this[SeriesChannelTable.id].toString(),
    channelId = this[SeriesChannelTable.channelId],
    categoryId = this[SeriesChannelTable.categoryId],
    name = this[SeriesChannelTable.name],
    number = this[SeriesChannelTable.number],
    cmd = this[SeriesChannelTable.cmd],
    cmd_1 = this[SeriesChannelTable.cmd1],
    cmd_2 = this[SeriesChannelTable.cmd2],
    cmd_3 = this[SeriesChannelTable.cmd3],
    logo = this[SeriesChannelTable.logo],
    extraJson = this[SeriesChannelTable.extraJson],
    censored = this[SeriesChannelTable.censored],
    status = this[SeriesChannelTable.status],
    hd = this[SeriesChannelTable.hd],
    drmType = this[SeriesChannelTable.drmType],
    drmLicenseUrl = this[SeriesChannelTable.drmLicenseUrl],
    clearKeysJson = this[SeriesChannelTable.clearKeysJson],
    inputstreamaddon = this[SeriesChannelTable.inputstreamaddon],
    manifestType = this[SeriesChannelTable.manifestType]
)

private fun UpdateBuilder<*>.write(channel: Channel, cachedAt: Long, categoryId: String, accountId: String) {
    this[SeriesChannelTable.channelId] = channel.channelId.orEmpty()
    this[SeriesChannelTable.categoryId] = categoryId
    this[SeriesChannelTable.accountId] = accountId
    this[SeriesChannelTable.name] = channel.name
    this[SeriesChannelTable.number] = channel.number
    this[SeriesChannelTable.cmd] = channel.cmd
    this[SeriesChannelTable.cmd1] = channel.cmd_1
    this[SeriesChannelTable.cmd2] = channel.cmd_2
    this[SeriesChannelTable.cmd3] = channel.cmd_3
    this[SeriesChannelTable.logo] = channel.logo
    this[SeriesChannelTable.censored] = channel.censored
    this[SeriesChannelTable.status] = channel.status
    this[SeriesChannelTable.hd] = channel.hd
    this[SeriesChannelTable.drmType] = channel.drmType
    this[SeriesChannelTable.drmLicenseUrl] = channel.drmLicenseUrl
    this[SeriesChannelTable.clearKeysJson] = channel.clearKeysJson
    this[SeriesChannelTable.inputstreamaddon] = channel.inputstreamaddon
    this[SeriesChannelTable.manifestType] = channel.manifestType
    this[SeriesChannelTable.extraJson] = channel.extraJson
    this[SeriesChannelTable.cachedAt] = cachedAt
}
