package com.uiptv.model;

import java.util.Objects;

import static com.uiptv.util.StringUtils.isBlank;

public class PlaylistEntry extends BaseJson {
    private String id;
    private String groupTitle;
    private String title;
    private String playlistEntry;
    private String logo;

    public PlaylistEntry(String id, String groupTitle, String title, String playlistEntry, String logo) {
        this.id = id;
        this.groupTitle = groupTitle;
        this.title = title;
        this.playlistEntry = playlistEntry;
        this.logo = logo;
    }

    public String getId() {
        return id;
    }

    public String getGroupTitle() {
        return isBlank(groupTitle) ? "All" : groupTitle;
    }

    public String getPlaylistEntry() {
        return playlistEntry;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistEntry that = (PlaylistEntry) o;
        return Objects.equals(groupTitle, that.groupTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupTitle);
    }

    @Override
    public String toString() {
        return "PlaylistEntry{" +
                "id='" + id + '\'' +
                ", groupTitle='" + getGroupTitle() + '\'' +
                ", title='" + title + '\'' +
                ", playlistEntry='" + playlistEntry + '\'' +
                ", logo='" + logo + '\'' +
                '}';
    }
}
