package com.uiptv.service

import com.uiptv.db.AccountInfoDb
import com.uiptv.model.AccountInfo
import com.uiptv.util.StringUtils

object AccountInfoService {
    fun getByAccountId(accountId: String?): AccountInfo? {
        if (StringUtils.isBlank(accountId)) {
            return null
        }
        return AccountInfoDb.get().getByAccountId(accountId)
    }
    fun save(info: AccountInfo) {
        AccountInfoDb.get().save(info)
    }
    fun deleteByAccountId(accountId: String?) {
        AccountInfoDb.get().deleteByAccountId(accountId)
    }
}
