package com.uiptv.shared;


import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class EpisodeList extends BaseJson {
    public SeasonInfo seasonInfo;
    public transient List<Episode> episodes = new ArrayList<>();
}
