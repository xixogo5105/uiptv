package com.uiptv.shared;

import com.uiptv.model.Account;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

import java.util.Map;

import static com.uiptv.util.StringUtils.getXtremeStreamUrl;
import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
public class Episode extends BaseJson {

    String id, episodeNum, title, containerExtension, custom_sid, added, season, direct_source, cmd;
    EpisodeInfo info;

    public Episode(Account account, Map map) {
        if (map == null) return;
        this.id = safeGetString(map, "id");
        this.episodeNum = safeGetString(map, "episode_num");
        this.title = safeGetString(map, "title");
        this.containerExtension = safeGetString(map, "container_extension");
        this.custom_sid = safeGetString(map, "custom_sid");
        this.added = safeGetString(map, "added");
        this.season = safeGetString(map, "season");
        this.direct_source = safeGetString(map, "direct_source");
        this.info = new EpisodeInfo((Map) map.get("info"));
        this.cmd = getXtremeStreamUrl(account, id, containerExtension);
    }

    public static Episode fromJson(String json) {
        try {
            JSONObject jsonObj = new JSONObject(json);
            Episode episode = new Episode();
            episode.setId(safeGetString(jsonObj, "id"));
            episode.setEpisodeNum(safeGetString(jsonObj, "episodeNum"));
            episode.setTitle(safeGetString(jsonObj, "title"));
            episode.setContainerExtension(safeGetString(jsonObj, "containerExtension"));
            episode.setCustom_sid(safeGetString(jsonObj, "custom_sid"));
            episode.setAdded(safeGetString(jsonObj, "added"));
            episode.setSeason(safeGetString(jsonObj, "season"));
            episode.setDirect_source(safeGetString(jsonObj, "direct_source"));
            episode.setCmd(safeGetString(jsonObj, "cmd"));
            if (jsonObj.has("info")) {
                episode.setInfo(new EpisodeInfo(jsonObj.getJSONObject("info").toMap()));
            }
            return episode;
        } catch (Exception e) {
            return null;
        }
    }
}
