package com.uiptv.shared;


import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class EpisodeList extends BaseJson {
    private SeasonInfo seasonInfo;
    private transient List<Episode> episodes = new ArrayList<>();
}
