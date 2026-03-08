package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.StringUtils;
import javafx.application.Platform;
import org.json.JSONObject;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.uiptv.util.FetchAPI.fetch;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;


public class HandshakeService {
    private static final String PARAM_ACTION = "action";
    private static final String PARAM_JS_HTTP_REQUEST = "JsHttpRequest";
    private static final String PARAM_TOKEN = "token";

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
        params.put("type", "stb");
        params.put(PARAM_ACTION, "get_profile");
        params.put("hd", "1");
        params.put("ver", "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.9; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c");
        params.put("num_banks", "2");
        params.put("sn", c.getSerialNumber());
        params.put("stb_type", "MAG250");
        params.put("client_type", "STB");
        params.put("image_version", "218");
        params.put("video_out", "hdmi");
        params.put("device_id", c.getDeviceId1());
        params.put("device_id2", c.getDeviceId2() == null ? c.getDeviceId1() : c.getDeviceId2());
        params.put("signature", isBlank(c.getSignature()) ? generateSerialNumber() : c.getSignature());
        params.put("auth_second_step", "1");
        params.put("hw_version", "1.7-BD-00");
        params.put("not_valid_token", "0");
        params.put("metrics", "{\"mac\":\"" + c.getMacAddress() + "\",\"sn\":\"" + c.getSerialNumber() + "\",\"type\":\"STB\",\"model\":\"MAG250\",\"uid\":\"\",\"random\":\"" + generateRandom() + "\"}");
        params.put("hw_version_2", generateRandom());
        params.put("api_signature", "262");
        params.put("prehash", "");
        params.put(PARAM_JS_HTTP_REQUEST, new Date().getTime() + "-xml");


        return params;
    }

    private static Map<String, String> getAccountParams() {
        final Map<String, String> params = new HashMap<>();
        params.put("type", "account_info");
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
            Platform.runLater(() -> com.uiptv.util.AppLog.addLog("Unable to resolve server portal URL for account: " + account.getAccountName()));
            return;
        }
        String json = fetch(getHandshakeParams(), account);
        account.setToken(parseJasonToken(json));
        AccountService.getInstance().syncSessionToken(account);
        if (account.isNotConnected()) {
            String finalJson = json;
            Platform.runLater(() -> com.uiptv.util.AppLog.addLog("Unable to retrieve a token:\n\n" + finalJson));
            return;
        }
        json = fetch(getProfileParams(account), account);
        if (isNotBlank(json)) {
            fetch(getAccountParams(), account);
        }
    }

    public void hardTokenRefresh(Account account) {
        account.setToken(null);
        AccountService.getInstance().syncSessionToken(account);
        if (isBlank(AccountService.getInstance().ensureServerPortalUrl(account))) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addLog("Unable to resolve server portal URL for account: " + account.getAccountName()));
            return;
        }
        String json = fetch(getHandshakeParams(), account);
        account.setToken(parseJasonToken(json));
        AccountService.getInstance().syncSessionToken(account);
        if (account.isNotConnected()) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addLog("Unable to retrieve a token:\n\n" + json));
        }
        fetch(getProfileParams(account), account);
    }

    public String parseJasonToken(String json) {
        if (isBlank(json) || new JSONObject(json).getJSONObject("js") == null
                || isBlank(new JSONObject(json).getJSONObject("js").getString(PARAM_TOKEN))) {
            Platform.runLater(() -> com.uiptv.util.AppLog.addLog("Error while establishing connection to server"));
            return StringUtils.EMPTY;
        }
        return new JSONObject(json).getJSONObject("js").getString(PARAM_TOKEN);
    }
}
