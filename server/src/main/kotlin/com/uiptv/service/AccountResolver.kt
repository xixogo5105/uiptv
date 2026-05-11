package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.shared.BaseJson

class AccountResolver(
    private val accountServiceProvider: () -> AccountService = { AccountService }
) {
    fun resolveAccounts(): List<AccountRow> =
        accountServiceProvider.invoke().getAll().values.map { fromAccount(it) }

    fun fromAccount(account: Account?): AccountRow {
        val row = AccountRow()
        if (account == null) {
            return row
        }
        row.accountName = account.accountName
        row.dbId = account.dbId
        row.type = account.type.name
        row.pinToTop = account.pinToTop
        row.pinSvgStemPath = PIN_SVG_STEM_PATH
        row.pinSvgHeadPath = PIN_SVG_HEAD_PATH
        row.pinSvgStemFill = PIN_SVG_STEM_FILL
        row.pinSvgHeadFill = PIN_SVG_HEAD_FILL
        row.pinSvgViewBox = PIN_SVG_VIEW_BOX
        row.pinSvgScale = PIN_SVG_SCALE
        return row
    }

    class AccountRow : BaseJson() {
        var accountName: String? = null
        var dbId: String? = null
        var type: String? = null
        @get:JvmName("isPinToTop")
        var pinToTop: Boolean = false
        var pinSvgStemPath: String? = null
        var pinSvgHeadPath: String? = null
        var pinSvgStemFill: String? = null
        var pinSvgHeadFill: String? = null
        var pinSvgViewBox: String? = null
        var pinSvgScale: Double = 0.0
    }

    companion object {
        const val PIN_SVG_STEM_PATH: String = "m 289.99122,309.99418 c -0.66028,0.58344 -50.08221,-43.19021 -52.50936,-45.29992 -2.42734,-2.10956 -51.06934,-43.57426 -52.83626,-46.26739 -1.76673,-2.69328 13.04928,-12.78624 13.70956,-13.36969 0.66024,-0.58341 12.52054,-14.06148 14.94736,-11.95215 2.42733,2.10957 37.03325,55.97684 38.80018,58.66996 1.76673,2.69328 38.54876,57.6358 37.88852,58.21919 z"
        const val PIN_SVG_HEAD_PATH: String = "m 56.34936,106.22036 c 20.30938,0.88278 45.68909,32.12704 73.173,75.95489 18.76942,29.93108 45.31357,11.58173 54.19751,2.7927 8.31501,-8.2259 25.42173,-32.179 -3.72915,-51.99008 -42.68539,-29.00919 -72.93354,-55.50764 -73.173,-75.954905 L 81.58356,81.621661 Z"
        const val PIN_SVG_STEM_FILL: String = "#cad2d2"
        const val PIN_SVG_HEAD_FILL: String = "#e30000"
        const val PIN_SVG_VIEW_BOX: String = "0 0 320 320"
        const val PIN_SVG_SCALE: Double = 0.075
    }
}
