package com.uiptv.model;


import java.util.ArrayList;
import java.util.List;

public class EpisodeList extends BaseJson {
    public SeasonInfo seasonInfo;
    public List<Episode> episodes = new ArrayList<>();
}

