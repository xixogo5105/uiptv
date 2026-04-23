package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import java.util.ArrayList;
import java.util.List;

final class WatchingNowActionMenu {
    private WatchingNowActionMenu() {
    }

    static List<ActionDescriptor> buildEpisodeStyleActions(boolean alreadySaved, List<PlaybackUIService.PlayerOption> playerOptions) {
        List<ActionDescriptor> actions = new ArrayList<>();
        if (!alreadySaved) {
            actions.add(ActionDescriptor.watchingNow());
            actions.add(ActionDescriptor.separator());
        }
        appendPlayerActions(actions, playerOptions);
        if (alreadySaved) {
            actions.add(ActionDescriptor.separator());
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
