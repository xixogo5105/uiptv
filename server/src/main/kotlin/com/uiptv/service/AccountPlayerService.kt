package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import java.io.IOException

interface AccountPlayerService {
    @Throws(IOException::class)
    fun get(account: Account, channel: Channel, series: String?, parentSeriesId: String?, categoryId: String?): PlayerResponse
}
