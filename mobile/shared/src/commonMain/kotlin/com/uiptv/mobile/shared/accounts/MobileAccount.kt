package com.uiptv.mobile.shared.accounts

enum class MobileAccountType(val displayName: String, val cacheRefreshSupported: Boolean) {
    STALKER_PORTAL("Stalker Portal", true),
    XTREME_API("Xtreme API", true),
    M3U8_URL("M3U Playlist", true),
    M3U8_LOCAL("M3U Playlist", true)
}

data class MobileAccount(
    val id: Long? = null,
    val accountName: String = "",
    val type: MobileAccountType = MobileAccountType.STALKER_PORTAL,
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val xtremeCredentialsJson: String = "",
    val macAddress: String = "",
    val macAddressList: String = "",
    val serialNumber: String = "",
    val deviceId1: String = "",
    val deviceId2: String = "",
    val signature: String = "",
    val epg: String = "",
    val m3u8Path: String = "",
    val serverPortalUrl: String = "",
    val pinToTop: Boolean = false,
    val resolveChainAndDeepRedirects: Boolean = false,
    val httpMethod: String = "GET",
    val timezone: String = "Europe/London"
) {
    fun normalizedForSave(): MobileAccount {
        val trimmedUrl = url.trim()
        val trimmedM3u8Path = m3u8Path.trim()
        val m3uRemoteUrl = if (type.isM3uPlaylistType()) {
            trimmedUrl.ifBlank { trimmedM3u8Path.takeIf { it.isRemotePlaylistUrl() }.orEmpty() }
        } else {
            ""
        }
        val normalizedType = when {
            type.isM3uPlaylistType() && m3uRemoteUrl.isNotBlank() -> MobileAccountType.M3U8_URL
            type.isM3uPlaylistType() -> MobileAccountType.M3U8_LOCAL
            else -> type
        }
        val normalizedUrl = when {
            normalizedType == MobileAccountType.STALKER_PORTAL &&
                trimmedUrl.isNotBlank() &&
                !trimmedUrl.endsWith("/") -> "$trimmedUrl/"
            normalizedType == MobileAccountType.M3U8_URL -> m3uRemoteUrl
            normalizedType == MobileAccountType.M3U8_LOCAL -> ""
            else -> trimmedUrl
        }
        val normalizedM3u8Path = when (normalizedType) {
            MobileAccountType.M3U8_URL -> ""
            MobileAccountType.M3U8_LOCAL -> trimmedM3u8Path
            else -> trimmedM3u8Path
        }
        val normalizedMacs = if (normalizedType == MobileAccountType.STALKER_PORTAL) {
            normalizeMacAddressEntries(listOf(macAddress, macAddressList))
        } else {
            emptyList()
        }
        val normalizedMac = normalizedMacs.firstOrNull().orEmpty()
        val normalizedXtremeCredentials = if (normalizedType == MobileAccountType.XTREME_API) {
            val parsedCredentials = parseXtremeCredentialsJson(xtremeCredentialsJson).toMutableList()
            val selectedUsername = username.trim()
            if (parsedCredentials.isEmpty() && selectedUsername.isNotBlank() && password.isNotBlank()) {
                parsedCredentials += XtremeCredential(selectedUsername, password, isDefault = true)
            } else if (selectedUsername.isNotBlank() && password.isNotBlank()) {
                val exists = parsedCredentials.any { it.username == selectedUsername && it.password == password }
                if (!exists) {
                    parsedCredentials += XtremeCredential(selectedUsername, password, isDefault = parsedCredentials.isEmpty())
                }
            }
            normalizeXtremeCredentials(
                entries = parsedCredentials,
                defaultUsername = selectedUsername,
                defaultPassword = password.takeIf { selectedUsername.isNotBlank() && it.isNotBlank() }
            )
        } else {
            emptyList()
        }
        val defaultXtremeCredential = resolveDefaultXtremeCredential(normalizedXtremeCredentials)
        return copy(
            type = normalizedType,
            accountName = accountName.trim(),
            url = normalizedUrl,
            username = defaultXtremeCredential?.username ?: username.trim(),
            password = defaultXtremeCredential?.password ?: password,
            macAddress = normalizedMac,
            macAddressList = normalizedMacs.joinToString(","),
            xtremeCredentialsJson = if (normalizedType == MobileAccountType.XTREME_API) {
                xtremeCredentialsToJson(normalizedXtremeCredentials)
            } else {
                xtremeCredentialsJson
            },
            m3u8Path = normalizedM3u8Path,
            httpMethod = httpMethod.ifBlank { "GET" }.trim().uppercase(),
            timezone = timezone.ifBlank { "Europe/London" }.trim()
        )
    }

    val canRefreshCache: Boolean
        get() = type.cacheRefreshSupported

    private fun normalizeMacAddressEntries(values: Iterable<String>): List<String> =
        values
            .flatMap { it.split(',', ';') }
            .map { it.filterNot { char -> char.isWhitespace() } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
}

private fun MobileAccountType.isM3uPlaylistType(): Boolean =
    this == MobileAccountType.M3U8_URL || this == MobileAccountType.M3U8_LOCAL

private fun String.isRemotePlaylistUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

data class XtremeCredential(
    val username: String,
    val password: String,
    val isDefault: Boolean = false
)

fun MobileAccount.xtremeCredentialOptions(): List<XtremeCredential> {
    val entries = parseXtremeCredentialsJson(xtremeCredentialsJson).toMutableList()
    val selectedUsername = username.trim()
    if (entries.isEmpty() && selectedUsername.isNotBlank() && password.isNotBlank()) {
        entries += XtremeCredential(selectedUsername, password, isDefault = true)
    }
    return normalizeXtremeCredentials(
        entries = entries,
        defaultUsername = selectedUsername,
        defaultPassword = password.takeIf { selectedUsername.isNotBlank() && it.isNotBlank() }
    )
}

fun MobileAccount.withXtremeCredentialSelection(credential: XtremeCredential): MobileAccount {
    val entries = xtremeCredentialOptions().toMutableList()
    val exists = entries.any { it.username == credential.username && it.password == credential.password }
    if (!exists) {
        entries += credential
    }
    val normalized = normalizeXtremeCredentials(
        entries = entries,
        defaultUsername = credential.username,
        defaultPassword = credential.password
    )
    return copy(
        username = credential.username,
        password = credential.password,
        xtremeCredentialsJson = xtremeCredentialsToJson(normalized)
    )
}

private fun parseXtremeCredentialsJson(rawJson: String): List<XtremeCredential> {
    if (rawJson.isBlank()) {
        return emptyList()
    }
    return runCatching {
        parseJsonObjects(rawJson.trim()).mapNotNull { fields ->
            val username = fields["username"]?.trim().orEmpty()
            val password = fields["password"].orEmpty()
            if (username.isNotBlank() && password.isNotBlank()) {
                XtremeCredential(
                    username = username,
                    password = password,
                    isDefault = fields["default"].equals("true", ignoreCase = true)
                )
            } else {
                null
            }
        }
    }.getOrDefault(emptyList()).let { normalizeXtremeCredentials(it) }
}

private fun normalizeXtremeCredentials(
    entries: List<XtremeCredential>,
    defaultUsername: String? = null,
    defaultPassword: String? = null
): List<XtremeCredential> {
    if (entries.isEmpty()) {
        return emptyList()
    }
    val unique = linkedMapOf<String, XtremeCredential>()
    entries.forEach { entry ->
        val username = entry.username.trim()
        val password = entry.password
        if (username.isNotBlank() && password.isNotBlank()) {
            unique.putIfAbsent("$username\u0000$password", XtremeCredential(username, password, entry.isDefault))
        }
    }
    if (unique.isEmpty()) {
        return emptyList()
    }
    val selectedUsername = defaultUsername?.trim().orEmpty()
    val selectedPassword = defaultPassword.orEmpty()
    var defaultAssigned = false
    val normalized = unique.values.map { entry ->
        val selectedExact = selectedUsername.isNotBlank() &&
            selectedPassword.isNotBlank() &&
            entry.username == selectedUsername &&
            entry.password == selectedPassword
        val selectedUser = selectedPassword.isBlank() &&
            selectedUsername.isNotBlank() &&
            entry.username == selectedUsername
        val keepExistingDefault = selectedUsername.isBlank() && entry.isDefault
        val isDefault = !defaultAssigned && (selectedExact || selectedUser || keepExistingDefault)
        if (isDefault) {
            defaultAssigned = true
        }
        entry.copy(isDefault = isDefault)
    }
    if (defaultAssigned) {
        return normalized
    }
    return normalized.mapIndexed { index, entry -> entry.copy(isDefault = index == 0) }
}

private fun resolveDefaultXtremeCredential(entries: List<XtremeCredential>): XtremeCredential? =
    entries.firstOrNull { it.isDefault } ?: entries.firstOrNull()

private fun xtremeCredentialsToJson(entries: List<XtremeCredential>): String {
    val normalized = normalizeXtremeCredentials(entries)
    if (normalized.isEmpty()) {
        return ""
    }
    return normalized.joinToString(separator = ",", prefix = "[", postfix = "]") { entry ->
        buildString {
            append("{\"username\":\"")
            append(entry.username.jsonEscaped())
            append("\",\"password\":\"")
            append(entry.password.jsonEscaped())
            append("\"")
            if (entry.isDefault) {
                append(",\"default\":true")
            }
            append("}")
        }
    }
}

private fun parseJsonObjects(json: String): List<Map<String, String>> {
    val objects = mutableListOf<Map<String, String>>()
    var index = json.indexOf('[').takeIf { it >= 0 }?.plus(1) ?: 0
    while (index < json.length) {
        index = json.indexOf('{', index)
        if (index < 0) {
            break
        }
        val objectEnd = findJsonObjectEnd(json, index)
        if (objectEnd <= index) {
            break
        }
        objects += parseJsonObjectFields(json.substring(index + 1, objectEnd))
        index = objectEnd + 1
    }
    return objects
}

private fun findJsonObjectEnd(json: String, start: Int): Int {
    var inString = false
    var escaped = false
    for (index in start + 1 until json.length) {
        val char = json[index]
        when {
            escaped -> escaped = false
            char == '\\' && inString -> escaped = true
            char == '"' -> inString = !inString
            char == '}' && !inString -> return index
        }
    }
    return -1
}

private fun parseJsonObjectFields(body: String): Map<String, String> {
    val fields = linkedMapOf<String, String>()
    var index = 0
    while (index < body.length) {
        index = body.skipWhitespaceAndCommas(index)
        if (index >= body.length || body[index] != '"') {
            break
        }
        val keyResult = body.readJsonString(index)
        index = body.skipWhitespace(keyResult.nextIndex)
        if (index >= body.length || body[index] != ':') {
            break
        }
        index = body.skipWhitespace(index + 1)
        if (index >= body.length) {
            break
        }
        val valueResult = if (body[index] == '"') {
            body.readJsonString(index)
        } else {
            body.readJsonLiteral(index)
        }
        fields[keyResult.value] = valueResult.value
        index = valueResult.nextIndex
    }
    return fields
}

private data class JsonReadResult(val value: String, val nextIndex: Int)

private fun String.skipWhitespace(index: Int): Int {
    var cursor = index
    while (cursor < length && this[cursor].isWhitespace()) {
        cursor++
    }
    return cursor
}

private fun String.skipWhitespaceAndCommas(index: Int): Int {
    var cursor = index
    while (cursor < length && (this[cursor].isWhitespace() || this[cursor] == ',')) {
        cursor++
    }
    return cursor
}

private fun String.readJsonLiteral(index: Int): JsonReadResult {
    var cursor = index
    while (cursor < length && this[cursor] != ',' && this[cursor] != '}') {
        cursor++
    }
    return JsonReadResult(substring(index, cursor).trim(), cursor)
}

private fun String.readJsonString(index: Int): JsonReadResult {
    val value = StringBuilder()
    var cursor = index + 1
    while (cursor < length) {
        val char = this[cursor]
        when (char) {
            '"' -> return JsonReadResult(value.toString(), cursor + 1)
            '\\' -> {
                cursor++
                if (cursor >= length) {
                    break
                }
                when (val escaped = this[cursor]) {
                    '"', '\\', '/' -> value.append(escaped)
                    'b' -> value.append('\b')
                    'f' -> value.append('\u000C')
                    'n' -> value.append('\n')
                    'r' -> value.append('\r')
                    't' -> value.append('\t')
                    'u' -> {
                        val hex = substring(cursor + 1, (cursor + 5).coerceAtMost(length))
                        if (hex.length == 4) {
                            hex.toIntOrNull(16)?.let { value.append(it.toChar()) }
                            cursor += 4
                        }
                    }
                    else -> value.append(escaped)
                }
            }
            else -> value.append(char)
        }
        cursor++
    }
    return JsonReadResult(value.toString(), cursor)
}

private fun String.jsonEscaped(): String =
    buildString {
        this@jsonEscaped.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

data class AccountCacheSummary(
    val liveCategories: Int = 0,
    val liveChannels: Int = 0,
    val vodCategories: Int = 0,
    val vodChannels: Int = 0,
    val seriesCategories: Int = 0,
    val seriesChannels: Int = 0,
    val seriesEpisodes: Int = 0
) {
    val totalItems: Int
        get() = liveCategories + liveChannels + vodCategories + vodChannels +
            seriesCategories + seriesChannels + seriesEpisodes
}

interface AccountRepository {
    suspend fun listAccounts(): List<MobileAccount>

    suspend fun saveAccount(account: MobileAccount): MobileAccount

    suspend fun deleteAccount(accountId: Long)

    suspend fun clearAccountCache(accountId: Long): AccountCacheSummary

    suspend fun clearAllCache(): AccountCacheSummary

    suspend fun cacheSummary(accountId: Long): AccountCacheSummary
}
