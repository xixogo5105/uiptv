package com.uiptv.model;


import org.json.JSONObject;

import java.util.Objects;

import static com.uiptv.util.StringUtils.safeGetString;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCover() {
        return cover;
    }

    public void setCover(String cover) {
        this.cover = cover;
    }

    public String getPlot() {
        return plot;
    }

    public void setPlot(String plot) {
        this.plot = plot;
    }

    public String getCast() {
        return cast;
    }

    public void setCast(String cast) {
        this.cast = cast;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getLastModified() {
        return lastModified;
    }

    public void setLastModified(String lastModified) {
        this.lastModified = lastModified;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getRating5Based() {
        return rating5Based;
    }

    public void setRating5Based(String rating5Based) {
        this.rating5Based = rating5Based;
    }

    public String getBackdropPath() {
        return backdropPath;
    }

    public void setBackdropPath(String backdropPath) {
        this.backdropPath = backdropPath;
    }

    public String getTmdb() {
        return tmdb;
    }

    public void setTmdb(String tmdb) {
        this.tmdb = tmdb;
    }

    public String getYoutubeTrailer() {
        return youtubeTrailer;
    }

    public void setYoutubeTrailer(String youtubeTrailer) {
        this.youtubeTrailer = youtubeTrailer;
    }

    public String getEpisodeRunTime() {
        return episodeRunTime;
    }

    public void setEpisodeRunTime(String episodeRunTime) {
        this.episodeRunTime = episodeRunTime;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    @Override
    public String toString() {
        return "SeasonInfo{" +
                "name='" + name + '\'' +
                ", cover='" + cover + '\'' +
                ", plot='" + plot + '\'' +
                ", cast='" + cast + '\'' +
                ", director='" + director + '\'' +
                ", genre='" + genre + '\'' +
                ", releaseDate='" + releaseDate + '\'' +
                ", last_modified='" + lastModified + '\'' +
                ", rating='" + rating + '\'' +
                ", rating_5based='" + rating5Based + '\'' +
                ", backdrop_path='" + backdropPath + '\'' +
                ", tmdb='" + tmdb + '\'' +
                ", youtube_trailer='" + youtubeTrailer + '\'' +
                ", episode_run_time='" + episodeRunTime + '\'' +
                ", category_id='" + categoryId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SeasonInfo that = (SeasonInfo) o;
        return Objects.equals(name, that.name) && Objects.equals(cover, that.cover) && Objects.equals(plot, that.plot) && Objects.equals(cast, that.cast) && Objects.equals(director, that.director) && Objects.equals(genre, that.genre) && Objects.equals(releaseDate, that.releaseDate) && Objects.equals(lastModified, that.lastModified) && Objects.equals(rating, that.rating) && Objects.equals(rating5Based, that.rating5Based) && Objects.equals(backdropPath, that.backdropPath) && Objects.equals(tmdb, that.tmdb) && Objects.equals(youtubeTrailer, that.youtubeTrailer) && Objects.equals(episodeRunTime, that.episodeRunTime) && Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, cover, plot, cast, director, genre, releaseDate, lastModified, rating, rating5Based, backdropPath, tmdb, youtubeTrailer, episodeRunTime, categoryId);
    }
}

