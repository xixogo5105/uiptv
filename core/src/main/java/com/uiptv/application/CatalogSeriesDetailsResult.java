package com.uiptv.application;

import org.json.JSONArray;
import org.json.JSONObject;

public record CatalogSeriesDetailsResult(JSONObject seasonInfo, JSONArray episodes, JSONArray episodesMeta) {
}
