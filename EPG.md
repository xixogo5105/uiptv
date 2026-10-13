# Electronic programme guide

The **EPG** field in account settings accepts an XMLTV HTTP/HTTPS URL or a local XMLTV file path, including gzip-compressed files. It is available for Stalker, Xtream, local M3U, and remote M3U accounts.

For M3U accounts, leaving this field empty enables discovery from the playlist header's `x-tvg-url`, `url-tvg`, or `tvg-url` attribute. Relative sources resolve against the playlist location. A manually entered source takes priority. When a header contains several comma-separated sources, the first is used.

Use the gear menu to **Enable EPG mode**. Bookmarks then shows the current bookmark group's live channels across a shared time axis, while an account's live channel page shows the current category's channels. Search and existing category filters remain active. **Disable EPG mode** restores the ordinary lists. The switch applies across these views for the current application session; it starts disabled. Watching Now contains only Series and VOD.

Double-click a channel or programme to play it on desktop; the web retains its ordinary single-click playback. A single desktop click selects programme details. Both guides show the broadcasting indicator beside the current channel. Use the arrows to browse six-hour windows and **Now** to return to the current window. Programme details and start/end times appear in tooltips; client times are local. In narrow player mode, Now & Next also appears below the video when guide data is available. The web gear labels use the configured application language, falling back to the browser language.

Matching uses the playlist/provider channel ID first. If it does not match, a unique, case-insensitive XMLTV display name may match the channel name. Region and quality suffixes are retained, and ambiguous names do not match. The local database ID is never used as an XMLTV identifier.

## Current scope

- Desktop Now/Next uses wrapping programme cards without an inner scrollbar; compact single-column mode keeps full titles and offers a programme-details link. Both guides wrap programme titles, offer timeline zoom, and show the selected programme's full title above the timeline.
- Channel logos in both guides follow the existing thumbnail setting. Plain-text mode omits logos.
- Shared XMLTV support for desktop and web; Now/Next also appears in live channel cards.
- In-memory cache for up to eight guides, refreshed on use after six hours. Failures retain the previous guide and retry after five minutes. Desktop and web Live views refresh while open.
- Streaming parsing, disabled external XML entities/DTD loading, request timeouts, a 100 MiB decompressed size limit, and a 200,000-programme limit per guide. Retained programmes cover roughly the previous day through the next seven days.
- Live defaults to channels with current or upcoming guide data. **Show all channels** reveals channels without guide data, with an empty-guide message. Normal account and bookmark lists retain every channel. No worldwide source is bundled or downloaded by default.
- Native Stalker/Xtream schedule APIs, persistent SQLite guide storage, and manual alias editing are future work. These accounts currently use the same manual XMLTV field and conservative matching.

## Validation

```sh
./mvnw -pl javafx-app -am -Dtest=EpgApplicationServiceTest,ProviderIdentityContractTest,HttpLiveGuideJsonServerTest,HttpPlayerEpgJsonServerTest,HttpChannelJsonServerTest,HttpWebChannelJsonServerTest,LiveGuideUITest,WatchingNowUITest,ManageAccountUILayoutTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The tests use isolated databases and sample schedules. Rendering previews contain illustrative data, not live broadcasts.
