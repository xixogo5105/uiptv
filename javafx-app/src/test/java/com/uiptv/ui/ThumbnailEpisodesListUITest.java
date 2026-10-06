package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThumbnailEpisodesListUITest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void watchingNowDetailModeScrollHeightsAreNotArtificiallyConstrained() throws Exception {
        EpisodeScrollSizing sizing = runOnFxThread(() -> {
            ThumbnailEpisodesListUI ui = new ThumbnailEpisodesListUI(testAccount(), "Series", "series-1", "category-1");
            ui.applyWatchingNowDetailStyling();
            contentStack(ui).resize(900, 480);
            updateWatchingNowEpisodeScrollHeight(ui);

            Region cardsFrame = cardsFrame(ui);
            ScrollPane cardsScroll = cardsScroll(ui);
            return new EpisodeScrollSizing(
                    cardsFrame.getLayoutBounds().getHeight(),
                    cardsFrame.getMaxHeight(),
                    cardsScroll.getLayoutBounds().getHeight(),
                    cardsScroll.getMaxHeight(),
                    cardsScroll.getPrefViewportHeight()
            );
        });

        assertTrue(sizing.framePrefHeight() >= 0);
        assertTrue(sizing.framePrefHeight() < 480);
    }

    @Test
    void applyingEpisodeMetadataRebuildsAlreadyRenderedCards() throws Exception {
        ThumbnailEpisodesListUI ui = runOnFxThread(() -> {
            ThumbnailEpisodesListUI view = new ThumbnailEpisodesListUI(testAccount(), "Series", "series-1", "category-1");
            Episode episode = new Episode();
            episode.setId("episode-1");
            episode.setTitle("Episode 1");
            episode.setSeason("1");
            episode.setEpisodeNum("1");
            episode.setCmd("http://example.test/episode-1");
            episode.setInfo(new EpisodeInfo());
            EpisodeList episodeList = new EpisodeList();
            episodeList.getEpisodes().add(episode);
            view.setItems(episodeList);
            view.layout();
            return view;
        });
        FxTestSupport.waitForFxEvents();

        boolean metadataIsVisible = runOnFxThread(() -> {
            assertTrue(!containsLabelText(ui, "Episode plot from TMDB"));

            Method applyMetadata = ThumbnailEpisodesListUI.class.getDeclaredMethod("applyImdbMetadata", JSONObject.class);
            applyMetadata.setAccessible(true);
            JSONObject metadata = new JSONObject()
                    .put("episodesMeta", new JSONArray().put(new JSONObject()
                            .put("season", "1")
                            .put("episodeNum", "1")
                            .put("title", "Episode 1")
                            .put("plot", "Episode plot from TMDB")
                            .put("logo", "https://image.test/episode.jpg")));
            applyMetadata.invoke(ui, metadata);
            ui.layout();
            assertTrue(ui.allEpisodeItems.getFirst().getPlot().contains("Episode plot from TMDB"));
            Field cardsContainerField = ThumbnailEpisodesListUI.class.getDeclaredField("cardsContainer");
            cardsContainerField.setAccessible(true);
            return containsLabelText((Node) cardsContainerField.get(ui), "Episode plot from TMDB");
        });

        assertTrue(metadataIsVisible);

        EpisodeList refreshedEpisodes = new EpisodeList();
        Episode refreshedEpisode = new Episode();
        refreshedEpisode.setId("episode-1");
        refreshedEpisode.setTitle("Episode 1");
        refreshedEpisode.setSeason("1");
        refreshedEpisode.setEpisodeNum("1");
        refreshedEpisode.setCmd("http://example.test/episode-1");
        refreshedEpisode.setInfo(new EpisodeInfo());
        refreshedEpisodes.getEpisodes().add(refreshedEpisode);
        runOnFxThread(() -> {
            ui.setItems(refreshedEpisodes);
            return null;
        });
        FxTestSupport.waitForFxEvents();

        boolean metadataSurvivedEpisodeRefresh = runOnFxThread(() ->
                ui.allEpisodeItems.getFirst().getPlot().contains("Episode plot from TMDB"));
        assertTrue(metadataSurvivedEpisodeRefresh, "later episode-page updates must retain fetched TMDB details");
    }

    @Test
    void staleWatchedStateSnapshotCannotMoveMarkerBackAfterEpisodeSwitch() throws Exception {
        ThumbnailEpisodesListUI ui = runOnFxThread(() -> {
            ThumbnailEpisodesListUI view = new ThumbnailEpisodesListUI(testAccount(), "Series", "series-1", "category-1");
            EpisodeList episodes = new EpisodeList();
            episodes.getEpisodes().add(testEpisode("episode-1", "1"));
            episodes.getEpisodes().add(testEpisode("episode-2", "2"));
            view.setItems(episodes);
            return view;
        });
        FxTestSupport.waitForFxEvents();

        runOnFxThread(() -> {
            Field generationField = BaseEpisodesListUI.class.getDeclaredField("watchedStateRefreshGeneration");
            generationField.setAccessible(true);
            AtomicLong generation = (AtomicLong) generationField.get(ui);
            generation.set(2);

            Method applySnapshot = BaseEpisodesListUI.class.getDeclaredMethod(
                    "applyWatchedStateSnapshot", long.class, SeriesWatchState.class);
            applySnapshot.setAccessible(true);
            Method applyImmediately = BaseEpisodesListUI.class.getDeclaredMethod(
                    "applyWatchedEpisodeImmediately", BaseEpisodesListUI.EpisodeItem.class);
            applyImmediately.setAccessible(true);

            applySnapshot.invoke(ui, 1L, watchedState("episode-1", "1"));
            assertFalse(ui.allEpisodeItems.get(0).isWatched());
            assertFalse(ui.allEpisodeItems.get(1).isWatched());

            applyImmediately.invoke(ui, ui.allEpisodeItems.get(1));
            assertFalse(ui.allEpisodeItems.get(0).isWatched());
            assertTrue(ui.allEpisodeItems.get(1).isWatched());

            applySnapshot.invoke(ui, 1L, watchedState("episode-1", "1"));
            assertFalse(ui.allEpisodeItems.get(0).isWatched());
            assertTrue(ui.allEpisodeItems.get(1).isWatched());
            return null;
        });
    }

    @Test
    void watchedBadgeUpdatesWhenAccountEpisodeStateChangesAfterCardRendering() throws Exception {
        ThumbnailEpisodesListUI ui = runOnFxThread(() -> {
            ThumbnailEpisodesListUI view = new ThumbnailEpisodesListUI(testAccount(), "Series", "series-1", "category-1");
            EpisodeList episodes = new EpisodeList();
            episodes.getEpisodes().add(testEpisode("episode-1", "1"));
            view.setItems(episodes);
            return view;
        });
        FxTestSupport.waitForFxEvents();

        runOnFxThread(() -> {
            BaseEpisodesListUI.EpisodeItem item = ui.allEpisodeItems.getFirst();
            Method createEpisodeCard = ThumbnailEpisodesListUI.class.getDeclaredMethod(
                    "createEpisodeCard", BaseEpisodesListUI.EpisodeItem.class);
            createEpisodeCard.setAccessible(true);
            Node card = (Node) createEpisodeCard.invoke(ui, item);
            Label watchedBadge = findLabelByText(card, I18n.tr("autoWatching"));

            assertTrue(watchedBadge != null);
            assertFalse(watchedBadge.isVisible());
            assertFalse(watchedBadge.isManaged());

            item.setWatched(true);
            assertTrue(watchedBadge.isVisible());
            assertTrue(watchedBadge.isManaged());

            item.setWatched(false);
            assertFalse(watchedBadge.isVisible());
            assertFalse(watchedBadge.isManaged());
            return null;
        });
    }

    private static Account testAccount() {
        Account account = new Account();
        account.setDbId("account-1");
        account.setAccountName("Account");
        account.setType(AccountType.XTREME_API);
        return account;
    }

    private static Episode testEpisode(String id, String episodeNum) {
        Episode episode = new Episode();
        episode.setId(id);
        episode.setTitle("Episode " + episodeNum);
        episode.setSeason("1");
        episode.setEpisodeNum(episodeNum);
        episode.setCmd("http://example.test/" + id);
        episode.setInfo(new EpisodeInfo());
        return episode;
    }

    private static SeriesWatchState watchedState(String episodeId, String episodeNum) {
        SeriesWatchState state = new SeriesWatchState();
        state.setEpisodeId(episodeId);
        state.setEpisodeName("Episode " + episodeNum);
        state.setSeason("1");
        state.setEpisodeNum(Integer.parseInt(episodeNum));
        return state;
    }

    private static StackPane contentStack(ThumbnailEpisodesListUI ui) throws Exception {
        Field field = BaseEpisodesListUI.class.getDeclaredField("contentStack");
        field.setAccessible(true);
        return (StackPane) field.get(ui);
    }

    private static Region cardsFrame(ThumbnailEpisodesListUI ui) throws Exception {
        Field field = ThumbnailEpisodesListUI.class.getDeclaredField("cardsFrame");
        field.setAccessible(true);
        return (Region) field.get(ui);
    }

    private static ScrollPane cardsScroll(ThumbnailEpisodesListUI ui) throws Exception {
        Field field = ThumbnailEpisodesListUI.class.getDeclaredField("cardsScroll");
        field.setAccessible(true);
        return (ScrollPane) field.get(ui);
    }

    private static void updateWatchingNowEpisodeScrollHeight(ThumbnailEpisodesListUI ui) throws Exception {
        Method method = ThumbnailEpisodesListUI.class.getDeclaredMethod("updateWatchingNowEpisodeScrollHeight");
        method.setAccessible(true);
        method.invoke(ui);
    }

    private static boolean containsLabelText(Node node, String expectedText) {
        if (node instanceof Label label && expectedText.equals(label.getText())) {
            return true;
        }
        if (node instanceof Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .anyMatch(child -> containsLabelText(child, expectedText));
        }
        return false;
    }

    private static Label findLabelByText(Node node, String expectedText) {
        if (node instanceof Label label && expectedText.equals(label.getText())) {
            return label;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Label match = findLabelByText(child, expectedText);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private record EpisodeScrollSizing(double framePrefHeight,
                                       double frameMaxHeight,
                                       double scrollPrefHeight,
                                       double scrollMaxHeight,
                                       double scrollPrefViewportHeight) {
    }
}
