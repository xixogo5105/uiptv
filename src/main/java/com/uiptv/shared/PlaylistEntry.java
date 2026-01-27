package com.uiptv.shared;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Map;

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
    private String drmType;
    private String drmLicenseUrl;
    private Map<String, String> clearKeys;
    private String inputstreamaddon;
    private String manifestType; // New field for manifest_type

    public PlaylistEntry(String id, String groupTitle, String title, String playlistEntry, String logo) {
        this.id = id;
        this.groupTitle = groupTitle;
        this.title = title;
        this.playlistEntry = playlistEntry;
        this.logo = logo;
    }

    public PlaylistEntry(String id, String groupTitle, String title, String playlistEntry, String logo, String drmType, String drmLicenseUrl, Map<String, String> clearKeys, String inputstreamaddon, String manifestType) {
        this.id = id;
        this.groupTitle = groupTitle;
        this.title = title;
        this.playlistEntry = playlistEntry;
        this.logo = logo;
        this.drmType = drmType;
        this.drmLicenseUrl = drmLicenseUrl;
        this.clearKeys = clearKeys;
        this.inputstreamaddon = inputstreamaddon;
        this.manifestType = manifestType;
    }

    public String getGroupTitle() {
        return isBlank(groupTitle) ? "All" : groupTitle;
    }
}
