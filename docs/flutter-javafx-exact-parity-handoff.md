# UIPTV Flutter Desktop: Exact JavaFX Parity Handoff

## Purpose

This document is the implementation contract for the Flutter desktop migration.
The task is to reproduce the existing JavaFX desktop application precisely on
macOS, Windows and Linux while retaining the existing Java backend and its
local, authenticated JSON API. Do not introduce a new product design, a new
navigation model, alternative terminology, invented cards, or substitute
workflows.

The JavaFX UI is the behavioural source of truth. The web UI supplies the
preferred colours, surface treatment and responsive card styling wherever
JavaFX is constrained by its native player surface. A Flutter feature counts as
complete only after its layout, data source, interactions and edge cases match
the relevant JavaFX implementation.

## Reference screenshots

The two images below show the required desktop layouts for the same Favourites
screen with embedded playback.

### Wide embedded player mode

Catalogue content occupies the left, wide portion of the window. A compact
player panel is fixed on the right. The catalogue stays usable while playback
continues.

![Wide embedded Favourites and player](/var/folders/zk/wr_41ph91tl737l9yvz3vbch0000gn/T/codex-clipboard-20020505-9921-4766-a7ba-2499170dc1b5.png)

### Narrow embedded player mode

The player is the dominant region on the right and catalogue becomes a slim,
single-column card list on the left. The player still contains the same
metadata and control bar. This is not the floating player mode.

![Narrow embedded Favourites and player](/var/folders/zk/wr_41ph91tl737l9yvz3vbch0000gn/T/codex-clipboard-bb9d17d5-4182-43bc-a175-c634f5b39cf1.png)

Earlier supplied reference screenshots show the non-playing Favourites page,
the settings menu, desktop spacing, navigation labels, category pills and
card-grid density. Treat all supplied screenshots as acceptance references.

## Required source reading before editing

Read these files in full before changing the equivalent Flutter area. Search
for supporting widgets and service calls when behaviour is not clear.

### Mandatory implementation discipline

Do not guess. Do not infer intended behaviour from a class name, a partial
screen capture, a generic Flutter pattern, or personal design preference.

Before changing any Flutter screen or control, the agent must:

1. Read the full corresponding JavaFX class and the directly used player/widget
   classes.
2. Trace the data source, service call and persistence operation behind each
   visible field and user action.
3. Search for event handlers for click, double-click, keyboard, focus, hover,
   resize, context-menu and error states.
4. Inspect the JavaFX CSS and supplied screenshots for the exact layout and
   visual state.
5. Write down the parity behaviour being implemented, then implement only that
   behaviour.

If the source does not establish the behaviour, inspect the next layer of
JavaFX code or report the uncertainty. Do not fill the gap with an invented
Flutter workflow. An implementation that looks plausible but differs from
JavaFX is a defect.

| JavaFX source | Flutter feature it defines |
| --- | --- |
| `javafx-app/src/main/java/com/uiptv/player/VlcVideoPlayer.java` | Native playback lifecycle, rendering, aspect ratio and stream state |
| `javafx-app/src/main/java/com/uiptv/player/BaseVideoPlayer.java` | Player layouts, fullscreen, PIP, overlay controls, keyboard/mouse behaviour, EPG summary, channel navigation |
| `javafx-app/src/main/java/com/uiptv/ui/BookmarkChannelListUI.java` | Favourites data, fixed category pills, Recent handling, card actions, search, sorting, Clear and Manage |
| `javafx-app/src/main/java/com/uiptv/ui/CategoryListUI.java` | Account/category browsing, modes, search, card/table behaviour, navigation |
| `javafx-app/src/main/java/com/uiptv/ui/ChannelListUI.java` | Channel/VOD/series cards, playback activation, favourite state, EPG cards, progress and pagination |
| `javafx-app/src/main/java/com/uiptv/ui/WatchingNowUI.java` | Watching Now tab model, debounced search and mode switching |
| `javafx-app/src/main/java/com/uiptv/ui/ConfigurationUI.java` | Settings cards, filters, search, switches, dialogs and wide-view preference |
| `javafx-app/src/main/resources/application.css` and `dark-application.css` | Existing sizing, state and theme detail |
| `api-server/src/main/resources/web/css/spa.css` and `shared-player.css` | Preferred responsive web colours, border, shadow and card treatment |

Also inspect the Java application services, database entities and existing JSON
handlers before adding an endpoint. Every UI API request uses local `dbId`
values; provider IDs never cross the Flutter UI boundary.

## Non-negotiable parity rules

1. Flutter is the view layer. Java owns accounts, database access, provider
   calls, playlist refresh, stream resolution, EPG, filters, history and saved
   configuration.
2. Never fabricate labels, tabs, categories, metadata, provider details or
   fallback content from what happened to be returned in a list.
3. Never derive saved Favourites categories from bookmark rows. Load the exact
   `BookmarkCategory` records from `BookmarkService.getAllCategories()`.
4. Preserve JavaFX information architecture: Favourites, Accounts, Watching,
   Settings and their child workflows use the same labels, controls and order.
5. Every user-visible string comes from the existing i18n key/value source.
6. Treat mouse, keyboard, focus, hover, double-click and scroll behaviour as
   product functionality. They are not optional decoration.
7. Do not remove a JavaFX feature because the corresponding v1 API route has
   not been implemented. Add a typed, secured endpoint and test it.
8. Preserve local ID versus provider ID rules from
   `ProviderIdentityContractTest` for every catalogue or playback change.

## Application chrome and global navigation

### Header

Recreate the existing desktop header:

- Favourites, Accounts and Watching have icon and text on ordinary desktop
  widths. Do not reduce them to icon-only controls except at a responsive width
  supported by the original layout.
- The selected page uses the pale blue outline/fill treatment visible in the
  screenshots.
- Settings is the green gear at the right and opens the existing action menu.
- Preserve the menu entries, separators, enabled state and original actions:
  Settings, Import Bulk Accounts, Reload Accounts Cache, Logs, parental-lock
  action, plain-text mode, EPG action, theme action, stay-on-top, lightweight
  mode, Help, Check for updates and About.

### Visual system

Use the web SPA light palette as the Flutter baseline:

- page background `#f7fafd` or white;
- cards white with subtle `#e1e6eb` outlines and restrained shadows;
- selected navigation pale blue with a blue border;
- readable near-black text and muted slate secondary text;
- positive/settings green and error/destructive red match existing UI;
- rounded corners, field padding and typography must match screenshots before
  introducing any new spacing scale.

Support the existing dark and light theme settings. Do not hard-code a Flutter
theme that ignores the Java configuration setting.

## Player: three distinct modes

The Flutter player needs all three modes. These are separate concepts.

| Mode | Required layout | Entry/exit |
| --- | --- | --- |
| Wide embedded | Catalogue wide at left; fixed, narrower player panel on right. See screenshot 1. | Existing layout toggle and saved wide-view preference. |
| Narrow embedded | Narrow single-column catalogue at left; large player dominates the right. See screenshot 2. | Same layout toggle and saved wide-view preference. |
| Floating PIP | Resizable, draggable player surface layered over the normal full catalogue. This is additive, not a replacement for embedded layouts. | PIP control; close hides only the surface unless Stop is explicitly chosen. |

### Player interaction contract

- Double-click the video surface toggles true fullscreen. Double-click again or
  press Escape returns to the prior embedded/PIP state.
- Hovering or moving the pointer over the player reveals a single overlay
  control bar. It hides after the JavaFX-equivalent idle delay and reappears on
  interaction. It must remain visible while pointer is over the bar or menus.
- Right click reveals the control bar.
- Mouse wheel changes volume in player context.
- Keyboard handling must match `BaseVideoPlayer`: Escape exits fullscreen,
  controls do not accidentally activate when focus is in a search field, and
  player keyboard shortcuts work when player focus is active.
- The PIP surface is draggable by a dedicated safe drag region that does not
  steal clicks from video controls. It must remain within window bounds. Add
  resize handling if the Flutter desktop window API supports it cleanly.
- Preserve aspect-fit/fill handling and the user-selected aspect ratio.
- Keep the player alive while changing layout mode, PIP, fullscreen, or page.
  Do not recreate libmpv or reopen the stream merely to move the widget.

### Overlay control bar

Mirror the JavaFX `BaseVideoPlayer.buildUI()` composition and preserve the
ordering. Display controls only when applicable.

1. EPG summary line: formatted `start–stop title` for Now and Next.
2. Now-playing line: channel/VOD title and media/stream information.
3. Main row: play/pause, stop, previous channel, next channel, rewind 15s,
   fast-forward 15s, repeat, reload, fullscreen, PIP, mute, volume slider,
   embedded-layout toggle, aspect ratio, audio/subtitle tracks, hide bar and
   guide button.
4. Time row: seek slider plus time label. Seek is enabled only for VOD/series
   and other seekable media. Live streams must not expose a fake functional
   seek bar.

At narrow control widths, retain the JavaFX responsive rule: move rewind and
fast-forward beside the time slider rather than overflowing the main row.

### Narrow-mode EPG

The narrow embedded layout must show its guide information beside/above the
player exactly as the JavaFX layout does. Use the account and local channel IDs
to call the typed player EPG endpoint. Render Now and Next with local time
ranges. The catalogue must never fetch EPG simply to render channel cards;
only playback and an explicit guide action may do so.

## Favourites

The authoritative implementation is `BookmarkChannelListUI`.

### Data model and pills

Load categories from a new or corrected typed v1 route backed by:

```java
BookmarkService.getInstance().getAllCategories()
```

The route returns stable `{ id, name }` values. Flutter creates pills in this
exact order:

1. `Recent` using the fixed ID `__recently_played_bookmarks__`.
2. Every persisted bookmark category in its service/database order.
3. `All` with a null/empty category ID.

`Recent` is the default selection. It is a playback-history view, not a
category guessed from a bookmark title. `All` includes every bookmark.

### Layout and interaction

- Toolbar: category pill bar at left, Clear and Manage actions at right. It
  wraps responsively using the same breakpoints as `BookmarkChannelListUI`.
- Search field sits beneath the toolbar, spans available width, supports clear,
  and filters the active category immediately.
- Show responsive media cards. Cards carry image/logo, title, account/source
  detail, existing EPG card content where enabled, and any existing progress or
  state markers.
- Do not put favourite-heart icons on cards simply because the card appears in
  Favourites. The JavaFX screen uses a heart only where
  `shouldShowRecentBookmarkHeart(categoryId, bookmarkId)` says a recent item
  needs the existing toggle affordance. Do not invent a remove-heart action.
- Clicking a card starts playback and records/reorders Recent through Java.
- Preserve card keyboard activation, selection, sorting, Manage Categories,
  reorder and Clear Recent confirmations.

## Accounts, Categories and Channels

The source of truth is `CategoryListUI` plus `ChannelListUI`.

### Accounts view

- Use virtualized responsive cards, not a simple `ListView` of rows.
- Card content uses real account data from the API: account name, account type,
  status and pin state where the JavaFX card exposes them. Do not replace the
  information with a generic television icon alone.
- Include the same search row and mode pill bar as JavaFX. Modes are Accounts,
  Live, VOD and Series, with availability determined by the account type and
  API capabilities.
- Account selection transitions to its category cards without losing the
  selected mode or search state unexpectedly.

### Category view

- Retain mode pills, search, back action and category heading.
- Search checks title and category ID as JavaFX does.
- Use responsive cards where JavaFX uses cards; preserve the alternative table
  behavior where it is a user-selected display mode.
- Category activation opens its channel list using account/category local IDs.

### Channel, VOD and series cards

- Preserve actual logo, title, number, provider/account detail, HD, DRM,
  watched/in-progress and EPG markers.
- Favourite controls are present only when the JavaFX channel card exposes
  them. They must call the existing Java operation using local IDs.
- Playback uses the Java-resolved stream response, its headers and its media
  metadata. Flutter does not build provider URLs.
- Pagination and long loads show the existing progress/loading state and must
  not block input.
- Series cards open the Java-backed season and episode flow. Episodes retain
  title, season, episode number, image, progress and selected-stream behavior.

## Watching Now

The current Flutter list implementation is unacceptable. Implement the JavaFX
`WatchingNowUI` model.

- Header identifies Watching Now.
- The filter row combines a Series/VOD pill bar and one search field. The
  search field is the same height as the pills and debounces non-empty input by
  180 ms.
- Keep separate Series and VOD delegates/state. Switching tabs preserves the
  active search query and refreshes only the active delegate.
- Use responsive poster/media cards for both modes, not `ListTile` rows.
- Series cards show series artwork, series title, last episode/season context,
  account/source and resume state. Activating opens existing episode/season
  details or resumes according to Java behavior.
- VOD cards show artwork, title, plot/release/rating/duration as available,
  account/source and progress state. Activating resumes playback.
- Infinite/paged loading must keep sorted recent order and append without
  resetting scroll position.

## Settings

`ConfigurationUI` is a large feature area. Flutter needs responsive settings
cards and the existing search/filter pill bar before it can replace JavaFX.

- Search filters named settings sections.
- Pill filters and theme selector use the existing labels and selected state.
- Preserve player settings, embedded/wide-view setting, theme, thumbnails,
  EPG, account cache controls, server, publishing, imports/exports, remote sync,
  filter lock, parental access and update/about flows.
- Settings that trigger a long operation use Java task/status routes and show
  progress, failure and final state. Flutter must not simulate a completed
  operation locally.

## API work required for parity

Every route is `/api/v1`, loopback-only and bearer-token protected. Use DTOs;
do not return database entities or credentials.

| Capability | Required endpoint contract |
| --- | --- |
| Bookmark categories | `GET /api/v1/bookmark-categories` returns persisted `{id,name}` values only; Flutter adds fixed Recent and All in JavaFX order. |
| Recent bookmarks | Explicit bounded recent endpoint using Java recent-playback service, not inferred bookmark sort order. |
| Bookmark management | Typed category create/rename/delete/reorder and clear-recent operations with confirmation in Flutter. |
| Player EPG | Typed now/next summary and optional schedule response keyed by local account/category/channel or bookmark IDs. |
| Player metadata | Playback response includes title, stream/media info, seekability, tracks and headers needed by libmpv. |
| Accounts/categories | DTOs expose every display-safe JavaFX card field and mode capability. |
| Watching Now | Separate bounded Series/VOD card DTOs and episode details; include safe progress and display metadata. |
| Settings | Typed sections, current values and mutations for every Flutter-exposed JavaFX setting. |

Update API documentation and add integration tests for each route. Never pass
provider secrets, raw provider commands or passwords to Flutter.

## Implementation sequence for the lite agent

1. Stop adding new visual ideas. Create a parity checklist from this document
   and mark a row complete only after manual comparison against JavaFX.
2. Build typed bookmark-category and recent endpoints. Replace Flutter's
   inferred favourites categories before changing the screen further.
3. Finish Favourites: exact pills, Clear/Manage, search, card actions and
   no invented hearts.
4. Implement the three player layouts using one persistent player controller.
   Restore double-click fullscreen, Escape, overlay timing, full control bar,
   guide line and draggable PIP.
5. Build Accounts, Categories and Channels from their JavaFX behaviors,
   including search, modes, metadata and loading state.
6. Replace Watching Now with separate Series/VOD card delegates and debounced
   search.
7. Implement Settings section by section from `ConfigurationUI`.
8. Add platform keyboard/mouse tests, API tests and desktop manual smoke
   scripts. Test both light and dark themes and all three player layouts.

## Acceptance checklist

- [ ] Flutter visual comparison at 1440px, 1920px and a narrow desktop width
      matches JavaFX/web screenshots in hierarchy, density and spacing.
- [ ] Favourites pills are Recent, persisted categories and All from Java data.
- [ ] Favourites has search, Clear and Manage. No unconditional heart icons.
- [ ] Account and category cards show real fields, modes and search.
- [ ] Channel cards retain all JavaFX states and data-derived actions.
- [ ] Watching Now is card-based with Series/VOD pills and debounced search.
- [ ] Wide embedded player matches screenshot 1.
- [ ] Narrow embedded player matches screenshot 2, including EPG detail.
- [ ] Floating PIP is draggable and preserves playback.
- [ ] Double-click fullscreen and Escape work from every player layout.
- [ ] Hover overlay includes all applicable JavaFX controls and respects idle
      visibility.
- [ ] Java resolves every stream; Flutter/libmpv only renders and controls it.
- [ ] API contract and tests cover every visible screen action.
- [ ] JavaFX remains available until all rows above are demonstrated manually.
