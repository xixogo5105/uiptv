package com.uiptv.model;

import com.uiptv.api.JsonCompliant;

import java.io.Serializable;
import java.util.Objects;

import static com.uiptv.util.StringUtils.safeJson;

public class Configuration implements Serializable, JsonCompliant {
    private String dbId, playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, fontFamily, fontSize, fontWeight, serverPort;
    private boolean darkTheme, pauseFiltering, pauseCaching;

    public Configuration() {
    }


    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String fontFamily, String fontSize, String fontWeight, boolean darkTheme, String serverPort, boolean pauseCaching) {
        this.playerPath1 = playerPath1;
        this.playerPath2 = playerPath2;
        this.playerPath3 = playerPath3;
        this.defaultPlayerPath = defaultPlayerPath;
        this.filterCategoriesList = filterCategoriesList;
        this.filterChannelsList = filterChannelsList;
        this.pauseFiltering = pauseFiltering;
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.fontWeight = fontWeight;
        this.darkTheme = darkTheme;
        this.serverPort = serverPort;
        this.pauseCaching = pauseCaching;
    }

    public String getDbId() {
        return dbId;
    }

    public void setDbId(String dbId) {
        this.dbId = dbId;
    }

    public String getPlayerPath1() {
        return playerPath1;
    }

    public void setPlayerPath1(String playerPath1) {
        this.playerPath1 = playerPath1;
    }

    public String getPlayerPath2() {
        return playerPath2;
    }

    public void setPlayerPath2(String playerPath2) {
        this.playerPath2 = playerPath2;
    }

    public String getPlayerPath3() {
        return playerPath3;
    }

    public void setPlayerPath3(String playerPath3) {
        this.playerPath3 = playerPath3;
    }

    public String getDefaultPlayerPath() {
        return defaultPlayerPath;
    }

    public void setDefaultPlayerPath(String defaultPlayerPath) {
        this.defaultPlayerPath = defaultPlayerPath;
    }

    public String getFilterCategoriesList() {
        return filterCategoriesList;
    }

    public void setFilterCategoriesList(String filterCategoriesList) {
        this.filterCategoriesList = filterCategoriesList;
    }

    public String getFilterChannelsList() {
        return filterChannelsList;
    }

    public void setFilterChannelsList(String filterChannelsList) {
        this.filterChannelsList = filterChannelsList;
    }

    public String getFontFamily() {
        return fontFamily;
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily = fontFamily;
    }

    public String getFontSize() {
        return fontSize;
    }

    public void setFontSize(String fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontWeight() {
        return fontWeight;
    }

    public void setFontWeight(String fontWeight) {
        this.fontWeight = fontWeight;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public boolean isDarkTheme() {
        return darkTheme;
    }

    public void setDarkTheme(boolean darkTheme) {
        this.darkTheme = darkTheme;
    }

    public boolean isPauseFiltering() {
        return pauseFiltering;
    }

    public void setPauseFiltering(boolean pauseFiltering) {
        this.pauseFiltering = pauseFiltering;
    }

    public boolean isPauseCaching() {
        return pauseCaching;
    }

    public void setPauseCaching(boolean pauseCaching) {
        this.pauseCaching = pauseCaching;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Configuration that = (Configuration) o;
        return darkTheme == that.darkTheme && pauseFiltering == that.pauseFiltering && pauseCaching == that.pauseCaching && Objects.equals(dbId, that.dbId) && Objects.equals(playerPath1, that.playerPath1) && Objects.equals(playerPath2, that.playerPath2) && Objects.equals(playerPath3, that.playerPath3) && Objects.equals(defaultPlayerPath, that.defaultPlayerPath) && Objects.equals(filterCategoriesList, that.filterCategoriesList) && Objects.equals(filterChannelsList, that.filterChannelsList) && Objects.equals(fontFamily, that.fontFamily) && Objects.equals(fontSize, that.fontSize) && Objects.equals(fontWeight, that.fontWeight) && Objects.equals(serverPort, that.serverPort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dbId, playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, fontFamily, fontSize, fontWeight, serverPort, darkTheme, pauseFiltering, pauseCaching);
    }

    @Override
    public String toString() {
        return "Configuration{" +
                "dbId='" + dbId + '\'' +
                ", playerPath1='" + playerPath1 + '\'' +
                ", playerPath2='" + playerPath2 + '\'' +
                ", playerPath3='" + playerPath3 + '\'' +
                ", defaultPlayerPath='" + defaultPlayerPath + '\'' +
                ", filterCategoriesList='" + filterCategoriesList + '\'' +
                ", filterChannelsList='" + filterChannelsList + '\'' +
                ", fontFamily='" + fontFamily + '\'' +
                ", fontSize='" + fontSize + '\'' +
                ", fontWeight='" + fontWeight + '\'' +
                ", serverPort='" + serverPort + '\'' +
                ", darkTheme=" + darkTheme +
                ", pauseFiltering=" + pauseFiltering +
                ", pauseCaching=" + pauseCaching +
                '}';
    }

    @Override
    public String toJson() {
        return "{" +
                "        \"dbId\":\"" + dbId + "\"" +
                ",         \"playerPath1\":\"" + safeJson(playerPath1) + "\"" +
                ",         \"playerPath2\":\"" + safeJson(playerPath2) + "\"" +
                ",         \"playerPath3\":\"" + safeJson(playerPath3) + "\"" +
                ",         \"defaultPlayerPath\":\"" + safeJson(defaultPlayerPath) + "\"" +
                ",         \"filterCategoriesList\":\"" + safeJson(filterCategoriesList) + "\"" +
                ",         \"filterChannelsList\":\"" + safeJson(filterChannelsList) + "\"" +
                ",         \"fontFamily\":\"" + safeJson(fontFamily) + "\"" +
                ",         \"fontSize\":\"" + safeJson(fontSize) + "\"" +
                ",         \"fontWeight\":\"" + safeJson(fontWeight) + "\"" +
                ",         \"serverPort\":\"" + safeJson(serverPort) + "\"" +
                ",         \"darkTheme\":\"" + darkTheme + "\"" +
                ",         \"pauseFiltering\":\"" + pauseFiltering + "\"" +
                ",         \"pauseCaching\":\"" + pauseCaching + "\"" +
                "}";
    }
}

