package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Bookmark extends BaseJson {
    private String dbId, accountName, categoryTitle, channelId, channelName, cmd, serverPortalUrl, categoryId;

    public Bookmark(String accountName, String categoryTitle, String channelId, String channelName, String cmd, String serverPortalUrl) {
        this.accountName = accountName;
        this.categoryTitle = categoryTitle;
        this.channelId = channelId;
        this.channelName = channelName;
        this.cmd = cmd;
        this.serverPortalUrl = serverPortalUrl;
    }
}
