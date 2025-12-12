package com.uiptv.shared;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import static com.uiptv.util.StringUtils.isBlank;

@Data
@NoArgsConstructor
@EqualsAndHashCode(of = "groupTitle", callSuper = false)
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

    public String getGroupTitle() {
        return isBlank(groupTitle) ? "All" : groupTitle;
    }
}
