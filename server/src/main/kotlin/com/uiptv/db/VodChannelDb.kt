package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Channel
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.BatchInsertStatement
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.batchInsert

object VodChannelDb : ExposedCrudRepository<String, Channel>() {
    @JvmStatic
    fun get(): VodChannelDb = this

    override fun findAll(): List<Channel> = query {
        VodChannelTable.selectAll().map(ResultRow::toVodChannel)
    }

    override fun findById(id: String): Channel? = query {
        id.toIntOrNull()
            ?.let { dbId -> VodChannelTable.selectAll().where { VodChannelTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toVodChannel()
    }

    override fun save(entity: Channel): Channel {
        val dbId = entity.dbId?.toIntOrNull()
        query {
            if (dbId == null) {
                val insertedId = VodChannelTable.insert { row -> row.write(entity, 0L, "", "") }[VodChannelTable.id]
                entity.dbId = insertedId.toString()
            } else {
                VodChannelTable.deleteWhere { VodChannelTable.id eq dbId }
                val insertedId = VodChannelTable.insert { row -> row.write(entity, 0L, entity.categoryId.orEmpty(), "") }[VodChannelTable.id]
                entity.dbId = insertedId.toString()
            }
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            VodChannelTable.deleteWhere { VodChannelTable.id eq dbId }
        }
    }

    fun getChannels(account: Account, categoryId: String): List<Channel> = query {
        VodChannelTable.selectAll()
            .where { (VodChannelTable.accountId eq account.dbId.orEmpty()) and (VodChannelTable.categoryId eq categoryId) }
            .map(ResultRow::toVodChannel)
    }

    fun getChannelByChannelId(channelId: String, categoryId: String, accountId: String): Channel? = query {
        VodChannelTable.selectAll()
            .where {
                (VodChannelTable.channelId eq channelId) and
                    (VodChannelTable.categoryId eq categoryId) and
                    (VodChannelTable.accountId eq accountId)
            }
            .limit(1)
            .firstOrNull()
            ?.toVodChannel()
    }

    fun getChannelByChannelIdAndAccount(channelId: String, accountId: String): Channel? = query {
        VodChannelTable.selectAll()
            .where { (VodChannelTable.channelId eq channelId) and (VodChannelTable.accountId eq accountId) }
            .limit(1)
            .firstOrNull()
            ?.toVodChannel()
    }

    fun isFresh(account: Account, categoryId: String, maxAgeMs: Long): Boolean = query {
        if (maxAgeMs <= 0) {
            false
        } else {
            val latest = VodChannelTable.selectAll()
                .where { (VodChannelTable.accountId eq account.dbId.orEmpty()) and (VodChannelTable.categoryId eq categoryId) }
                .map { it[VodChannelTable.cachedAt] }
                .maxOrNull() ?: 0L
            latest > 0 && (System.currentTimeMillis() - latest) <= maxAgeMs
        }
    }

    fun saveAll(channels: List<Channel>, categoryId: String, account: Account) {
        val accountId = account.dbId.orEmpty()
        query {
            VodChannelTable.deleteWhere {
                (VodChannelTable.accountId eq accountId) and (VodChannelTable.categoryId eq categoryId)
            }
            if (channels.isNotEmpty()) {
                val cachedAt = System.currentTimeMillis()
                VodChannelTable.batchInsert(channels) { channel ->
                    write(channel, cachedAt, categoryId, accountId)
                }
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        query {
            VodChannelTable.deleteWhere { VodChannelTable.accountId eq accountId }
        }
    }

    fun getAll(extendedSql: String, parameters: Array<String>): List<Channel> {
        if (extendedSql == " WHERE accountId=? AND channelId=?" && parameters.size == 2) {
            return query {
                VodChannelTable.selectAll()
                    .where {
                        (VodChannelTable.accountId eq parameters[0]) and
                            (VodChannelTable.channelId eq parameters[1])
                    }
                    .map(ResultRow::toVodChannel)
            }
        }
        return emptyList()
    }
}

private object VodChannelTable : Table(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE.tableName) {
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

private fun ResultRow.toVodChannel(): Channel = Channel(
    dbId = this[VodChannelTable.id].toString(),
    channelId = this[VodChannelTable.channelId],
    categoryId = this[VodChannelTable.categoryId],
    name = this[VodChannelTable.name],
    number = this[VodChannelTable.number],
    cmd = this[VodChannelTable.cmd],
    cmd_1 = this[VodChannelTable.cmd1],
    cmd_2 = this[VodChannelTable.cmd2],
    cmd_3 = this[VodChannelTable.cmd3],
    logo = this[VodChannelTable.logo],
    extraJson = this[VodChannelTable.extraJson],
    censored = this[VodChannelTable.censored],
    status = this[VodChannelTable.status],
    hd = this[VodChannelTable.hd],
    drmType = this[VodChannelTable.drmType],
    drmLicenseUrl = this[VodChannelTable.drmLicenseUrl],
    clearKeysJson = this[VodChannelTable.clearKeysJson],
    inputstreamaddon = this[VodChannelTable.inputstreamaddon],
    manifestType = this[VodChannelTable.manifestType]
)

private fun UpdateBuilder<*>.write(channel: Channel, cachedAt: Long, categoryId: String, accountId: String) {
    this[VodChannelTable.channelId] = channel.channelId.orEmpty()
    this[VodChannelTable.categoryId] = categoryId
    this[VodChannelTable.accountId] = accountId
    this[VodChannelTable.name] = channel.name
    this[VodChannelTable.number] = channel.number
    this[VodChannelTable.cmd] = channel.cmd
    this[VodChannelTable.cmd1] = channel.cmd_1
    this[VodChannelTable.cmd2] = channel.cmd_2
    this[VodChannelTable.cmd3] = channel.cmd_3
    this[VodChannelTable.logo] = channel.logo
    this[VodChannelTable.censored] = channel.censored
    this[VodChannelTable.status] = channel.status
    this[VodChannelTable.hd] = channel.hd
    this[VodChannelTable.drmType] = channel.drmType
    this[VodChannelTable.drmLicenseUrl] = channel.drmLicenseUrl
    this[VodChannelTable.clearKeysJson] = channel.clearKeysJson
    this[VodChannelTable.inputstreamaddon] = channel.inputstreamaddon
    this[VodChannelTable.manifestType] = channel.manifestType
    this[VodChannelTable.extraJson] = channel.extraJson
    this[VodChannelTable.cachedAt] = cachedAt
}
