package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PublishedM3uChannelSelection extends BaseJson {
    private String dbId;
    private String accountId;
    private String categoryName;
    private String channelId;
    private boolean selected;

    public PublishedM3uChannelSelection(String accountId, String categoryName, String channelId, boolean selected) {
        this.accountId = accountId;
        this.categoryName = categoryName;
        this.channelId = channelId;
        this.selected = selected;
    }
}
