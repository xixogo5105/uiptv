package com.uiptv.application;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import org.json.JSONObject;

/** Account row identity is stable across cache reloads; channel identity is always the provider id. */
public class EpgMappingApplicationService {
    public String read(String accountId, String providerChannelId) {
        if (accountId == null || providerChannelId == null) return "";
        JSONObject mappings = mappings(ConfigurationService.getInstance().read());
        JSONObject account = mappings.optJSONObject(accountId);
        return account == null ? "" : account.optString(providerChannelId, "");
    }
    public void save(String accountId, String providerChannelId, String guideId) {
        if (accountId == null || accountId.isBlank() || providerChannelId == null || providerChannelId.isBlank())
            throw new IllegalArgumentException("Account and provider channel identities required");
        synchronized (ConfigurationService.getInstance()) {
            Configuration config = ConfigurationService.getInstance().read();
            JSONObject mappings = mappings(config);
            JSONObject account = mappings.optJSONObject(accountId);
            if (account == null) account = new JSONObject();
            if (guideId == null || guideId.isBlank()) account.remove(providerChannelId);
            else account.put(providerChannelId, guideId.strip());
            mappings.put(accountId, account);
            config.setEpgMappingsJson(mappings.toString());
            ConfigurationService.getInstance().save(config);
        }
    }
    private JSONObject mappings(Configuration configuration) {
        try { return new JSONObject(configuration.getEpgMappingsJson()); }
        catch (Exception ignored) { return new JSONObject(); }
    }
}
