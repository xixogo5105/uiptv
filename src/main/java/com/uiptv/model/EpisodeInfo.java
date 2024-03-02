package com.uiptv.model;

import java.util.Map;
import java.util.Objects;

import static com.uiptv.util.StringUtils.safeGetString;

class EpisodeInfo extends BaseJson {
    String tmdbId, releaseDate, plot, durationSecs, duration, movieImage, bitrate, rating, season;
    VideoInfo video;
    AudioInfo audio;

    public EpisodeInfo(String tmdbId, String releaseDate, String plot, String durationSecs, String duration, String movieImage, String bitrate, String rating, String season, VideoInfo video, AudioInfo audio) {
    }

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

    public String getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(String tmdbId) {
        this.tmdbId = tmdbId;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getPlot() {
        return plot;
    }

    public void setPlot(String plot) {
        this.plot = plot;
    }

    public String getDurationSecs() {
        return durationSecs;
    }

    public void setDurationSecs(String durationSecs) {
        this.durationSecs = durationSecs;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getMovieImage() {
        return movieImage;
    }

    public void setMovieImage(String movieImage) {
        this.movieImage = movieImage;
    }

    public String getBitrate() {
        return bitrate;
    }

    public void setBitrate(String bitrate) {
        this.bitrate = bitrate;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public VideoInfo getVideo() {
        return video;
    }

    public void setVideo(VideoInfo video) {
        this.video = video;
    }

    public AudioInfo getAudio() {
        return audio;
    }

    public void setAudio(AudioInfo audio) {
        this.audio = audio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EpisodeInfo that = (EpisodeInfo) o;
        return Objects.equals(tmdbId, that.tmdbId) && Objects.equals(releaseDate, that.releaseDate) && Objects.equals(plot, that.plot) && Objects.equals(durationSecs, that.durationSecs) && Objects.equals(duration, that.duration) && Objects.equals(movieImage, that.movieImage) && Objects.equals(bitrate, that.bitrate) && Objects.equals(rating, that.rating) && Objects.equals(season, that.season) && Objects.equals(video, that.video) && Objects.equals(audio, that.audio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tmdbId, releaseDate, plot, durationSecs, duration, movieImage, bitrate, rating, season, video, audio);
    }
}
