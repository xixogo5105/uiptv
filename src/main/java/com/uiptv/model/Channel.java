package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Channel extends BaseJson {
    private static final String KEY_SEASON = "season";

    private String dbId;
    private String channelId;
    private String categoryId;
    private String name;
    private String number;
    private String cmd;
    @SuppressWarnings("java:S116")
    private String cmd_1;
    @SuppressWarnings("java:S116")
    private String cmd_2;
    @SuppressWarnings("java:S116")
    private String cmd_3;
    private String logo;
    private String description;
    private String season;
    private String episodeNum;
    private String releaseDate;
    private String rating;
    private String duration;
    private String extraJson;
    private int censored;
    private int status;
    private int hd;
    private boolean watched;
    private String drmType;
    private String drmLicenseUrl;
    private String clearKeysJson;
    private String inputstreamaddon;
    private String manifestType; // New field for manifest_type

    @SuppressWarnings("java:S107")
    public Channel(String channelId, String name, String number, String cmd, String cmd1, String cmd2, String cmd3, String logo, int censored, int status, int hd, String drmType, String drmLicenseUrl, Map<String, String> clearKeys, String inputstreamaddon, String manifestType) {
        this.channelId = channelId;
        this.name = name;
        this.number = number;
        this.cmd = cmd;
        this.cmd_1 = cmd1;
        this.cmd_2 = cmd2;
        this.cmd_3 = cmd3;
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
            channel.setSeason(safeGetString(jsonObj, KEY_SEASON));
            channel.setEpisodeNum(safeGetString(jsonObj, "episodeNum"));
            channel.setReleaseDate(safeGetString(jsonObj, "releaseDate"));
            channel.setRating(safeGetString(jsonObj, "rating"));
            channel.setDuration(safeGetString(jsonObj, "duration"));
            channel.setExtraJson(safeGetString(jsonObj, "extraJson"));
            channel.setCensored(jsonObj.optInt("censored"));
            channel.setStatus(jsonObj.optInt("status"));
            channel.setHd(jsonObj.optInt("hd"));
            Object watched = jsonObj.opt("watched");
            if (watched instanceof Boolean watchedBoolean) {
                channel.setWatched(watchedBoolean);
            } else {
                String watchedStr = safeGetString(jsonObj, "watched");
                channel.setWatched("1".equals(watchedStr) || "true".equalsIgnoreCase(watchedStr));
            }
            channel.setDrmType(safeGetString(jsonObj, "drmType"));
            channel.setDrmLicenseUrl(safeGetString(jsonObj, "drmLicenseUrl"));
            channel.setClearKeysJson(safeGetString(jsonObj, "clearKeysJson"));
            channel.setInputstreamaddon(safeGetString(jsonObj, "inputstreamaddon"));
            channel.setManifestType(safeGetString(jsonObj, "manifestType"));
            return channel;
        } catch (Exception _) {
            // Treat malformed serialized channels as absent cached snapshots.
            return null;
        }
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
            return Integer.parseInt(this.getName().split("-")[0]
                    .toLowerCase()
                    .replace(" ", "")
                    .replace(KEY_SEASON, "")
                    .replace("episode", "")
                    .replace("-", ""));
        } catch (Exception _) {
            // Fall back to zero when the title cannot be parsed into a season number.
        }
        return 0;
    }

    public int getCompareEpisode() {
        try {
            return Integer.parseInt(this.getName().split("-")[1]
                    .toLowerCase()
                    .replace(" ", "")
                    .replace(KEY_SEASON, "")
                    .replace("episode", "")
                    .replace("-", ""));
        } catch (Exception _) {
            // Fall back to zero when the title cannot be parsed into an episode number.
        }
        return 0;
    }
}
