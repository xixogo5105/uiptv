package com.uiptv.shared;


import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EpisodeList extends BaseJson {
    public SeasonInfo seasonInfo;
    public List<Episode> episodes = new ArrayList<>();
}
