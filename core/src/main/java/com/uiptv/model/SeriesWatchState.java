package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SeriesWatchState extends BaseJson {
    private String dbId;
    private String accountId;
    private String mode;
    private String categoryId;
    private String seriesId;
    private String episodeId;
    private String episodeName;
    private String season;
    private int episodeNum;
    private long updatedAt;
    private String source;
    private String seriesCategorySnapshot;
    private String seriesChannelSnapshot;
    private String seriesEpisodeSnapshot;
}
