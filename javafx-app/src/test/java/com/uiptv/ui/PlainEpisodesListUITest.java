package com.uiptv.ui;

import com.uiptv.model.Account;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlainEpisodesListUITest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
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

    @Test
    void watchedBadgeUpdatesWhenAccountEpisodeStateChangesAfterRowRendering() throws Exception {
        PlainEpisodesListUI ui = runOnFxThread(() -> {
            PlainEpisodesListUI view = new PlainEpisodesListUI(testAccount(), "Series", "series-1", "category-1");
            EpisodeList episodes = new EpisodeList();
            episodes.getEpisodes().add(testEpisode("episode-1", "1"));
            view.setItems(episodes);
            return view;
        });
        FxTestSupport.waitForFxEvents();

        runOnFxThread(() -> {
            BaseEpisodesListUI.EpisodeItem item = ui.allEpisodeItems.getFirst();
            Method createEpisodeRow = PlainEpisodesListUI.class.getDeclaredMethod(
                    "createEpisodeRow", BaseEpisodesListUI.EpisodeItem.class);
            createEpisodeRow.setAccessible(true);
            Node card = (Node) createEpisodeRow.invoke(ui, item);
            Label watchedBadge = findLabelByText(card, I18n.tr("autoWatching"));

            assertTrue(watchedBadge != null, "Watching badge must be present in the row");
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
}
