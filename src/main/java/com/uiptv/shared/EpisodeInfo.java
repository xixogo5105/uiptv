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

        this.tmdbId = safeGetString(map, "tmdb_id");
        this.releaseDate = safeGetString(map, "releaseDate");
        this.plot = safeGetString(map, "plot");
        this.durationSecs = safeGetString(map, "duration_secs");
        this.duration = safeGetString(map, "duration");
        this.movieImage = safeGetString(map, "movie_image");
        this.bitrate = safeGetString(map, "bitrate");
        this.rating = safeGetString(map, "rating");
        this.season = safeGetString(map, "season");
        this.video = new VideoInfo((Map) map.get("video"));
        this.audio = new AudioInfo((Map) map.get("audio"));
    }
}
