# EPG

EPG has one enable/disable switch on desktop and mobile. Sources belong to an account; there is no global source or refresh/cache configuration.

## Requests

- Starting a live channel fetches that channel's guide for the player.
- Desktop live-channel and bookmark context menus offer **View Guide**.
- Mobile live-channel and bookmark cards offer **View Guide** through their long-press menu.
- The mobile embedded player's edge-swipe guide remains accessible when the result is empty or still loading.
- Cards show no programme summaries and never fetch EPG data while browsing or scrolling.

Stalker and Xtreme native APIs are requested for the selected provider channel ID. Account XMLTV sources are streamed when needed, with M3U header source discovery used only when the account has no explicit source. Only the requested channel's programme list is retained in the open guide/player view. Closing it releases that result; opening it again makes a fresh request.

## Storage and upgrade

EPG responses, aliases, matches, icons, negative results and source failures are not cached in memory, files or the database. Authentication sessions may be reused by provider transport code; they contain no programme data.

Migration `0222_remove_epg_caches.sql` drops the legacy programme, source, alias, mapping, icon, shared-source, name-rule, blacklist and change-journal tables. Desktop and Android compact the database after the upgrade outside the migration transaction. Android also removes legacy downloaded guide files. Account details, account-level EPG sources, catalogues, bookmarks, watching history and the EPG enable flag remain.

The deprecated configuration columns remain inert for database/restore compatibility. They are cleared by the migration and are not exposed in settings or used by guide requests.
