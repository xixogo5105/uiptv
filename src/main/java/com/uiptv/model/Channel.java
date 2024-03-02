package com.uiptv.model;


import com.uiptv.api.JsonCompliant;

import java.io.Serializable;
import java.util.Objects;

import static com.uiptv.util.StringUtils.safeJson;

public class Channel implements Serializable, JsonCompliant {
    private String dbId, channelId, categoryId, name, number, cmd, cmd_1, cmd_2, cmd_3, logo;
    private int censored, status, hd;

    public Channel(String channelId, String name, String number, String cmd, String cmd_1, String cmd_2, String cmd_3, String logo, int censored, int status, int hd) {
        this.channelId = channelId;
        this.name = name;
        this.number = number;
        this.cmd = cmd;
        this.cmd_1 = cmd_1;
        this.cmd_2 = cmd_2;
        this.cmd_3 = cmd_3;
        this.logo = logo;
        this.censored = censored;
        this.status = status;
        this.hd = hd;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public String getCmd_1() {
        return cmd_1;
    }

    public void setCmd_1(String cmd_1) {
        this.cmd_1 = cmd_1;
    }

    public String getCmd_2() {
        return cmd_2;
    }

    public void setCmd_2(String cmd_2) {
        this.cmd_2 = cmd_2;
    }

    public String getCmd_3() {
        return cmd_3;
    }

    public void setCmd_3(String cmd_3) {
        this.cmd_3 = cmd_3;
    }

    public String getLogo() {
        return logo;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public int getCensored() {
        return censored;
    }

    public void setCensored(int censored) {
        this.censored = censored;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getHd() {
        return hd;
    }

    public void setHd(int hd) {
        this.hd = hd;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Channel channel = (Channel) o;
        return censored == channel.censored && status == channel.status && hd == channel.hd && Objects.equals(dbId, channel.dbId) && Objects.equals(channelId, channel.channelId) && Objects.equals(categoryId, channel.categoryId) && Objects.equals(name, channel.name) && Objects.equals(number, channel.number) && Objects.equals(cmd, channel.cmd) && Objects.equals(cmd_1, channel.cmd_1) && Objects.equals(cmd_2, channel.cmd_2) && Objects.equals(cmd_3, channel.cmd_3) && Objects.equals(logo, channel.logo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbId, channelId, categoryId, name, number, cmd, cmd_1, cmd_2, cmd_3, logo, censored, status, hd);
    }

    @Override
    public String toString() {
        return "Channel{" +
                "dbId='" + dbId + '\'' +
                ", channelId='" + channelId + '\'' +
                ", categoryId='" + categoryId + '\'' +
                ", name='" + name + '\'' +
                ", number='" + number + '\'' +
                ", cmd='" + cmd + '\'' +
                ", cmd_1='" + cmd_1 + '\'' +
                ", cmd_2='" + cmd_2 + '\'' +
                ", cmd_3='" + cmd_3 + '\'' +
                ", logo='" + logo + '\'' +
                ", censored=" + censored +
                ", status=" + status +
                ", hd=" + hd +
                '}';
    }

    @Override
    public String toJson() {
        return "{" +
                "        \"dbId\":\"" + dbId + "\"" +
                ",         \"channelId\":\"" + safeJson(channelId) + "\"" +
                ",         \"categoryId\":\"" + safeJson(categoryId) + "\"" +
                ",         \"name\":\"" + safeJson(name) + "\"" +
                ",         \"number\":\"" + safeJson(number) + "\"" +
                ",         \"cmd\":\"" + safeJson(cmd) + "\"" +
                ",         \"cmd_1\":\"" + safeJson(cmd_1) + "\"" +
                ",         \"cmd_2\":\"" + safeJson(cmd_2) + "\"" +
                ",         \"cmd_3\":\"" + safeJson(cmd_3) + "\"" +
                ",         \"logo\":\"" + safeJson(logo) + "\"" +
                ",         \"censored\":\"" + censored + "\"" +
                ",         \"status\":\"" + status + "\"" +
                ",         \"hd\":\"" + hd + "\"" +
                "}";
    }

    public int getCompareSeason() {
        try {
            Integer season = Integer.parseInt(this.getName().split("-")[0]
                    .toLowerCase()
                    .replace(" ", "")
                    .replace("season", "")
                    .replace("episode", "")
                    .replace("-", ""));
            return season;
        } catch (Exception ignored) {
        }
        return 0;
    }

    public int getCompareEpisode() {
        try {
            Integer episode = Integer.parseInt(this.getName().split("-")[1]
                    .toLowerCase()
                    .replace(" ", "")
                    .replace("season", "")
                    .replace("episode", "")
                    .replace("-", ""));
            return episode;
        } catch (Exception ignored) {
        }
        return 0;
    }
}

