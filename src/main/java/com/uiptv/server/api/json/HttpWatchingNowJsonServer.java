package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.WatchingNowSeriesResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpWatchingNowJsonServer implements HttpHandler {
    private final WatchingNowSeriesResolver resolver = new WatchingNowSeriesResolver();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        generateJsonResponse(ex, toJson(buildRows()));
    }

    private String toJson(List<PanelRow> rows) {
        JSONArray payload = new JSONArray();
        for (PanelRow row : rows) {
            payload.put(row.toJson());
        }
        return payload.toString();
    }

    private List<PanelRow> buildRows() {
        List<PanelRow> rows = new ArrayList<>();
        for (WatchingNowSeriesResolver.SeriesRow row : resolver.resolveAll()) {
            SeriesWatchState state = row.getState();
            Account account = row.getAccount();
            rows.add(new PanelRow(
                    safe(account.getDbId()),
                    safe(account.getAccountName()),
                    safe(account.getType() != null ? account.getType().name() : ""),
                    safe(state.getCategoryId()),
                    safe(row.getCategoryDbId()),
                    safe(state.getSeriesId()),
                    safe(state.getEpisodeId()),
                    safe(state.getEpisodeName()),
                    safe(state.getSeason()),
                    state.getEpisodeNum(),
                    safe(row.getSeriesTitle()),
                    safe(row.getSeriesPoster()),
                    state.getUpdatedAt()
            ));
        }
        rows.sort(
                Comparator.comparingLong((PanelRow row) -> row.updatedAt).reversed()
                        .thenComparing(row -> safe(row.seriesTitle), String.CASE_INSENSITIVE_ORDER)
        );
        return rows;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class PanelRow {
        private final String accountId;
        private final String accountName;
        private final String accountType;
        private final String categoryId;
        private final String categoryDbId;
        private final String seriesId;
        private final String episodeId;
        private final String episodeName;
        private final String season;
        private final int episodeNum;
        private final String seriesTitle;
        private final String seriesPoster;
        private final long updatedAt;

        @SuppressWarnings("java:S107")
        private PanelRow(String accountId,
                         String accountName,
                         String accountType,
                         String categoryId,
                         String categoryDbId,
                         String seriesId,
                         String episodeId,
                         String episodeName,
                         String season,
                         int episodeNum,
                         String seriesTitle,
                         String seriesPoster,
                         long updatedAt) {
            this.accountId = accountId;
            this.accountName = accountName;
            this.accountType = accountType;
            this.categoryId = categoryId;
            this.categoryDbId = categoryDbId;
            this.seriesId = seriesId;
            this.episodeId = episodeId;
            this.episodeName = episodeName;
            this.season = season;
            this.episodeNum = episodeNum;
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.updatedAt = updatedAt;
        }

        private JSONObject toJson() {
            JSONObject row = new JSONObject();
            row.put("key", accountId + "|" + seriesId);
            row.put("accountId", accountId);
            row.put("accountName", accountName);
            row.put("accountType", accountType);
            row.put("categoryId", categoryId);
            row.put("categoryDbId", categoryDbId);
            row.put("seriesId", seriesId);
            row.put("episodeId", episodeId);
            row.put("episodeName", episodeName);
            row.put("season", season);
            row.put("episodeNum", episodeNum);
            row.put("seriesTitle", seriesTitle);
            row.put("seriesPoster", seriesPoster);
            row.put("updatedAt", updatedAt);
            return row;
        }
    }
}
