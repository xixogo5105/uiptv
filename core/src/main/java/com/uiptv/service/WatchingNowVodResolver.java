package com.uiptv.service;

import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class WatchingNowVodResolver {

    public List<VodRow> resolveAll() {
        List<VodRow> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            rows.addAll(resolveForAccount(account));
        }
        return rows;
    }

    public List<VodRow> resolveForAccount(Account account) {
        List<VodRow> rows = new ArrayList<>();
        if (account == null || isBlank(account.getDbId())) {
            return rows;
        }
        for (VodWatchState state : VodWatchStateService.getInstance().getAllByAccount(account.getDbId())) {
            VodRow row = buildRow(account, state);
            if (row != null) {
                rows.add(row);
            }
        }
        return rows;
    }

    private VodRow buildRow(Account account, VodWatchState state) {
        if (account == null || state == null || isBlank(state.getVodId())) {
            return null;
        }
        Channel provider = resolveProviderChannel(account, state);
        VodMetadata providerMetadata = resolveMetadataFromProvider(provider);
        String title = firstNonBlank(state.getVodName(), providerMetadata.name, state.getVodId());
        String logo = firstNonBlank(state.getVodLogo(), providerMetadata.logo);
        String plot = firstNonBlank(providerMetadata.plot, "");
        String releaseDate = firstNonBlank(providerMetadata.releaseDate, "");
        String rating = firstNonBlank(providerMetadata.rating, "");
        String duration = firstNonBlank(providerMetadata.duration, "");
        Channel playbackChannel = mergePlaybackChannel(buildFallbackChannel(state), provider);
        VodMetadata metadata = new VodMetadata(logo, plot, releaseDate, rating, duration);
        return new VodRow(account, state, playbackChannel, title, metadata);
    }

    private VodMetadata resolveMetadataFromProvider(Channel provider) {
        VodMetadataBuilder builder = buildMetadataBuilder(provider);
        applyExtraJsonOverrides(builder, provider == null ? "" : provider.getExtraJson());
        return toVodMetadata(builder);
    }

    private VodMetadataBuilder buildMetadataBuilder(Channel provider) {
        VodMetadataBuilder builder = new VodMetadataBuilder();
        if (provider == null) {
            return builder;
        }
        builder.name = safe(provider.getName());
        builder.logo = safe(provider.getLogo());
        builder.plot = safe(provider.getDescription());
        builder.releaseDate = safe(provider.getReleaseDate());
        builder.rating = safe(provider.getRating());
        builder.duration = safe(provider.getDuration());
        return builder;
    }

    private void applyExtraJsonOverrides(VodMetadataBuilder builder, String extraJson) {
        if (builder == null || isBlank(extraJson)) {
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(extraJson);
            builder.name = preferIfBlank(builder.name, json.optString("name"), json.optString("o_name"));
            builder.logo = preferIfBlank(builder.logo, json.optString("stream_icon"), json.optString("cover_big"), json.optString("cover"));
            builder.plot = preferIfBlank(builder.plot, json.optString("description"), json.optString("plot"), json.optString("overview"));
            builder.releaseDate = preferIfBlank(builder.releaseDate, json.optString("release_date"), json.optString("released"), json.optString("year"));
            builder.rating = preferIfBlank(builder.rating, json.optString("rating_imdb"), json.optString("rating"));
            builder.duration = preferIfBlank(builder.duration, json.optString("duration"), json.optString("runtime"), json.optString("time"));
        } catch (Exception _) {
            // Ignore malformed provider metadata.
        }
    }

    private String preferIfBlank(String current, String... candidates) {
        if (!isBlank(current)) {
            return current;
        }
        return firstNonBlank(candidates);
    }

    private VodMetadata toVodMetadata(VodMetadataBuilder builder) {
        if (builder == null) {
            return new VodMetadata("", "", "", "", "");
        }
        return new VodMetadata(
                safe(builder.logo),
                safe(builder.plot),
                safe(builder.releaseDate),
                safe(builder.rating),
                safe(builder.duration),
                safe(builder.name)
        );
    }

    private Channel resolveProviderChannel(Account account, VodWatchState state) {
        Channel direct = VodChannelDb.get().getChannelByChannelId(state.getVodId(), safe(state.getCategoryId()), account.getDbId());
        if (direct != null) {
            return direct;
        }
        List<Channel> matches = VodChannelDb.get().getAll(
                " WHERE accountId=? AND channelId=?",
                new String[]{account.getDbId(), state.getVodId()}
        );
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private Channel buildFallbackChannel(VodWatchState state) {
        Channel channel = new Channel();
        channel.setChannelId(state.getVodId());
        channel.setCategoryId(state.getCategoryId());
        channel.setName(state.getVodName());
        channel.setCmd(state.getVodCmd());
        channel.setLogo(state.getVodLogo());
        return channel;
    }

    private Channel mergePlaybackChannel(Channel primary, Channel fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        fillIfBlank(primary::getName, primary::setName, fallback.getName());
        fillIfBlank(primary::getLogo, primary::setLogo, fallback.getLogo());
        fillIfBlank(primary::getCmd, primary::setCmd, fallback.getCmd());
        fillIfBlank(primary::getCmd_1, primary::setCmd_1, fallback.getCmd_1());
        fillIfBlank(primary::getCmd_2, primary::setCmd_2, fallback.getCmd_2());
        fillIfBlank(primary::getCmd_3, primary::setCmd_3, fallback.getCmd_3());
        fillIfBlank(primary::getDrmType, primary::setDrmType, fallback.getDrmType());
        fillIfBlank(primary::getDrmLicenseUrl, primary::setDrmLicenseUrl, fallback.getDrmLicenseUrl());
        fillIfBlank(primary::getClearKeysJson, primary::setClearKeysJson, fallback.getClearKeysJson());
        fillIfBlank(primary::getInputstreamaddon, primary::setInputstreamaddon, fallback.getInputstreamaddon());
        fillIfBlank(primary::getManifestType, primary::setManifestType, fallback.getManifestType());
        fillIfBlank(primary::getSeason, primary::setSeason, fallback.getSeason());
        fillIfBlank(primary::getEpisodeNum, primary::setEpisodeNum, fallback.getEpisodeNum());
        if (isBlank(primary.getCategoryId())) {
            primary.setCategoryId(fallback.getCategoryId());
        }
        return primary;
    }

    private void fillIfBlank(java.util.function.Supplier<String> getter,
                             java.util.function.Consumer<String> setter,
                             String value) {
        if (isBlank(getter.get()) && !isBlank(value)) {
            setter.accept(value);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class VodMetadataBuilder {
        private String logo = "";
        private String plot = "";
        private String releaseDate = "";
        private String rating = "";
        private String duration = "";
        private String name = "";
    }

    public static final class VodRow {
        private final Account account;
        private final VodWatchState state;
        private final Channel playbackChannel;
        private final String displayTitle;
        private final VodMetadata metadata;

        private VodRow(Account account, VodWatchState state, Channel playbackChannel, String displayTitle, VodMetadata metadata) {
            this.account = account;
            this.state = state;
            this.playbackChannel = playbackChannel;
            this.displayTitle = displayTitle;
            this.metadata = metadata;
        }

        public Account getAccount() {
            return account;
        }

        public VodWatchState getState() {
            return state;
        }

        public Channel getPlaybackChannel() {
            return playbackChannel;
        }

        public String getDisplayTitle() {
            return displayTitle;
        }

        public VodMetadata getMetadata() {
            return metadata;
        }
    }

    public static final class VodMetadata {
        private final String logo;
        private final String plot;
        private final String releaseDate;
        private final String rating;
        private final String duration;
        private final String name;

        private VodMetadata(String logo, String plot, String releaseDate, String rating, String duration) {
            this(logo, plot, releaseDate, rating, duration, "");
        }

        private VodMetadata(String logo, String plot, String releaseDate, String rating, String duration, String name) {
            this.logo = logo;
            this.plot = plot;
            this.releaseDate = releaseDate;
            this.rating = rating;
            this.duration = duration;
            this.name = name;
        }

        public String getLogo() {
            return logo;
        }

        public String getPlot() {
            return plot;
        }

        public String getReleaseDate() {
            return releaseDate;
        }

        public String getRating() {
            return rating;
        }

        public String getDuration() {
            return duration;
        }

        public String getName() {
            return name;
        }
    }
}
