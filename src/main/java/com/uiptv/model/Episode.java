package com.uiptv.model;


import java.util.Map;
import java.util.Objects;

import static com.uiptv.util.StringUtils.getXtremeStreamUrl;
import static com.uiptv.util.StringUtils.safeGetString;

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

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEpisodeNum() {
        return episodeNum;
    }

    public void setEpisodeNum(String episodeNum) {
        this.episodeNum = episodeNum;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContainerExtension() {
        return containerExtension;
    }

    public void setContainerExtension(String containerExtension) {
        this.containerExtension = containerExtension;
    }

    public String getCustom_sid() {
        return custom_sid;
    }

    public void setCustom_sid(String custom_sid) {
        this.custom_sid = custom_sid;
    }

    public String getAdded() {
        return added;
    }

    public void setAdded(String added) {
        this.added = added;
    }

    public String getSeason() {
        return season;
    }

    public void setSeason(String season) {
        this.season = season;
    }

    public String getDirect_source() {
        return direct_source;
    }

    public void setDirect_source(String direct_source) {
        this.direct_source = direct_source;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public EpisodeInfo getInfo() {
        return info;
    }

    public void setInfo(EpisodeInfo info) {
        this.info = info;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Episode episode = (Episode) o;
        return Objects.equals(id, episode.id) && Objects.equals(episodeNum, episode.episodeNum) && Objects.equals(title, episode.title) && Objects.equals(containerExtension, episode.containerExtension) && Objects.equals(custom_sid, episode.custom_sid) && Objects.equals(added, episode.added) && Objects.equals(season, episode.season) && Objects.equals(direct_source, episode.direct_source) && Objects.equals(cmd, episode.cmd) && Objects.equals(info, episode.info);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, episodeNum, title, containerExtension, custom_sid, added, season, direct_source, cmd, info);
    }

    @Override
    public String toString() {
        return "Episode{" +
                "id='" + id + '\'' +
                ", episodeNum='" + episodeNum + '\'' +
                ", title='" + title + '\'' +
                ", containerExtension='" + containerExtension + '\'' +
                ", custom_sid='" + custom_sid + '\'' +
                ", added='" + added + '\'' +
                ", season='" + season + '\'' +
                ", direct_source='" + direct_source + '\'' +
                ", cmd='" + cmd + '\'' +
                ", info=" + info +
                '}';
    }
}

