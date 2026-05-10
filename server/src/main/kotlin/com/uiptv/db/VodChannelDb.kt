package com.uiptv.db

import com.uiptv.model.Account
import com.uiptv.model.Channel
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

object VodChannelDb : BaseDb<Channel>(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE) {
    private const val WHERE_ACCOUNT_AND_CATEGORY = " WHERE accountId=? AND categoryId=?"

    @JvmStatic
    fun get(): VodChannelDb = this

    fun getChannels(account: Account, categoryId: String): List<Channel> = getAll(WHERE_ACCOUNT_AND_CATEGORY, arrayOf(account.dbId ?: "", categoryId))

    fun getChannelByChannelId(channelId: String, categoryId: String, accountId: String): Channel? =
        getAll(" WHERE channelId=? AND categoryId=? AND accountId=?", arrayOf(channelId, categoryId, accountId)).firstOrNull()

    fun getChannelByChannelIdAndAccount(channelId: String, accountId: String): Channel? =
        getAll(" WHERE channelId=? AND accountId=?", arrayOf(channelId, accountId)).firstOrNull()

    fun isFresh(account: Account, categoryId: String, maxAgeMs: Long): Boolean {
        if (maxAgeMs <= 0) return false
        val sql = "SELECT MAX(cachedAt) FROM ${DatabaseUtils.DbTable.VOD_CHANNEL_TABLE.tableName}$WHERE_ACCOUNT_AND_CATEGORY"
        try {
            openConnection().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, account.dbId)
                    statement.setString(2, categoryId)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            val cachedAt = rs.getLong(1)
                            return cachedAt > 0 && (System.currentTimeMillis() - cachedAt) <= maxAgeMs
                        }
                    }
                }
            }
        } catch (_: SQLException) {
        }
        return false
    }

    fun saveAll(channels: List<Channel>, categoryId: String, account: Account) {
        deleteByAccountAndCategory(account.dbId ?: "", categoryId)
        val cachedAt = System.currentTimeMillis()
        channels.forEach { insert(it, categoryId, account, cachedAt) }
    }

    fun deleteByAccount(accountId: String) {
        val sql = "DELETE FROM ${DatabaseUtils.DbTable.VOD_CHANNEL_TABLE.tableName} WHERE accountId=?"
        try {
            openConnection().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute delete query", e)
        }
    }

    private fun deleteByAccountAndCategory(accountId: String, categoryId: String) {
        val sql = "DELETE FROM ${DatabaseUtils.DbTable.VOD_CHANNEL_TABLE.tableName}$WHERE_ACCOUNT_AND_CATEGORY"
        try {
            openConnection().use { conn ->
                conn.prepareStatement(sql).use { statement ->
                    statement.setString(1, accountId)
                    statement.setString(2, categoryId)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute delete query", e)
        }
    }

    private fun insert(channel: Channel, categoryId: String, account: Account, cachedAt: Long) {
        try {
            openConnection().use { conn ->
                conn.prepareStatement(DatabaseUtils.insertTableSql(DatabaseUtils.DbTable.VOD_CHANNEL_TABLE)).use { statement ->
                    statement.setString(1, channel.channelId)
                    statement.setString(2, categoryId)
                    statement.setString(3, account.dbId)
                    statement.setString(4, channel.name)
                    statement.setString(5, channel.number)
                    statement.setString(6, channel.cmd)
                    statement.setString(7, channel.cmd_1)
                    statement.setString(8, channel.cmd_2)
                    statement.setString(9, channel.cmd_3)
                    statement.setString(10, channel.logo)
                    statement.setInt(11, channel.censored)
                    statement.setInt(12, channel.status)
                    statement.setInt(13, channel.hd)
                    statement.setString(14, channel.drmType)
                    statement.setString(15, channel.drmLicenseUrl)
                    statement.setString(16, channel.clearKeysJson)
                    statement.setString(17, channel.inputstreamaddon)
                    statement.setString(18, channel.manifestType)
                    statement.setString(19, channel.extraJson)
                    statement.setLong(20, cachedAt)
                    statement.execute()
                }
            }
        } catch (e: SQLException) {
            throw DatabaseAccessException("Unable to execute insert query", e)
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
        channel.extraJson = nullSafeString(resultSet, "extraJson")
        return channel
    }

    private fun openConnection(): Connection =
        try {
            SQLConnection::class.java.getDeclaredMethod("connect").invoke(null) as Connection
        } catch (ex: InvocationTargetException) {
            val target = ex.targetException
            if (target is SQLException) {
                throw target
            }
            throw IllegalStateException(target)
        }
}
