package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountStatus;
import com.uiptv.service.AccountInfoService;
import com.uiptv.util.StringUtils;
import javafx.application.Platform;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.uiptv.util.FetchAPI.fetch;
import static com.uiptv.util.FetchAPI.nullSafeString;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;


public class HandshakeService {
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_JS_HTTP_REQUEST = "JsHttpRequest";
    private static final String PARAM_TOKEN = "token";
    private static final String PASS_HASH_PREFIX = "pbkdf2_sha256";
    private static final int PASS_HASH_ITERATIONS = 120_000;
    private static final int PASS_SALT_BYTES = 16;
    private static final int PASS_HASH_BYTES = 32;
    private static final SecureRandom PASS_RANDOM = new SecureRandom();
    private static final String DEFAULT_STB_TYPE = "MAG250";
    private static final String DATE_ZERO = "0000-00-00 00:00:00";
    private static final String MSG_UNABLE_RESOLVE_URL = "Unable to resolve server portal URL for account: ";
    private static final String MSG_UNABLE_TOKEN = "Unable to retrieve a token:\n\n";
    private static final String KEY_ACCOUNT_INFO = "account_info";
    private static final String KEY_TARIFF_NAME = "tariff_name";
    private static final String KEY_TARIFF_PLAN = "tariff_plan";
    private static final String KEY_DEFAULT_TIMEZONE = "default_timezone";
    private static final String KEY_TIMEZONE = "timezone";
    private static final DateTimeFormatter EXTEND_AT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private HandshakeService() {
    }

    private static class SingletonHelper {
        private static final HandshakeService INSTANCE = new HandshakeService();
    }

    public static HandshakeService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    private static Map<String, String> getHandshakeParams() {
        final Map<String, String> params = new HashMap<>();
        params.put("type", "stb");
        params.put(PARAM_ACTION, "handshake");
        params.put(PARAM_TOKEN, "");
        params.put(PARAM_JS_HTTP_REQUEST, new Date().getTime() + "-xml");
        return params;
    }

    private static Map<String, String> getProfileParams(Account c) {
        final Map<String, String> params = new HashMap<>();
        String stbType = resolveStbType(c);
        params.put("type", "stb");
        params.put(PARAM_ACTION, "get_profile");
        params.put("hd", "1");
        params.put("ver", "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.9; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c");
        params.put("num_banks", "2");
        params.put("sn", c.getSerialNumber());
        params.put("stb_type", stbType);
        params.put("client_type", "STB");
        params.put("image_version", "218");
        params.put("video_out", "hdmi");
        params.put("device_id", c.getDeviceId1());
        params.put("device_id2", c.getDeviceId2() == null ? c.getDeviceId1() : c.getDeviceId2());
        params.put("signature", isBlank(c.getSignature()) ? generateSerialNumber() : c.getSignature());
        params.put("auth_second_step", "1");
        params.put("hw_version", "1.7-BD-00");
        params.put("not_valid_token", "0");
        params.put("metrics", "{\"mac\":\"" + c.getMacAddress() + "\",\"sn\":\"" + c.getSerialNumber() + "\",\"type\":\"STB\",\"model\":\"" + stbType + "\",\"uid\":\"\",\"random\":\"" + generateRandom() + "\"}");
        params.put("hw_version_2", generateRandom());
        params.put("api_signature", "262");
        params.put("prehash", "");
        params.put(PARAM_JS_HTTP_REQUEST, new Date().getTime() + "-xml");


        return params;
    }

    private static Map<String, String> getAccountParams() {
        final Map<String, String> params = new HashMap<>();
        params.put("type", KEY_ACCOUNT_INFO);
        params.put(PARAM_ACTION, "get_main_info");
        params.put(PARAM_JS_HTTP_REQUEST, new Date().getTime() + "-xml");
        return params;
    }

    private static String generateSerialNumber() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").toUpperCase();
    }

    private static String generateRandom() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 39);
    }

    public void connect(Account account) {
        account.setToken(null);
        AccountService.getInstance().syncSessionToken(account);
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addWarningLog(HandshakeService.class, MSG_UNABLE_RESOLVE_URL + account.getAccountName()));
            return;
        }
        String json = fetch(getHandshakeParams(), account);
        account.setToken(parseJasonToken(json));
        AccountService.getInstance().syncSessionToken(account);
        if (account.isNotConnected()) {
            String finalJson = json;
            Platform.runLater(() -> com.uiptv.util.AppLog.addWarningLog(HandshakeService.class, MSG_UNABLE_TOKEN + finalJson));
            return;
        }
        json = fetch(getProfileParams(account), account);
        AccountInfo info = resolveAccountInfo(account);
        boolean updated = applyProfileDetails(info, json);
        if (isNotBlank(json)) {
            String accountInfoJson = fetch(getAccountParams(), account);
            updated = applyAccountInfoDetails(info, accountInfoJson) || updated;
        }
        if (updated) {
            persistAccountInfo(info);
        }
    }

    public AccountInfo fetchAccountInfo(Account account) {
        if (account == null) {
            return null;
        }
        account.setToken(null);
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addWarningLog(HandshakeService.class, MSG_UNABLE_RESOLVE_URL + account.getAccountName()));
            return null;
        }
        String json = fetch(getHandshakeParams(), account);
        account.setToken(parseJasonToken(json));
        if (account.isNotConnected()) {
            String finalJson = json;
            Platform.runLater(() -> com.uiptv.util.AppLog.addWarningLog(HandshakeService.class, MSG_UNABLE_TOKEN + finalJson));
            return null;
        }
        json = fetch(getProfileParams(account), account);
        AccountInfo info = createTransientAccountInfo(account);
        boolean updated = applyProfileDetails(info, json);
        if (isNotBlank(json)) {
            String accountInfoJson = fetch(getAccountParams(), account);
            updated = applyAccountInfoDetails(info, accountInfoJson) || updated;
        }
        return updated ? info : null;
    }

    public void hardTokenRefresh(Account account) {
        account.setToken(null);
        AccountService.getInstance().syncSessionToken(account);
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addWarningLog(HandshakeService.class, MSG_UNABLE_RESOLVE_URL + account.getAccountName()));
            return;
        }
        String json = fetch(getHandshakeParams(), account);
        account.setToken(parseJasonToken(json));
        AccountService.getInstance().syncSessionToken(account);
        if (account.isNotConnected()) {
            String finalJson = json;
            Platform.runLater(() -> com.uiptv.util.AppLog.addWarningLog(HandshakeService.class, MSG_UNABLE_TOKEN + finalJson));
        }
        json = fetch(getProfileParams(account), account);
        AccountInfo info = resolveAccountInfo(account);
        boolean updated = applyProfileDetails(info, json);
        if (isNotBlank(json)) {
            String accountInfoJson = fetch(getAccountParams(), account);
            updated = applyAccountInfoDetails(info, accountInfoJson) || updated;
        }
        if (updated) {
            persistAccountInfo(info);
        }
    }

    public String parseJasonToken(String json) {
        if (isBlank(json) || new JSONObject(json).getJSONObject("js") == null
                || isBlank(new JSONObject(json).getJSONObject("js").getString(PARAM_TOKEN))) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addErrorLog(HandshakeService.class, "Error while establishing connection to server"));
            return StringUtils.EMPTY;
        }
        return new JSONObject(json).getJSONObject("js").getString(PARAM_TOKEN);
    }

    private boolean applyProfileDetails(AccountInfo info, String json) {
        JSONObject js = parsePortalResponse(json);
        if (js == null || info == null) {
            return false;
        }
        boolean updated = false;
        updated |= updateIfNotBlank(json, info::getProfileJson, info::setProfileJson);
        String expireDate = deriveExpiryDate(js);
        updated |= updateIfNotBlank(expireDate, info::getExpireDate, info::setExpireDate);
        AccountStatus derivedStatus = deriveAccountStatus(js);
        updated |= updateIfNotBlank(derivedStatus, info::getAccountStatus, info::setAccountStatus);
        updated |= updateIfNotBlank(firstNonBlank(nullSafeString(js, KEY_TARIFF_PLAN), nullSafeString(js, "tariff_plan_id")),
                info::getTariffPlan, info::setTariffPlan);
        updated |= updateIfNotBlank(nullSafeString(js, KEY_TARIFF_NAME), info::getTariffName, info::setTariffName);
        updated |= updateIfNotBlank(firstNonBlank(nullSafeString(js, KEY_DEFAULT_TIMEZONE), nullSafeString(js, KEY_TIMEZONE)),
                info::getDefaultTimezone, info::setDefaultTimezone);
        updated |= applyAllowedStbTypes(info, js);
        updated |= applyPasswordHashes(info, js);
        return updated;
    }

    private boolean applyAccountInfoDetails(AccountInfo info, String json) {
        JSONObject js = parsePortalResponse(json);
        if (js == null || info == null) {
            return false;
        }
        JSONObject accountInfoJson = js.optJSONObject(KEY_ACCOUNT_INFO);
        if (accountInfoJson == null) {
            accountInfoJson = js;
        }
        boolean updated = false;
        String balance = firstNonBlank(
                nullSafeString(accountInfoJson, KEY_ACCOUNT_INFO),
                nullSafeString(accountInfoJson, "account_balance"),
                nullSafeString(accountInfoJson, "balance"),
                nullSafeString(js, KEY_ACCOUNT_INFO),
                nullSafeString(js, "account_balance"),
                nullSafeString(js, "balance")
        );
        updated |= updateIfNotBlank(balance, info::getAccountBalance, info::setAccountBalance);
        updated |= updateIfNotBlank(firstNonBlank(nullSafeString(accountInfoJson, KEY_TARIFF_NAME), nullSafeString(js, KEY_TARIFF_NAME)),
                info::getTariffName, info::setTariffName);
        updated |= updateIfNotBlank(firstNonBlank(nullSafeString(accountInfoJson, KEY_TARIFF_PLAN), nullSafeString(js, KEY_TARIFF_PLAN)),
                info::getTariffPlan, info::setTariffPlan);
        updated |= updateIfNotBlank(firstNonBlank(nullSafeString(accountInfoJson, KEY_DEFAULT_TIMEZONE), nullSafeString(js, KEY_DEFAULT_TIMEZONE),
                nullSafeString(accountInfoJson, KEY_TIMEZONE), nullSafeString(js, KEY_TIMEZONE)),
                info::getDefaultTimezone, info::setDefaultTimezone);
        updated |= applyAllowedStbTypes(info, accountInfoJson);
        if (accountInfoJson != js) {
            updated |= applyAllowedStbTypes(info, js);
        }
        updated |= applyPasswordHashes(info, accountInfoJson);
        if (accountInfoJson != js) {
            updated |= applyPasswordHashes(info, js);
        }
        return updated;
    }

    private JSONObject parsePortalResponse(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            JSONObject root = new JSONObject(json);
            JSONObject js = root.optJSONObject("js");
            return js != null ? js : root;
        } catch (Exception _) {
            return null;
        }
    }

    private boolean updateIfNotBlank(String value, Supplier<String> currentValue, Consumer<String> setter) {
        if (isBlank(value)) {
            return false;
        }
        String current = currentValue != null ? currentValue.get() : null;
        if (value.equals(current)) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private boolean applyPasswordHashes(AccountInfo info, JSONObject json) {
        if (info == null || json == null) {
            return false;
        }
        boolean updated = false;
        updated |= updatePasswordHash(nullSafeString(json, "pass"), info::getPassHash, info::setPassHash);
        updated |= updatePasswordHash(nullSafeString(json, "parent_password"), info::getParentPasswordHash, info::setParentPasswordHash);
        updated |= updatePasswordHash(nullSafeString(json, "password"), info::getPasswordHash, info::setPasswordHash);
        updated |= updatePasswordHash(nullSafeString(json, "settings_password"), info::getSettingsPasswordHash, info::setSettingsPasswordHash);
        updated |= updatePasswordHash(nullSafeString(json, "account_page_by_password"), info::getAccountPagePasswordHash, info::setAccountPagePasswordHash);
        return updated;
    }

    private boolean updatePasswordHash(String rawValue, Supplier<String> currentValue, Consumer<String> setter) {
        if (isBlank(rawValue)) {
            return false;
        }
        String existing = currentValue != null ? currentValue.get() : null;
        if (isNotBlank(existing) && verifyPassword(rawValue, existing)) {
            return false;
        }
        String hashed = hashPassword(rawValue);
        if (isBlank(hashed)) {
            return false;
        }
        setter.accept(hashed);
        return true;
    }

    private AccountStatus deriveAccountStatus(JSONObject js) {
        String blocked = nullSafeString(js, "blocked");
        if (isTruthy(blocked)) {
            return AccountStatus.SUSPENDED;
        }
        return AccountStatus.ACTIVE;
    }

    private boolean isTruthy(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private String deriveExpiryDate(JSONObject js) {
        String tariffExpired = normalizeExpiry(nullSafeString(js, "tariff_expired_date"));
        if (isNotBlank(tariffExpired)) {
            return tariffExpired;
        }
        String expireBilling = normalizeExpiry(nullSafeString(js, "expire_billing_date"));
        if (isNotBlank(expireBilling)) {
            return expireBilling;
        }
        String extendAt = nullSafeString(js, "extend_at");
        String extendResolved = resolveExtendAt(extendAt);
        if (isNotBlank(extendResolved)) {
            return extendResolved;
        }
        return "";
    }

    private String normalizeExpiry(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (DATE_ZERO.equals(trimmed) || "0".equals(trimmed)) {
            return "";
        }
        return trimmed;
    }

    private String resolveExtendAt(String value) {
        if (isBlank(value)) {
            return "";
        }
        String trimmed = value.trim();
        try {
            long epochSeconds = Long.parseLong(trimmed);
            if (epochSeconds <= 0) {
                return "";
            }
            return EXTEND_AT_FORMATTER.format(Instant.ofEpochSecond(epochSeconds));
        } catch (NumberFormatException _) {
            return "";
        }
    }

    private boolean applyAllowedStbTypes(AccountInfo info, JSONObject json) {
        if (info == null || json == null) {
            return false;
        }
        boolean updated = false;
        String allowed = normalizeArray(json.optJSONArray("allowed_stb_types"));
        String allowedRecording = normalizeArray(json.optJSONArray("allowed_stb_types_for_local_recording"));
        updated |= updateIfNotBlank(allowed, info::getAllowedStbTypesJson, info::setAllowedStbTypesJson);
        updated |= updateIfNotBlank(allowedRecording, info::getAllowedStbTypesForLocalRecordingJson, info::setAllowedStbTypesForLocalRecordingJson);
        if (isNotBlank(allowed)) {
            String preferred = selectPreferredStbType(allowed);
            updated |= updatePreferredStbType(preferred, info::getPreferredStbType, info::setPreferredStbType);
        }
        return updated;
    }

    private boolean updateIfNotBlank(AccountStatus value, Supplier<AccountStatus> currentValue, Consumer<AccountStatus> setter) {
        if (value == null) {
            return false;
        }
        AccountStatus current = currentValue != null ? currentValue.get() : null;
        if (value == current) {
            return false;
        }
        setter.accept(value);
        return true;
    }

    private static String resolveStbType(Account account) {
        if (account == null || isBlank(account.getDbId())) {
            return DEFAULT_STB_TYPE;
        }
        AccountInfo info = AccountInfoService.getInstance().getByAccountId(account.getDbId());
        if (info == null) {
            return DEFAULT_STB_TYPE;
        }
        String preferred = info.getPreferredStbType();
        if (isNotBlank(preferred)) {
            return preferred;
        }
        String allowed = info.getAllowedStbTypesJson();
        if (isNotBlank(allowed)) {
            return allowedContainsMag250(allowed) ? DEFAULT_STB_TYPE : firstAllowedStbType(allowed, DEFAULT_STB_TYPE);
        }
        return DEFAULT_STB_TYPE;
    }

    private static String selectPreferredStbType(String allowedJson) {
        if (isBlank(allowedJson)) {
            return "";
        }
        try {
            JSONArray array = new JSONArray(allowedJson);
            if (array.length() == 0) {
                return "";
            }
            boolean hasMag = false;
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (value.equalsIgnoreCase("mag250")) {
                    hasMag = true;
                    break;
                }
            }
            if (hasMag) {
                return "";
            }
            String first = array.optString(0, "");
            return isNotBlank(first) ? first : "";
        } catch (Exception _) {
            return "";
        }
    }

    private boolean updatePreferredStbType(String preferred, Supplier<String> currentValue, Consumer<String> setter) {
        String current = currentValue != null ? currentValue.get() : "";
        if (isBlank(preferred)) {
            if (isBlank(current)) {
                return false;
            }
            setter.accept("");
            return true;
        }
        if (preferred.equals(current)) {
            return false;
        }
        setter.accept(preferred);
        return true;
    }

    private static boolean allowedContainsMag250(String allowedJson) {
        if (isBlank(allowedJson)) {
            return false;
        }
        try {
            JSONArray array = new JSONArray(allowedJson);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "");
                if (value.equalsIgnoreCase("mag250")) {
                    return true;
                }
            }
        } catch (Exception _) {
            return false;
        }
        return false;
    }

    private static String firstAllowedStbType(String allowedJson, String fallback) {
        if (isBlank(allowedJson)) {
            return fallback;
        }
        try {
            JSONArray array = new JSONArray(allowedJson);
            String first = array.optString(0, "");
            return isNotBlank(first) ? first : fallback;
        } catch (Exception _) {
            return fallback;
        }
    }

    private String normalizeArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return "";
        }
        return array.toString();
    }

    private String hashPassword(String rawValue) {
        if (isBlank(rawValue)) {
            return "";
        }
        try {
            byte[] salt = new byte[PASS_SALT_BYTES];
            PASS_RANDOM.nextBytes(salt);
            byte[] hash = pbkdf2(rawValue.toCharArray(), salt, PASS_HASH_ITERATIONS, PASS_HASH_BYTES);
            return PASS_HASH_PREFIX + "$" + PASS_HASH_ITERATIONS + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (java.security.GeneralSecurityException _) {
            return "";
        }
    }

    private boolean verifyPassword(String rawValue, String storedHash) {
        if (isBlank(rawValue) || isBlank(storedHash)) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        if (!PASS_HASH_PREFIX.equals(parts[0])) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException _) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(rawValue.toCharArray(), salt, iterations, expectedHash.length);
            return slowEquals(expectedHash, actualHash);
        } catch (java.security.GeneralSecurityException _) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] value, byte[] salt, int iterations, int length) throws java.security.GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(value, salt, iterations, length * 8);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return factory.generateSecret(spec).getEncoded();
    }

    private boolean slowEquals(byte[] left, byte[] right) {
        if (left == null || right == null) {
            return false;
        }
        int diff = left.length ^ right.length;
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            byte a = i < left.length ? left[i] : 0;
            byte b = i < right.length ? right[i] : 0;
            diff |= a ^ b;
        }
        return diff == 0;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private AccountInfo resolveAccountInfo(Account account) {
        if (account == null || isBlank(account.getDbId())) {
            return null;
        }
        AccountInfo existing = AccountInfoService.getInstance().getByAccountId(account.getDbId());
        if (existing == null) {
            AccountInfo created = new AccountInfo();
            created.setAccountId(account.getDbId());
            return created;
        }
        existing.setAccountId(account.getDbId());
        return existing;
    }

    private void persistAccountInfo(AccountInfo info) {
        if (info == null || isBlank(info.getAccountId())) {
            return;
        }
        AccountInfoService.getInstance().save(info);
    }

    private AccountInfo createTransientAccountInfo(Account account) {
        AccountInfo info = new AccountInfo();
        if (account != null && isNotBlank(account.getDbId())) {
            info.setAccountId(account.getDbId());
        }
        return info;
    }
}
