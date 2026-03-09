package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchingNowActionMenuTest {

    @Test
    void episodeStyleActions_forNewItem_addsWatchingNowBeforePlayers() {
        List<WatchingNowActionMenu.ActionDescriptor> actions = WatchingNowActionMenu.buildEpisodeStyleActions(
                false,
                List.of(new PlaybackUIService.PlayerOption("Embedded", "embedded"))
        );

        assertEquals(List.of(
                WatchingNowActionMenu.ActionKind.WATCHING_NOW,
                WatchingNowActionMenu.ActionKind.SEPARATOR,
                WatchingNowActionMenu.ActionKind.PLAYER
        ), actions.stream().map(WatchingNowActionMenu.ActionDescriptor::kind).toList());
    }

    @Test
    void episodeStyleActions_forSavedItem_addsRemoveAfterPlayers() {
        List<WatchingNowActionMenu.ActionDescriptor> actions = WatchingNowActionMenu.buildEpisodeStyleActions(
                true,
                List.of(
                        new PlaybackUIService.PlayerOption("Embedded", "embedded"),
                        new PlaybackUIService.PlayerOption("Browser", "browser")
                )
        );

        assertEquals(List.of(
                WatchingNowActionMenu.ActionKind.PLAYER,
                WatchingNowActionMenu.ActionKind.PLAYER,
                WatchingNowActionMenu.ActionKind.SEPARATOR,
                WatchingNowActionMenu.ActionKind.REMOVE_WATCHING_NOW
        ), actions.stream().map(WatchingNowActionMenu.ActionDescriptor::kind).toList());
    }
}
