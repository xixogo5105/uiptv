package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Channel extends BaseJson {
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
