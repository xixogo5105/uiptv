package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
public class Channel extends BaseJson {
    private String dbId, channelId, categoryId, name, number, cmd, cmd_1, cmd_2, cmd_3, logo;
    private String description, season, episodeNum, releaseDate, rating, duration;
    private String extraJson;
    private int censored, status, hd;
    private String drmType;
    private String drmLicenseUrl;
    private String clearKeysJson;
    private String inputstreamaddon;
    private String manifestType; // New field for manifest_type

    public Channel(String channelId, String name, String number, String cmd, String cmd_1, String cmd_2, String cmd_3, String logo, int censored, int status, int hd, String drmType, String drmLicenseUrl, Map<String, String> clearKeys, String inputstreamaddon, String manifestType) {
        this.channelId = channelId;
        this.name = name;
        this.number = number;
        this.cmd = cmd;
        this.cmd_1 = cmd_1;
        this.cmd_2 = cmd_2;
        this.cmd_3 = cmd_3;
        this.logo = logo;
        this.censored = censored;
        this.status = status;
        this.hd = hd;
        this.drmType = drmType;
        this.drmLicenseUrl = drmLicenseUrl;
        setClearKeys(clearKeys);
        this.inputstreamaddon = inputstreamaddon;
        this.manifestType = manifestType;
    }

    public static Channel fromJson(String json) {
        try {
            JSONObject jsonObj = new JSONObject(json);
            Channel channel = new Channel();
            channel.setDbId(safeGetString(jsonObj, "dbId"));
            channel.setChannelId(safeGetString(jsonObj, "channelId"));
            channel.setCategoryId(safeGetString(jsonObj, "categoryId"));
            channel.setName(safeGetString(jsonObj, "name"));
            channel.setNumber(safeGetString(jsonObj, "number"));
            channel.setCmd(safeGetString(jsonObj, "cmd"));
            channel.setCmd_1(safeGetString(jsonObj, "cmd_1"));
            channel.setCmd_2(safeGetString(jsonObj, "cmd_2"));
            channel.setCmd_3(safeGetString(jsonObj, "cmd_3"));
            channel.setLogo(safeGetString(jsonObj, "logo"));
            channel.setDescription(safeGetString(jsonObj, "description"));
            channel.setSeason(safeGetString(jsonObj, "season"));
            channel.setEpisodeNum(safeGetString(jsonObj, "episodeNum"));
            channel.setReleaseDate(safeGetString(jsonObj, "releaseDate"));
            channel.setRating(safeGetString(jsonObj, "rating"));
            channel.setDuration(safeGetString(jsonObj, "duration"));
            channel.setExtraJson(safeGetString(jsonObj, "extraJson"));
            channel.setCensored(jsonObj.optInt("censored"));
            channel.setStatus(jsonObj.optInt("status"));
            channel.setHd(jsonObj.optInt("hd"));
            channel.setDrmType(safeGetString(jsonObj, "drmType"));
            channel.setDrmLicenseUrl(safeGetString(jsonObj, "drmLicenseUrl"));
            channel.setClearKeysJson(safeGetString(jsonObj, "clearKeysJson"));
            channel.setInputstreamaddon(safeGetString(jsonObj, "inputstreamaddon"));
            channel.setManifestType(safeGetString(jsonObj, "manifestType"));
            return channel;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, String> getClearKeys() {
        if (this.clearKeysJson == null || this.clearKeysJson.isEmpty() || "{}".equals(this.clearKeysJson)) {
            return null;
        }
        Map<String, String> map = new HashMap<>();
        try {
            String json = this.clearKeysJson.substring(1, this.clearKeysJson.length() - 1);
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim().replace("\"", "");
                map.put(key, value);
            }
        } catch (Exception e) {
            return null;
        }
        return map;
    }

    public void setClearKeys(Map<String, String> clearKeys) {
        if (clearKeys == null || clearKeys.isEmpty()) {
            this.clearKeysJson = null;
            return;
        }
        this.clearKeysJson = clearKeys.entrySet()
                .stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    public int getCompareSeason() {
        try {
            Integer season = Integer.parseInt(this.getName().split("-")[0]
                    .toLowerCase()
                    .replace(" ", "")
                    .replace("season", "")
                    .replace("episode", "")
                    .replace("-", ""));
            return season;
        } catch (Exception ignored) {
        }
        return 0;
    }

    public int getCompareEpisode() {
        try {
            Integer episode = Integer.parseInt(this.getName().split("-")[1]
                    .toLowerCase()
                    .replace(" ", "")
                    .replace("season", "")
                    .replace("episode", "")
                    .replace("-", ""));
            return episode;
        } catch (Exception ignored) {
        }
        return 0;
    }
}
