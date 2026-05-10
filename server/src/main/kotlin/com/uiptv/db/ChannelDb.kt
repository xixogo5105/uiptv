package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import java.util.LinkedHashMap
import java.util.Locale

object ChannelDb : ExposedCrudRepository<String, Channel>() {
    @JvmStatic
    fun get(): ChannelDb = this

    @JvmStatic
    fun insert(channel: Channel, category: Category) {
        val categoryDbId = category.dbId.orEmpty()
        query {
            ChannelTable.insert { row -> row.write(channel, categoryDbId) }
        }
    }

    override fun findAll(): List<Channel> = query {
        ChannelTable.selectAll()
            .orderBy(ChannelTable.id to SortOrder.ASC)
            .map(ResultRow::toChannel)
    }

    override fun findById(id: String): Channel? = query {
        id.toIntOrNull()
            ?.let { dbId -> ChannelTable.selectAll().where { ChannelTable.id eq dbId }.limit(1).firstOrNull() }
            ?.toChannel()
    }

    override fun save(entity: Channel): Channel {
        val dbId = entity.dbId?.toIntOrNull()
        query {
            if (dbId != null) {
                ChannelTable.deleteWhere { ChannelTable.id eq dbId }
            }
            val insertedId = ChannelTable.insert { row ->
                row.write(entity, entity.categoryId.orEmpty())
            }[ChannelTable.id]
            entity.dbId = insertedId.toString()
        }
        return entity
    }

    override fun deleteById(id: String) {
        val dbId = id.toIntOrNull() ?: return
        query {
            ChannelTable.deleteWhere { ChannelTable.id eq dbId }
        }
    }

    fun getChannels(dbId: String): List<Channel> = query {
        ChannelTable.selectAll()
            .where { ChannelTable.categoryId eq dbId }
            .orderBy(ChannelTable.id to SortOrder.ASC)
            .map(ResultRow::toChannel)
    }

    fun getChannelCountForAccount(accountId: String): Int = query {
        val categoryIds = CategoryTable.selectAll()
            .where { CategoryTable.accountId eq accountId }
            .map { it[CategoryTable.id].toString() }
        if (categoryIds.isEmpty()) 0 else {
            ChannelTable.selectAll()
                .where { ChannelTable.categoryId inList categoryIds }
                .count()
                .toInt()
        }
    }

    fun getChannelById(dbId: String, categoryId: String): Channel? = query {
        val intId = dbId.toIntOrNull() ?: return@query null
        ChannelTable.selectAll()
            .where { (ChannelTable.id eq intId) and (ChannelTable.categoryId eq categoryId) }
            .limit(1)
            .firstOrNull()
            ?.toChannel()
    }

    fun getChannelByChannelIdAndAccount(channelId: String, accountId: String): Channel? = query {
        val categoryIds = CategoryTable.selectAll()
            .where { CategoryTable.accountId eq accountId }
            .map { it[CategoryTable.id].toString() }
        if (categoryIds.isEmpty()) {
            null
        } else {
            ChannelTable.selectAll()
                .where {
                    (ChannelTable.channelId eq channelId) and
                        (ChannelTable.categoryId inList categoryIds)
                }
                .limit(1)
                .firstOrNull()
                ?.toChannel()
        }
    }

    fun getChannelsByChannelIdsAndAccount(channelIds: Collection<String>?, accountId: String?): List<Channel> {
        if (channelIds.isNullOrEmpty() || accountId.isNullOrBlank()) {
            return emptyList()
        }
        val effectiveChannelIds = channelIds.filter { !it.isNullOrBlank() }.distinct()
        if (effectiveChannelIds.isEmpty()) {
            return emptyList()
        }
        return query {
            val categoryIds = CategoryTable.selectAll()
                .where { CategoryTable.accountId eq accountId }
                .map { it[CategoryTable.id].toString() }
            if (categoryIds.isEmpty()) {
                emptyList()
            } else {
                ChannelTable.selectAll()
                    .where {
                        (ChannelTable.channelId inList effectiveChannelIds) and
                            (ChannelTable.categoryId inList categoryIds)
                    }
                    .map(ResultRow::toChannel)
            }
        }
    }

    fun saveAll(channels: List<Channel>, dbCategoryId: String, account: Account) {
        val category = requireNotNull(CategoryDb.get().getCategoryByDbId(dbCategoryId, account)) {
            "Category not found for dbCategoryId=$dbCategoryId"
        }
        val categoryDbId = category.dbId.orEmpty()
        val dedupedChannels = dedupeChannelsCaseInsensitive(channels)
        query {
            ChannelTable.deleteWhere { ChannelTable.categoryId eq categoryDbId }
            if (dedupedChannels.isNotEmpty()) {
                ChannelTable.batchInsert(dedupedChannels) { channel ->
                    write(channel, categoryDbId)
                }
            }
        }
    }

    fun deleteByAccount(accountId: String) {
        query {
            val categoryIds = CategoryTable.selectAll()
                .where { CategoryTable.accountId eq accountId }
                .map { it[CategoryTable.id].toString() }
            if (categoryIds.isNotEmpty()) {
                ChannelTable.deleteWhere { ChannelTable.categoryId inList categoryIds }
            }
        }
    }

    private fun dedupeChannelsCaseInsensitive(channels: List<Channel>?): List<Channel> {
        if (channels.isNullOrEmpty()) {
            return emptyList()
        }
        val uniqueChannels = LinkedHashMap<String, Channel>()
        channels.filterNotNull().forEach { channel ->
            uniqueChannels.putIfAbsent(channelComparisonKey(channel), channel)
        }
        return ArrayList(uniqueChannels.values)
    }

    private fun channelComparisonKey(channel: Channel): String {
        val channelId = normalize(channel.channelId)
        if (channelId.isNotEmpty()) return "id:$channelId"
        val name = normalize(channel.name)
        if (name.isNotEmpty()) return "name:$name"
        val cmd = normalize(channel.cmd)
        if (cmd.isNotEmpty()) return "cmd:$cmd"
        return "fallback:${System.identityHashCode(channel)}"
    }

    private fun normalize(value: String?): String = value?.trim()?.lowercase(Locale.ROOT).orEmpty()
}

private object ChannelTable : Table(DatabaseUtils.DbTable.CHANNEL_TABLE.tableName) {
    val id = integer("id").autoIncrement()
    val channelId = text("channelId")
    val categoryId = text("categoryId").nullable()
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

    override val primaryKey = PrimaryKey(id)
}

private fun ResultRow.toChannel(): Channel = Channel(
    dbId = this[ChannelTable.id].toString(),
    channelId = this[ChannelTable.channelId],
    categoryId = this[ChannelTable.categoryId],
    name = this[ChannelTable.name],
    number = this[ChannelTable.number],
    cmd = this[ChannelTable.cmd],
    cmd_1 = this[ChannelTable.cmd1],
    cmd_2 = this[ChannelTable.cmd2],
    cmd_3 = this[ChannelTable.cmd3],
    logo = this[ChannelTable.logo],
    censored = this[ChannelTable.censored],
    status = this[ChannelTable.status],
    hd = this[ChannelTable.hd],
    drmType = this[ChannelTable.drmType],
    drmLicenseUrl = this[ChannelTable.drmLicenseUrl],
    clearKeysJson = this[ChannelTable.clearKeysJson],
    inputstreamaddon = this[ChannelTable.inputstreamaddon],
    manifestType = this[ChannelTable.manifestType]
)

private fun UpdateBuilder<*>.write(channel: Channel, categoryDbId: String) {
    this[ChannelTable.channelId] = channel.channelId.orEmpty()
    this[ChannelTable.categoryId] = categoryDbId
    this[ChannelTable.name] = channel.name
    this[ChannelTable.number] = channel.number
    this[ChannelTable.cmd] = channel.cmd
    this[ChannelTable.cmd1] = channel.cmd_1
    this[ChannelTable.cmd2] = channel.cmd_2
    this[ChannelTable.cmd3] = channel.cmd_3
    this[ChannelTable.logo] = channel.logo
    this[ChannelTable.censored] = channel.censored
    this[ChannelTable.status] = channel.status
    this[ChannelTable.hd] = channel.hd
    this[ChannelTable.drmType] = channel.drmType
    this[ChannelTable.drmLicenseUrl] = channel.drmLicenseUrl
    this[ChannelTable.clearKeysJson] = channel.clearKeysJson
    this[ChannelTable.inputstreamaddon] = channel.inputstreamaddon
    this[ChannelTable.manifestType] = channel.manifestType
}
