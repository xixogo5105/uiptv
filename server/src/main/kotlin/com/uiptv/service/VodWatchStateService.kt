package com.uiptv.service

import com.uiptv.db.VodWatchStateDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.VodWatchState
import com.uiptv.util.StringUtils
import java.util.concurrent.CopyOnWriteArraySet

object VodWatchStateService {
    private val listeners = CopyOnWriteArraySet<VodWatchStateChangeListener>()

    @JvmStatic
    fun getInstance(): VodWatchStateService = this
    fun getVod(accountId: String?, categoryId: String?, vodId: String?): VodWatchState? {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(vodId)) {
            return null
        }
        val canonicalVodId = canonicalizeVodId(vodId)
        val normalizedAccountId = accountId.orEmpty()
        val exact = VodWatchStateDb.get().getByVod(normalizedAccountId, normalize(categoryId), canonicalVodId)
        if (exact != null) {
            return exact
        }
        var latest: VodWatchState? = null
        for (candidate in VodWatchStateDb.get().getByVod(normalizedAccountId, canonicalVodId)) {
            if (candidate != null && (latest == null || candidate.updatedAt > latest!!.updatedAt)) {
                latest = candidate
            }
        }
        return latest
    }
    fun getAllByAccount(accountId: String?): List<VodWatchState> {
        if (StringUtils.isBlank(accountId)) {
            return emptyList()
        }
        return VodWatchStateDb.get().getByAccount(accountId.orEmpty())
    }
    fun isSaved(accountId: String?, categoryId: String?, vodId: String?): Boolean = getVod(accountId, categoryId, vodId) != null
    fun save(account: Account?, categoryId: String?, channel: Channel?) {
        if (account == null || StringUtils.isBlank(account.dbId) || channel == null || StringUtils.isBlank(channel.channelId)) {
            return
        }
        val state = VodWatchState()
        state.accountId = account.dbId
        state.categoryId = normalize(categoryId)
        state.vodId = canonicalizeVodId(channel.channelId)
        state.vodName = channel.name
        state.vodCmd = channel.cmd
        state.vodLogo = channel.logo
        state.updatedAt = System.currentTimeMillis()
        VodWatchStateDb.get().upsert(state)
        notifyListeners(account.dbId.orEmpty(), state.vodId.orEmpty())
    }
    fun remove(accountId: String?, categoryId: String?, vodId: String?) {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(vodId)) {
            return
        }
        val canonicalVodId = canonicalizeVodId(vodId)
        val normalizedAccountId = accountId.orEmpty()
        VodWatchStateDb.get().clear(normalizedAccountId, normalize(categoryId), canonicalVodId)
        notifyListeners(normalizedAccountId, canonicalVodId)
    }
    fun clearAll() {
        VodWatchStateDb.get().clearAll()
        notifyListeners("", "")
    }
    fun addChangeListener(listener: VodWatchStateChangeListener?) {
        if (listener != null) {
            listeners.add(listener)
        }
    }
    fun removeChangeListener(listener: VodWatchStateChangeListener?) {
        if (listener != null) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners(accountId: String, vodId: String) {
        listeners.forEach { it.onChanged(accountId, vodId) }
    }

    private fun normalize(value: String?): String = value?.trim().orEmpty()

    private fun canonicalizeVodId(vodId: String?): String {
        val raw = vodId?.trim().orEmpty()
        if (StringUtils.isBlank(raw) || !raw.contains(":")) {
            return raw
        }
        val last = raw.split(":").asReversed().firstOrNull { StringUtils.isNotBlank(it?.trim()) }?.trim().orEmpty()
        return if (StringUtils.isBlank(last)) raw else last
    }
}
