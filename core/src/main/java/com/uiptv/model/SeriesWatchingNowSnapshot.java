package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SeriesWatchingNowSnapshot extends BaseJson {
    private String dbId;
    private String accountId;
    private String categoryId;
    private String seriesId;
    private String categoryDbId;
    private String seriesTitle;
    private String seriesPoster;
    private String episodesJson;
    private long updatedAt;
}
