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
        mergeEpisodeLevelArtwork(map);
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
            episode.mergeEpisodeLevelArtwork(jsonObj.toMap());
            return episode;
        } catch (Exception e) {
            return null;
        }
    }

    private void mergeEpisodeLevelArtwork(Map map) {
        if (map == null) return;
        if (this.info == null) {
            this.info = new EpisodeInfo();
        }
        String current = this.info.getMovieImage();
        if (!isBlankLike(current)) {
            return;
        }
        String rootEpisodeImage = firstNonBlank(
                map,
                "movie_image",
                "thumbnail",
                "still_path",
                "cover_big",
                "cover",
                "screenshot_uri",
                "stream_icon",
                "image",
                "poster"
        );
        if (!isBlankLike(rootEpisodeImage)) {
            this.info.setMovieImage(rootEpisodeImage);
        }
    }

    private String firstNonBlank(Map map, String... keys) {
        if (map == null || keys == null) return "";
        for (String key : keys) {
            String value = safeGetString(map, key);
            if (!isBlankLike(value)) return value.trim();
        }
        return "";
    }

    private boolean isBlankLike(String value) {
        if (value == null) return true;
        String v = value.trim();
        return v.isEmpty() || "null".equalsIgnoreCase(v) || "n/a".equalsIgnoreCase(v);
    }
}
