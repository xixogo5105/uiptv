package com.uiptv.ui;

import java.util.ArrayList;
import java.util.List;

final class WatchingNowActionMenu {
    private WatchingNowActionMenu() {
    }

    static List<ActionDescriptor> buildEpisodeStyleActions(boolean alreadySaved, List<PlaybackUIService.PlayerOption> playerOptions) {
        List<ActionDescriptor> actions = new ArrayList<>();
        appendPlayerActions(actions, playerOptions);
        if (!alreadySaved) {
            appendSeparatorIfNeeded(actions);
            actions.add(ActionDescriptor.watchingNow());
        } else {
            appendSeparatorIfNeeded(actions);
            actions.add(ActionDescriptor.removeWatchingNow());
        }
        return actions;
    }

    private static void appendPlayerActions(List<ActionDescriptor> actions, List<PlaybackUIService.PlayerOption> playerOptions) {
        if (playerOptions == null) {
            return;
        }
        for (PlaybackUIService.PlayerOption option : playerOptions) {
            actions.add(ActionDescriptor.player(option.label(), option.playerPath()));
        }
    }

    private static void appendSeparatorIfNeeded(List<ActionDescriptor> actions) {
        if (!actions.isEmpty()) {
            actions.add(ActionDescriptor.separator());
        }
    }

    record ActionDescriptor(ActionKind kind, String label, String playerPath) {
        static ActionDescriptor watchingNow() {
            return new ActionDescriptor(ActionKind.WATCHING_NOW, null, null);
        }

        static ActionDescriptor separator() {
            return new ActionDescriptor(ActionKind.SEPARATOR, null, null);
        }

        static ActionDescriptor player(String label, String playerPath) {
            return new ActionDescriptor(ActionKind.PLAYER, label, playerPath);
        }

        static ActionDescriptor removeWatchingNow() {
            return new ActionDescriptor(ActionKind.REMOVE_WATCHING_NOW, null, null);
        }
    }

    enum ActionKind {
        WATCHING_NOW,
        SEPARATOR,
        PLAYER,
        REMOVE_WATCHING_NOW
    }
}
