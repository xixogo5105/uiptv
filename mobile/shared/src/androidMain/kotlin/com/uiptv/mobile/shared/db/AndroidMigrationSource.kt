package com.uiptv.mobile.shared.db

import android.content.Context
import java.nio.charset.StandardCharsets

class AndroidMigrationSource(private val context: Context) {
    fun migrationNames(): List<String> =
        UiptvMigrationSql.parseMigrationNames(readAsset(UiptvSchemaInfo.MIGRATIONS_LIST))

    fun migrationSql(name: String): String =
        readAsset("${UiptvSchemaInfo.MIGRATIONS_DIR}/$name")

    private fun readAsset(path: String): String =
        context.assets.open(path).use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        }
}
