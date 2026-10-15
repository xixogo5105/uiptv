package com.uiptv.mobile.shared.epg

import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest

object AndroidEpgNameRules {
    fun read(db: SQLiteDatabase): MobileEpgNameRules {
        val rules=mutableListOf<MobileEpgNameRules.Rule>()
        db.rawQuery("SELECT term,scope FROM epg_name_exclusion WHERE enabled=1 ORDER BY term,scope",null).use { rows ->
            while (rows.moveToNext()) {
                val term=rows.getString(0); val scope=rows.getString(1)
                if (!term.isNullOrBlank() && term.length<=128 && scope in setOf("annotation","quality"))
                    rules += MobileEpgNameRules.Rule(term,scope)
            }
        }
        return MobileEpgNameRules(rules)
    }
    fun fingerprint(rules: MobileEpgNameRules) = MessageDigest.getInstance("SHA-256").digest(rules.canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
