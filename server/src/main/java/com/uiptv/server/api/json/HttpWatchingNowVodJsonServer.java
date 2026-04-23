package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
import com.uiptv.service.WatchingNowVodResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


public class HttpWatchingNowVodJsonServer implements HttpHandler {
    private final WatchingNowVodResolver resolver = new WatchingNowVodResolver();

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Allow", "GET");
            ex.sendResponseHeaders(405, -1);
            return;
        }
        byte[] responseBytes = buildPayload().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }

    private String buildPayload() {
        List<VodRow> rows = new ArrayList<>();
        for (WatchingNowVodResolver.VodRow row : resolver.resolveAll()) {
            rows.add(toVodRow(row));
        }
        rows.sort(Comparator.comparingLong((VodRow row) -> row.updatedAt).reversed()
                .thenComparing(row -> row.vodName, String.CASE_INSENSITIVE_ORDER));
        JSONArray payload = new JSONArray();
        for (VodRow row : rows) {
            payload.put(row.toJson());
        }
        return payload.toString();
    }

    private VodRow toVodRow(WatchingNowVodResolver.VodRow row) {
        return new VodRow(row.getAccount(), row.getState(), row.getPlaybackChannel(), new VodMetadata(
                row.getDisplayTitle(),
                row.getMetadata().getLogo(),
                row.getMetadata().getPlot(),
                row.getMetadata().getReleaseDate(),
                row.getMetadata().getRating(),
                row.getMetadata().getDuration()
        ));
    }

    private static final class VodRow {
        private final String accountId;
        private final String accountName;
        private final String accountType;
        private final String categoryId;
        private final String vodId;
        private final String vodName;
        private final String vodLogo;
        private final String plot;
        private final String releaseDate;
        private final String rating;
        private final String duration;
        private final long updatedAt;
        private final Channel playItem;

        private VodRow(Account account, VodWatchState state, Channel playItem, VodMetadata metadata) {
            this.accountId = safeStatic(account.getDbId());
            this.accountName = safeStatic(account.getAccountName());
            this.accountType = safeStatic(account.getType() != null ? account.getType().name() : "");
            this.categoryId = safeStatic(state.getCategoryId());
            this.vodId = safeStatic(state.getVodId());
            this.vodName = safeStatic(metadata.title());
            this.vodLogo = safeStatic(metadata.logo());
            this.plot = safeStatic(metadata.plot());
            this.releaseDate = safeStatic(metadata.releaseDate());
            this.rating = safeStatic(metadata.rating());
            this.duration = safeStatic(metadata.duration());
            this.updatedAt = state.getUpdatedAt();
            this.playItem = playItem;
        }

        private static String safeStatic(String value) {
            return value == null ? "" : value;
        }

        private JSONObject toJson() {
            JSONObject item = new JSONObject();
            item.put("accountId", accountId);
            item.put("accountName", accountName);
            item.put("accountType", accountType);
            item.put("categoryId", categoryId);
            item.put("vodId", vodId);
            item.put("vodName", vodName);
            item.put("vodLogo", vodLogo);
            item.put("plot", plot);
            item.put("releaseDate", releaseDate);
            item.put("rating", rating);
            item.put("duration", duration);
            item.put("updatedAt", updatedAt);
            if (playItem != null) {
                item.put("playItem", new JSONObject(playItem.toJson()));
            }
            return item;
        }
    }

    private record VodMetadata(String title, String logo, String plot, String releaseDate, String rating,
                               String duration) {
    }
}
