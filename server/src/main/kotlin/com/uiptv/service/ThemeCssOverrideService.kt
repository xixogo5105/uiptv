package com.uiptv.service

import com.uiptv.db.ThemeCssOverrideDb
import com.uiptv.model.ThemeCssOverride

object ThemeCssOverrideService {
    @JvmStatic
    fun getInstance(): ThemeCssOverrideService = this
    fun read(): ThemeCssOverride? = ThemeCssOverrideDb.get().read()
    fun save(override: ThemeCssOverride) {
        ThemeCssOverrideDb.get().save(override)
    }
}
