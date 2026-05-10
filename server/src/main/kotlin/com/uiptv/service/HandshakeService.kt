package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.AccountInfo
import com.uiptv.model.AccountStatus
import com.uiptv.util.FetchAPI.fetch
import com.uiptv.util.FetchAPI.nullSafeString
import com.uiptv.util.StringUtils
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.util.Base64
import java.util.Date
import java.util.HashMap
import java.util.Locale
import java.util.UUID
import java.util.function.Consumer
import java.util.function.Supplier
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object HandshakeService {
    private const val PARAM_ACTION = "action"
    private const val PARAM_JS_HTTP_REQUEST = "JsHttpRequest"
    private const val PARAM_TOKEN = "token"
    private const val PASS_HASH_PREFIX = "pbkdf2_sha256"
    private const val PASS_HASH_ITERATIONS = 120_000
    private const val PASS_SALT_BYTES = 16
    private const val PASS_HASH_BYTES = 32
    private val PASS_RANDOM = SecureRandom()
    private const val DEFAULT_STB_TYPE = "MAG250"
    private const val DATE_ZERO = "0000-00-00 00:00:00"
    private const val MSG_UNABLE_RESOLVE_URL = "Unable to resolve server portal URL for account: "
    private const val MSG_UNABLE_TOKEN = "Unable to retrieve a token:\n\n"
    private const val KEY_ACCOUNT_INFO = "account_info"
    private const val KEY_TARIFF_NAME = "tariff_name"
    private const val KEY_TARIFF_PLAN = "tariff_plan"
    private const val KEY_DEFAULT_TIMEZONE = "default_timezone"
    private const val KEY_TIMEZONE = "timezone"
    private const val DATE_TIME_DASH_FORMAT = "yyyy-MM-dd HH:mm:ss"
    private const val DATE_TIME_SLASH_FORMAT = "yyyy/MM/dd HH:mm:ss"
    private const val DATE_TIME_US_FORMAT = "MM/dd/yyyy HH:mm:ss"
    private const val DATE_TIME_EU_FORMAT = "dd/MM/yyyy HH:mm:ss"
    private const val DATE_DASH_FORMAT = "yyyy-MM-dd"
    private const val DATE_SLASH_FORMAT = "yyyy/MM/dd"
    private const val DATE_US_FORMAT = "MM/dd/yyyy"
    private const val DATE_EU_FORMAT = "dd/MM/yyyy"
    private const val DATE_TIME_FULL_PATTERN = "MMMM d, yyyy, h:mm a"
    private const val DATE_TIME_ABBR_PATTERN = "MMM d, yyyy, h:mm a"

    private val EXTEND_AT_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern(DATE_TIME_DASH_FORMAT).withZone(ZoneOffset.UTC)
    private val CANONICAL_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_DASH_FORMAT)
    private val ACCOUNT_INFO_EXPIRY_DATE_TIME_FORMATTERS = listOf(
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(DATE_TIME_FULL_PATTERN).toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(DATE_TIME_ABBR_PATTERN).toFormatter(Locale.ENGLISH),
        DateTimeFormatter.ofPattern(DATE_TIME_DASH_FORMAT),
        DateTimeFormatter.ofPattern(DATE_TIME_SLASH_FORMAT),
        DateTimeFormatter.ofPattern(DATE_TIME_US_FORMAT),
        DateTimeFormatter.ofPattern(DATE_TIME_EU_FORMAT)
    )
    private val ACCOUNT_INFO_EXPIRY_DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern(DATE_DASH_FORMAT),
        DateTimeFormatter.ofPattern(DATE_SLASH_FORMAT),
        DateTimeFormatter.ofPattern(DATE_US_FORMAT),
        DateTimeFormatter.ofPattern(DATE_EU_FORMAT)
    )

    @JvmStatic
    fun getInstance(): HandshakeService = this

    private fun getHandshakeParams(): Map<String, String> {
        val params = HashMap<String, String>()
        params["type"] = "stb"
        params[PARAM_ACTION] = "handshake"
        params[PARAM_TOKEN] = ""
        params[PARAM_JS_HTTP_REQUEST] = Date().time.toString() + "-xml"
        return params
    }

    private fun getProfileParams(account: Account): Map<String, String> {
        val params = HashMap<String, String>()
        val stbType = resolveStbType(account)
        params["type"] = "stb"
        params[PARAM_ACTION] = "get_profile"
        params["hd"] = "1"
        params["ver"] = "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.9; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c"
        params["num_banks"] = "2"
        params["sn"] = account.serialNumber.orEmpty()
        params["stb_type"] = stbType
        params["client_type"] = "STB"
        params["image_version"] = "218"
        params["video_out"] = "hdmi"
        params["device_id"] = account.deviceId1.orEmpty()
        params["device_id2"] = account.deviceId2 ?: account.deviceId1.orEmpty()
        params["signature"] = if (isBlank(account.signature)) generateSerialNumber() else account.signature.orEmpty()
        params["auth_second_step"] = "1"
        params["hw_version"] = "1.7-BD-00"
        params["not_valid_token"] = "0"
        params["metrics"] = "{\"mac\":\"${account.macAddress}\",\"sn\":\"${account.serialNumber}\",\"type\":\"STB\",\"model\":\"$stbType\",\"uid\":\"\",\"random\":\"${generateRandom()}\"}"
        params["hw_version_2"] = generateRandom()
        params["api_signature"] = "262"
        params["prehash"] = ""
        params[PARAM_JS_HTTP_REQUEST] = Date().time.toString() + "-xml"
        return params
    }

    private fun getAccountParams(): Map<String, String> {
        val params = HashMap<String, String>()
        params["type"] = KEY_ACCOUNT_INFO
        params[PARAM_ACTION] = "get_main_info"
        params[PARAM_JS_HTTP_REQUEST] = Date().time.toString() + "-xml"
        return params
    }

    private fun generateSerialNumber(): String =
        (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").uppercase(Locale.ROOT)

    private fun generateRandom(): String =
        (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 39)
    fun connect(account: Account) {
        account.token = null
        AccountService.getInstance().syncSessionToken(account)
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            com.uiptv.util.AppLog.addWarningLog(HandshakeService::class.java, MSG_UNABLE_RESOLVE_URL + account.accountName)
            return
        }
        var json = fetch(getHandshakeParams(), account)
        account.token = parseJasonToken(json)
        AccountService.getInstance().syncSessionToken(account)
        if (account.isNotConnected()) {
            com.uiptv.util.AppLog.addWarningLog(HandshakeService::class.java, MSG_UNABLE_TOKEN + json)
            return
        }
        json = fetch(getProfileParams(account), account)
        val info = resolveAccountInfo(account)
        var updated = applyProfileDetails(info, json)
        if (isNotBlank(json)) {
            val accountInfoJson = fetch(getAccountParams(), account)
            updated = applyAccountInfoDetails(info, accountInfoJson) || updated
        }
        if (updated) persistAccountInfo(info)
    }
    fun fetchAccountInfo(account: Account?): AccountInfo? {
        if (account == null) return null
        account.token = null
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            com.uiptv.util.AppLog.addWarningLog(HandshakeService::class.java, MSG_UNABLE_RESOLVE_URL + account.accountName)
            return null
        }
        var json = fetch(getHandshakeParams(), account)
        account.token = parseJasonToken(json)
        if (account.isNotConnected()) {
            com.uiptv.util.AppLog.addWarningLog(HandshakeService::class.java, MSG_UNABLE_TOKEN + json)
            return null
        }
        json = fetch(getProfileParams(account), account)
        val info = createTransientAccountInfo(account)
        var updated = applyProfileDetails(info, json)
        if (isNotBlank(json)) {
            val accountInfoJson = fetch(getAccountParams(), account)
            updated = applyAccountInfoDetails(info, accountInfoJson) || updated
        }
        return if (updated) info else null
    }
    fun hardTokenRefresh(account: Account) {
        account.token = null
        AccountService.getInstance().syncSessionToken(account)
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            com.uiptv.util.AppLog.addWarningLog(HandshakeService::class.java, MSG_UNABLE_RESOLVE_URL + account.accountName)
            return
        }
        var json = fetch(getHandshakeParams(), account)
        account.token = parseJasonToken(json)
        AccountService.getInstance().syncSessionToken(account)
        if (account.isNotConnected()) {
            com.uiptv.util.AppLog.addWarningLog(HandshakeService::class.java, MSG_UNABLE_TOKEN + json)
        }
        json = fetch(getProfileParams(account), account)
        val info = resolveAccountInfo(account)
        var updated = applyProfileDetails(info, json)
        if (isNotBlank(json)) {
            val accountInfoJson = fetch(getAccountParams(), account)
            updated = applyAccountInfoDetails(info, accountInfoJson) || updated
        }
        if (updated) persistAccountInfo(info)
    }
    fun parseJasonToken(json: String?): String {
        if (isBlank(json)) {
            com.uiptv.util.AppLog.addErrorLog(HandshakeService::class.java, "Error while establishing connection to server")
            return StringUtils.EMPTY
        }
        return try {
            val token = JSONObject(json).getJSONObject("js").getString(PARAM_TOKEN)
            if (isBlank(token)) {
                com.uiptv.util.AppLog.addErrorLog(HandshakeService::class.java, "Error while establishing connection to server")
                StringUtils.EMPTY
            } else {
                token
            }
        } catch (_: Exception) {
            com.uiptv.util.AppLog.addErrorLog(HandshakeService::class.java, "Error while establishing connection to server")
            StringUtils.EMPTY
        }
    }

    private fun applyProfileDetails(info: AccountInfo?, json: String?): Boolean {
        val js = parsePortalResponse(json) ?: return false
        if (info == null) return false
        var updated = false
        updated = updateIfNotBlank(json, Supplier { info.profileJson }, Consumer { info.profileJson = it }) || updated
        val expireDate = deriveExpiryDate(js)
        updated = updateIfNotBlank(expireDate, Supplier { info.expireDate }, Consumer { info.expireDate = it }) || updated
        val derivedStatus = deriveAccountStatus(js)
        updated = updateIfNotBlank(derivedStatus, Supplier { info.accountStatus }, Consumer { info.accountStatus = it }) || updated
        updated = updateIfNotBlank(
            firstNonBlank(nullSafeString(js, KEY_TARIFF_PLAN), nullSafeString(js, "tariff_plan_id")),
            Supplier { info.tariffPlan },
            Consumer { info.tariffPlan = it }
        ) || updated
        updated = updateIfNotBlank(nullSafeString(js, KEY_TARIFF_NAME), Supplier { info.tariffName }, Consumer { info.tariffName = it }) || updated
        updated = updateIfNotBlank(
            firstNonBlank(nullSafeString(js, KEY_DEFAULT_TIMEZONE), nullSafeString(js, KEY_TIMEZONE)),
            Supplier { info.defaultTimezone },
            Consumer { info.defaultTimezone = it }
        ) || updated
        updated = applyAllowedStbTypes(info, js) || updated
        updated = applyPasswordHashes(info, js) || updated
        return updated
    }

    private fun applyAccountInfoDetails(info: AccountInfo?, json: String?): Boolean {
        val js = parsePortalResponse(json) ?: return false
        if (info == null) return false
        val accountInfoJson = js.optJSONObject(KEY_ACCOUNT_INFO) ?: js
        var updated = false
        val balance = firstNonBlank(
            nullSafeString(accountInfoJson, KEY_ACCOUNT_INFO),
            nullSafeString(accountInfoJson, "account_balance"),
            nullSafeString(accountInfoJson, "balance"),
            nullSafeString(js, KEY_ACCOUNT_INFO),
            nullSafeString(js, "account_balance"),
            nullSafeString(js, "balance")
        )
        updated = updateIfNotBlank(balance, Supplier { info.accountBalance }, Consumer { info.accountBalance = it }) || updated
        updated = updateIfNotBlank(
            firstNonBlank(nullSafeString(accountInfoJson, KEY_TARIFF_NAME), nullSafeString(js, KEY_TARIFF_NAME)),
            Supplier { info.tariffName },
            Consumer { info.tariffName = it }
        ) || updated
        updated = updateIfNotBlank(
            firstNonBlank(nullSafeString(accountInfoJson, KEY_TARIFF_PLAN), nullSafeString(js, KEY_TARIFF_PLAN)),
            Supplier { info.tariffPlan },
            Consumer { info.tariffPlan = it }
        ) || updated
        updated = updateIfNotBlank(
            firstNonBlank(
                nullSafeString(accountInfoJson, KEY_DEFAULT_TIMEZONE),
                nullSafeString(js, KEY_DEFAULT_TIMEZONE),
                nullSafeString(accountInfoJson, KEY_TIMEZONE),
                nullSafeString(js, KEY_TIMEZONE)
            ),
            Supplier { info.defaultTimezone },
            Consumer { info.defaultTimezone = it }
        ) || updated
        updated = updateIfNotBlank(
            firstAccountInfoExpiry(accountInfoJson, js, info.expireDate),
            Supplier { info.expireDate },
            Consumer { info.expireDate = it }
        ) || updated
        updated = applyAllowedStbTypes(info, accountInfoJson) || updated
        if (accountInfoJson !== js) updated = applyAllowedStbTypes(info, js) || updated
        updated = applyPasswordHashes(info, accountInfoJson) || updated
        if (accountInfoJson !== js) updated = applyPasswordHashes(info, js) || updated
        return updated
    }

    private fun parsePortalResponse(json: String?): JSONObject? {
        if (isBlank(json)) return null
        return try {
            val root = JSONObject(json)
            root.optJSONObject("js") ?: root
        } catch (_: Exception) {
            null
        }
    }

    private fun updateIfNotBlank(value: String?, currentValue: Supplier<String?>, setter: Consumer<String?>): Boolean {
        if (isBlank(value)) return false
        val current = currentValue.get()
        if (value == current) return false
        setter.accept(value)
        return true
    }

    private fun applyPasswordHashes(info: AccountInfo?, json: JSONObject?): Boolean {
        if (info == null || json == null) return false
        var updated = false
        updated = updatePasswordHash(nullSafeString(json, "pass"), Supplier { info.passHash }, Consumer { info.passHash = it }) || updated
        updated = updatePasswordHash(nullSafeString(json, "parent_password"), Supplier { info.parentPasswordHash }, Consumer { info.parentPasswordHash = it }) || updated
        updated = updatePasswordHash(nullSafeString(json, "password"), Supplier { info.passwordHash }, Consumer { info.passwordHash = it }) || updated
        updated = updatePasswordHash(nullSafeString(json, "settings_password"), Supplier { info.settingsPasswordHash }, Consumer { info.settingsPasswordHash = it }) || updated
        updated = updatePasswordHash(nullSafeString(json, "account_page_by_password"), Supplier { info.accountPagePasswordHash }, Consumer { info.accountPagePasswordHash = it }) || updated
        return updated
    }

    private fun updatePasswordHash(rawValue: String?, currentValue: Supplier<String?>, setter: Consumer<String?>): Boolean {
        if (isBlank(rawValue)) return false
        val existing = currentValue.get()
        if (isNotBlank(existing) && verifyPassword(rawValue, existing)) return false
        val hashed = hashPassword(rawValue)
        if (isBlank(hashed)) return false
        setter.accept(hashed)
        return true
    }

    private fun deriveAccountStatus(js: JSONObject): AccountStatus =
        if (isTruthy(nullSafeString(js, "blocked"))) AccountStatus.SUSPENDED else AccountStatus.ACTIVE

    private fun isTruthy(value: String?): Boolean {
        if (isBlank(value)) return false
        return when (value!!.trim().lowercase()) {
            "1", "true", "yes" -> true
            else -> false
        }
    }

    private fun deriveExpiryDate(js: JSONObject): String {
        val tariffExpired = normalizeExpiry(nullSafeString(js, "tariff_expired_date"))
        if (isNotBlank(tariffExpired)) return tariffExpired
        val expireBilling = normalizeExpiry(nullSafeString(js, "expire_billing_date"))
        if (isNotBlank(expireBilling)) return expireBilling
        val extendResolved = resolveExtendAt(nullSafeString(js, "extend_at"))
        if (isNotBlank(extendResolved)) return extendResolved
        return ""
    }

    private fun firstAccountInfoExpiry(accountInfoJson: JSONObject, rootJson: JSONObject, existingExpiry: String?): String {
        if (isNotBlank(normalizeExpiry(existingExpiry))) return ""
        val candidates = arrayOf(
            normalizeAccountInfoExpiry(nullSafeString(accountInfoJson, "end_date")),
            normalizeAccountInfoExpiry(nullSafeString(rootJson, "end_date")),
            normalizeAccountInfoExpiry(nullSafeString(accountInfoJson, "phone")),
            normalizeAccountInfoExpiry(nullSafeString(rootJson, "phone"))
        )
        return candidates.firstOrNull { isNotBlank(it) } ?: ""
    }

    private fun normalizeExpiry(value: String?): String {
        if (isBlank(value)) return ""
        val trimmed = value!!.trim()
        if (trimmed == DATE_ZERO || trimmed == "0") return ""
        return trimmed
    }

    private fun normalizeAccountInfoExpiry(value: String?): String {
        val normalized = normalizeExpiry(value)
        if (isBlank(normalized)) return ""
        ACCOUNT_INFO_EXPIRY_DATE_TIME_FORMATTERS.forEach { formatter ->
            try {
                val dateTime = LocalDateTime.parse(normalized, formatter)
                return CANONICAL_DATE_TIME_FORMATTER.format(dateTime)
            } catch (_: DateTimeParseException) {
            }
        }
        ACCOUNT_INFO_EXPIRY_DATE_FORMATTERS.forEach { formatter ->
            try {
                val date = LocalDate.parse(normalized, formatter)
                return CANONICAL_DATE_TIME_FORMATTER.format(date.atStartOfDay())
            } catch (_: DateTimeParseException) {
            }
        }
        return ""
    }

    private fun resolveExtendAt(value: String?): String {
        if (isBlank(value)) return ""
        return try {
            val epochSeconds = value!!.trim().toLong()
            if (epochSeconds <= 0) "" else EXTEND_AT_FORMATTER.format(Instant.ofEpochSecond(epochSeconds))
        } catch (_: NumberFormatException) {
            ""
        }
    }

    private fun applyAllowedStbTypes(info: AccountInfo?, json: JSONObject?): Boolean {
        if (info == null || json == null) return false
        var updated = false
        val allowed = normalizeArray(json.optJSONArray("allowed_stb_types"))
        val allowedRecording = normalizeArray(json.optJSONArray("allowed_stb_types_for_local_recording"))
        updated = updateIfNotBlank(allowed, Supplier { info.allowedStbTypesJson }, Consumer { info.allowedStbTypesJson = it }) || updated
        updated = updateIfNotBlank(allowedRecording, Supplier { info.allowedStbTypesForLocalRecordingJson }, Consumer { info.allowedStbTypesForLocalRecordingJson = it }) || updated
        if (isNotBlank(allowed)) {
            val preferred = selectPreferredStbType(allowed)
            updated = updatePreferredStbType(preferred, Supplier { info.preferredStbType }, Consumer { info.preferredStbType = it }) || updated
        }
        return updated
    }

    private fun updateIfNotBlank(value: AccountStatus?, currentValue: Supplier<AccountStatus?>, setter: Consumer<AccountStatus?>): Boolean {
        if (value == null) return false
        if (value == currentValue.get()) return false
        setter.accept(value)
        return true
    }
    fun resolveStbType(account: Account?): String {
        if (account == null || isBlank(account.dbId)) return DEFAULT_STB_TYPE
        val info = AccountInfoService.getInstance().getByAccountId(account.dbId)
        if (info == null) return DEFAULT_STB_TYPE
        val preferred = info.preferredStbType
        if (isNotBlank(preferred)) return preferred!!
        val allowed = info.allowedStbTypesJson
        if (isNotBlank(allowed)) {
            return if (allowedContainsMag250(allowed)) DEFAULT_STB_TYPE else firstAllowedStbType(allowed, DEFAULT_STB_TYPE)
        }
        return DEFAULT_STB_TYPE
    }
    fun selectPreferredStbType(allowedJson: String?): String {
        if (isBlank(allowedJson)) return ""
        return try {
            val array = JSONArray(allowedJson)
            if (array.length() == 0) return ""
            var hasMag = false
            for (i in 0 until array.length()) {
                if (array.optString(i, "").equals("mag250", true)) {
                    hasMag = true
                    break
                }
            }
            if (hasMag) "" else array.optString(0, "").takeIf { isNotBlank(it) } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun updatePreferredStbType(preferred: String?, currentValue: Supplier<String?>, setter: Consumer<String?>): Boolean {
        val current = currentValue.get().orEmpty()
        if (isBlank(preferred)) {
            if (isBlank(current)) return false
            setter.accept("")
            return true
        }
        if (preferred == current) return false
        setter.accept(preferred)
        return true
    }
    fun allowedContainsMag250(allowedJson: String?): Boolean {
        if (isBlank(allowedJson)) return false
        return try {
            val array = JSONArray(allowedJson)
            for (i in 0 until array.length()) {
                if (array.optString(i, "").equals("mag250", true)) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }
    fun firstAllowedStbType(allowedJson: String?, fallback: String): String {
        if (isBlank(allowedJson)) return fallback
        return try {
            val array = JSONArray(allowedJson)
            array.optString(0, "").takeIf { isNotBlank(it) } ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun normalizeArray(array: JSONArray?): String =
        if (array == null || array.length() == 0) "" else array.toString()

    private fun hashPassword(rawValue: String?): String {
        if (isBlank(rawValue)) return ""
        return try {
            val salt = ByteArray(PASS_SALT_BYTES)
            PASS_RANDOM.nextBytes(salt)
            val hash = pbkdf2(rawValue!!.toCharArray(), salt, PASS_HASH_ITERATIONS, PASS_HASH_BYTES)
            PASS_HASH_PREFIX + "$" + PASS_HASH_ITERATIONS + "$" +
                Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(hash)
        } catch (_: java.security.GeneralSecurityException) {
            ""
        }
    }

    private fun verifyPassword(rawValue: String?, storedHash: String?): Boolean {
        if (isBlank(rawValue) || isBlank(storedHash)) return false
        val parts = storedHash!!.split("\\$".toRegex())
        if (parts.size != 4 || parts[0] != PASS_HASH_PREFIX) return false
        val iterations = try {
            parts[1].toInt()
        } catch (_: NumberFormatException) {
            return false
        }
        return try {
            val salt = Base64.getDecoder().decode(parts[2])
            val expectedHash = Base64.getDecoder().decode(parts[3])
            val actualHash = pbkdf2(rawValue!!.toCharArray(), salt, iterations, expectedHash.size)
            slowEquals(expectedHash, actualHash)
        } catch (_: java.security.GeneralSecurityException) {
            false
        }
    }

    private fun pbkdf2(value: CharArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
        val spec = PBEKeySpec(value, salt, iterations, length * 8)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun slowEquals(left: ByteArray?, right: ByteArray?): Boolean {
        if (left == null || right == null) return false
        var diff = left.size xor right.size
        val max = maxOf(left.size, right.size)
        for (i in 0 until max) {
            val a: Byte = if (i < left.size) left[i] else 0
            val b: Byte = if (i < right.size) right[i] else 0
            diff = diff or (a.toInt() xor b.toInt())
        }
        return diff == 0
    }

    private fun firstNonBlank(vararg values: String?): String {
        values.forEach { value -> if (isNotBlank(value)) return value!! }
        return ""
    }

    private fun resolveAccountInfo(account: Account?): AccountInfo? {
        if (account == null || isBlank(account.dbId)) return null
        val existing = AccountInfoService.getInstance().getByAccountId(account.dbId)
        if (existing == null) {
            return AccountInfo(accountId = account.dbId)
        }
        existing.accountId = account.dbId
        return existing
    }

    private fun persistAccountInfo(info: AccountInfo?) {
        if (info == null || isBlank(info.accountId)) return
        AccountInfoService.getInstance().save(info)
    }

    private fun createTransientAccountInfo(account: Account?): AccountInfo {
        val info = AccountInfo()
        if (account != null && isNotBlank(account.dbId)) {
            info.accountId = account.dbId
        }
        return info
    }
}
