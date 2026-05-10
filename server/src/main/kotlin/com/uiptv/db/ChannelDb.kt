package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import java.sql.ResultSet
import java.sql.SQLException
import java.util.LinkedHashMap
import java.util.Locale

object ChannelDb : BaseDb<Channel>(DatabaseUtils.DbTable.CHANNEL_TABLE) {
    private const val BATCH_SIZE = 1000

    @JvmStatic
    fun get(): ChannelDb = this

    @JvmStatic
    fun insert(channel: Channel, category: Category) {
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.CHANNEL_TABLE)).use { statement ->
                    statement.setString(1, channel.channelId)
                    statement.setString(2, category.dbId)
                    statement.setString(3, channel.name)
                    statement.setString(4, channel.number)
                    statement.setString(5, channel.cmd)
                    statement.setString(6, channel.cmd_1)
                    statement.setString(7, channel.cmd_2)
                    statement.setString(8, channel.cmd_3)
                    statement.setString(9, channel.logo)
                    statement.setInt(10, channel.censored)
                    statement.setInt(11, channel.status)
                    statement.setInt(12, channel.hd)
                    statement.setString(13, channel.drmType)
                    statement.setString(14, channel.drmLicenseUrl)
                    statement.setString(15, channel.clearKeysJson)
                    statement.setString(16, channel.inputstreamaddon)
                    statement.setString(17, channel.manifestType)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute query", e)
        }
    }

    private fun deleteAll(categoryId: String) {
        val sql = "DELETE FROM ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CHANNEL_TABLE)} WHERE categoryId=?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, categoryId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute delete all query", e)
        }
    }

    fun getChannels(dbId: String): List<Channel> = getAll(" WHERE categoryId=?", arrayOf(dbId))

    fun getChannelCountForAccount(accountId: String): Int {
        val sql = "SELECT COUNT(*) FROM ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CHANNEL_TABLE)} " +
            "WHERE categoryId IN (SELECT id FROM ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CATEGORY_TABLE)} WHERE accountId = ?)"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            return rs.getInt(1)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute channel count for account query", e)
        }
        return 0
    }

    fun getChannelById(dbId: String, categoryId: String): Channel? =
        getAll(" WHERE id=? AND categoryId=?", arrayOf(dbId, categoryId)).firstOrNull()

    fun getChannelByChannelIdAndAccount(channelId: String, accountId: String): Channel? {
        val sql = "SELECT c.* FROM ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CHANNEL_TABLE)} c" +
            " INNER JOIN ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CATEGORY_TABLE)} cat ON c.categoryId = cat.id" +
            " WHERE c.channelId = ? AND cat.accountId = ?"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, channelId)
                    statement.setString(2, accountId)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            return populate(rs)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute getChannelByChannelIdAndAccount query", e)
        }
        return null
    }

    fun getChannelsByChannelIdsAndAccount(channelIds: Collection<String>?, accountId: String?): List<Channel> {
        if (channelIds.isNullOrEmpty() || accountId.isNullOrBlank()) {
            return emptyList()
        }
        val effectiveChannelIds = channelIds.filter { !it.isNullOrBlank() }.distinct()
        if (effectiveChannelIds.isEmpty()) {
            return emptyList()
        }
        val placeholders = List(effectiveChannelIds.size) { "?" }.joinToString(",")
        val sql = "SELECT c.* FROM ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CHANNEL_TABLE)} c" +
            " INNER JOIN ${DatabaseUtils.validatedTableName(DatabaseUtils.DbTable.CATEGORY_TABLE)} cat ON c.categoryId = cat.id" +
            " WHERE cat.accountId = ? AND c.channelId IN ($placeholders)"
        val channels = ArrayList<Channel>()
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    effectiveChannelIds.forEachIndexed { index, id -> statement.setString(index + 2, id) }
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            channels += populate(rs)
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute getChannelsByChannelIdsAndAccount query", e)
        }
        return channels
    }

    fun saveAll(channels: List<Channel>, dbCategoryId: String, account: Account) {
        val category = requireNotNull(CategoryDb().getCategoryByDbId(dbCategoryId, account)) {
            "Category not found for dbCategoryId=$dbCategoryId"
        }
        deleteAll(category.dbId ?: "")
        val dedupedChannels = dedupeChannelsCaseInsensitive(channels)
        try {
            SQLConnection.connect().use { conn ->
                conn.autoCommit = false
                try {
                    conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.CHANNEL_TABLE)).use { statement ->
                        var count = 0
                        dedupedChannels.forEach { channel ->
                            statement.setString(1, channel.channelId)
                            statement.setString(2, category.dbId)
                            statement.setString(3, channel.name)
                            statement.setString(4, channel.number)
                            statement.setString(5, channel.cmd)
                            statement.setString(6, channel.cmd_1)
                            statement.setString(7, channel.cmd_2)
                            statement.setString(8, channel.cmd_3)
                            statement.setString(9, channel.logo)
                            statement.setInt(10, channel.censored)
                            statement.setInt(11, channel.status)
                            statement.setInt(12, channel.hd)
                            statement.setString(13, channel.drmType)
                            statement.setString(14, channel.drmLicenseUrl)
                            statement.setString(15, channel.clearKeysJson)
                            statement.setString(16, channel.inputstreamaddon)
                            statement.setString(17, channel.manifestType)
                            statement.addBatch()
                            count++
                            if (count % BATCH_SIZE == 0) {
                                statement.executeBatch()
                            }
                        }
                        statement.executeBatch()
                    }
                    conn.commit()
                } catch (e: SQLException) {
                    conn.rollback()
                    throw DatabaseAccessException("Unable to execute saveAll query", e)
                } finally {
                    conn.autoCommit = true
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to connect to database", e)
        }
    }

    fun deleteByAccount(accountId: String) {
        val sql = "DELETE FROM ${DatabaseUtils.DbTable.CHANNEL_TABLE.tableName} " +
            "WHERE categoryId IN (SELECT id FROM ${DatabaseUtils.DbTable.CATEGORY_TABLE.tableName} WHERE accountId=?)"
        try {
            SQLConnection.connect().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute deleteByAccount query", e)
        }
    }

    override fun populate(resultSet: ResultSet): Channel {
        val channel = Channel(
            nullSafeString(resultSet, "channelId"),
            nullSafeString(resultSet, "name"),
            nullSafeString(resultSet, "number"),
            nullSafeString(resultSet, "cmd"),
            nullSafeString(resultSet, "cmd_1"),
            nullSafeString(resultSet, "cmd_2"),
            nullSafeString(resultSet, "cmd_3"),
            nullSafeString(resultSet, "logo"),
            safeInteger(resultSet, "censored"),
            safeInteger(resultSet, "status"),
            safeInteger(resultSet, "hd"),
            nullSafeString(resultSet, "drmType"),
            nullSafeString(resultSet, "drmLicenseUrl"),
            null,
            nullSafeString(resultSet, "inputstreamaddon"),
            nullSafeString(resultSet, "manifestType")
        )
        channel.dbId = nullSafeString(resultSet, "id")
        channel.categoryId = nullSafeString(resultSet, "categoryId")
        channel.clearKeysJson = nullSafeString(resultSet, "clearKeysJson")
        return channel
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
