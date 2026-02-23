package com.uiptv.shared;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EpisodeInfo extends BaseJson {
    String tmdbId, releaseDate, plot, durationSecs, duration, movieImage, bitrate, rating, season;
    VideoInfo video;
    AudioInfo audio;

    public EpisodeInfo(Map map) {
        if (map == null) return;

        this.tmdbId = firstNonBlank(map, "tmdb_id", "tmdb", "imdb_id");
        this.releaseDate = firstNonBlank(map, "releaseDate", "release_date", "released", "air_date");
        this.plot = firstNonBlank(map, "plot", "overview", "description");
        this.durationSecs = firstNonBlank(map, "duration_secs", "durationSeconds");
        this.duration = firstNonBlank(map, "duration", "runtime");
        // Prefer episode-level thumbnails first, then generic poster keys.
        this.movieImage = firstNonBlank(
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
        this.bitrate = firstNonBlank(map, "bitrate");
        this.rating = firstNonBlank(map, "rating", "rating_imdb", "imdb_rating");
        this.season = firstNonBlank(map, "season");
        this.video = new VideoInfo((Map) map.get("video"));
        this.audio = new AudioInfo((Map) map.get("audio"));
    }

    private String firstNonBlank(Map map, String... keys) {
        if (map == null || keys == null) return "";
        for (String key : keys) {
            String value = safeGetString(map, key);
            if (value != null) {
                value = value.trim();
                if (!value.isEmpty() && !"null".equalsIgnoreCase(value) && !"n/a".equalsIgnoreCase(value)) {
                    return value;
                }
            }
        }
        return "";
    }
}
