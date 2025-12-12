package com.uiptv.shared;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.json.JSONObject;

import static com.uiptv.util.StringUtils.safeGetString;

@Data
@NoArgsConstructor
public class SeasonInfo extends BaseJson {
    String name, cover, plot, cast, director, genre, releaseDate, lastModified, rating, rating5Based, backdropPath,
            tmdb, youtubeTrailer, episodeRunTime, categoryId;

    public SeasonInfo(JSONObject info) {
        this.name = safeGetString(info, "name");
        this.cover = safeGetString(info, "cover");
        this.plot = safeGetString(info, "plot");
        this.cast = safeGetString(info, "cast");
        this.director = safeGetString(info, "director");
        this.genre = safeGetString(info, "genre");
        this.releaseDate = safeGetString(info, "releaseDate");
        this.lastModified = safeGetString(info, "last_modified");
        this.rating = safeGetString(info, "rating");
        this.rating5Based = safeGetString(info, "rating_5based");
        this.backdropPath = safeGetString(info, "backdrop_path");
        this.tmdb = safeGetString(info, "tmdb");
        this.youtubeTrailer = safeGetString(info, "youtube_trailer");
        this.episodeRunTime = safeGetString(info, "episode_run_time");
        this.categoryId = safeGetString(info, "category_id");
    }
}
