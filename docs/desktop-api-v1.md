# Desktop JSON API v1

The Flutter desktop client talks to the Java application through `/api/v1`.
The server creates a dedicated `127.0.0.1` connector on an ephemeral port and
generates a random bearer token for the Java process. The launcher passes both
values to Flutter at startup. The configured LAN/web connector remains separate.
Every `/api/v1/*` request requires the bearer token.

## Implemented desktop contract

All IDs named `dbId` are local database row identifiers. Flutter sends the
selected account, category, and channel `dbId` values back to the API; provider
IDs and provider playback commands are not part of the catalogue DTOs.

| Method and path | Request | Response |
|---|---|---|
| `GET /api/v1/health` | None | `{ "status": "ok", "apiVersion": "v1" }` |
| `GET /api/v1/accounts` | None | Array of safe summaries: `dbId`, `accountName`, `type`, `typeDisplay`, `pinToTop`, JavaFX pin-vector paths/colors, cached `categoryCount`/`channelCount`, and formatted `expiryDate`/`expiryState`, plus cache/playback flags; excludes provider credentials |
| `POST /api/v1/accounts` | Account form fields (`accountName`, `type`, provider fields, cache/playback options) | Creates an account through `AccountService`; returns `201` and editable details |
| `GET /api/v1/accounts/{dbId}` | Local account row ID | Returns editable account details, including credentials, for the authenticated local desktop client only; provider tokens and Xtreme credential JSON are excluded |
| `PUT /api/v1/accounts/{dbId}` | Local account row ID and editable account form fields | Saves through `AccountService` and returns updated details; changing the account name follows JavaFX semantics and may create a new account row |
| `DELETE /api/v1/accounts/{dbId}` | Local account row ID | Deletes the account through `AccountService` |
| `POST /api/v1/accounts/import` | `{ "sourceType": "stalker\|xtreme\|m3u", "data": "...", "groupAccounts": true, "convertM3uToXtreme": false, "startVerificationAfterParsing": true }` | `201` with safe account summaries and optional local `verificationAccountIds`; parses through the existing Java service and does not return credentials or source URLs |
| `GET /api/v1/translations?keys=key1,key2` | Comma-separated allowlisted message keys | JSON key/value map translated using the active Java locale; unsupported keys are rejected |
| `GET /api/v1/about` | None | Version, runtime/platform metadata, and fixed project/help/release URLs; no secrets |
| `GET /api/v1/logs?source=app\|web` | `source` defaults to `app` | `{ "lines": [...] }`; app logs are bounded and redact common credential fields |
| `DELETE /api/v1/logs?source=app\|web` | Optional log source | `{ "status": "ok" }`; clears only the selected app/web log source |
| `GET /api/v1/settings` | None | Desktop settings and locale/zoom options; returns only whether secret values are configured, never their values |
| `PUT /api/v1/settings` | Allowlisted settings fields with validated values | Updated settings plus `restartRequired`; unsupported fields, invalid ports, locale, theme, zoom, and ranges return `400` |
| `GET /api/v1/settings/server` | None | Internal server running state and loopback URLs |
| `POST /api/v1/settings/server` | `{ "action": "start\|stop" }` | Current server state; start failures return `409` |
| `GET /api/v1/backups/current` | None | ZIP archive download created by the existing snapshot service; downloads are limited to 256 MiB |
| `POST /api/v1/backups/current` | ZIP archive bytes (`application/zip`) | Restores through the existing archive validator, refreshes application data, and returns byte counts; uploads are limited to 256 MiB |
| `GET /api/v1/cache-reloads/accounts` | None | Safe account summaries (`dbId`, `accountName`, `type`, `cacheEnabled`) filtered to cache-supported account types |
| `POST /api/v1/cache-reloads` | `{ "accountIds": ["{dbId}"], "failurePolicy": "ask\|carryOn\|markBad\|markBadIgnoreDomain\|stopAll" }` | `202` with a job ID; one reload job runs at a time and accepts at most 200 local account IDs |
| `GET /api/v1/cache-reloads/{jobId}` | Job ID | Job state, per-account progress/status, bounded redacted logs, and a pending global-failure decision if needed |
| `POST /api/v1/cache-reloads/{jobId}/decision` | `{ "decision": "carryOn\|markBad\|markBadIgnoreDomain\|stopAll" }` | Resolves a pending global-failure prompt; ignoring similar URLs is offered only when the account can be safely grouped |
| `POST /api/v1/cache-reloads/{jobId}/stop` | Job ID | Requests stop after the in-flight account finishes |
| `GET /api/v1/filter-lock` | None | Password-configured/unlocked status and lock timing/requirements; never returns the stored hash |
| `POST /api/v1/filter-lock` | `unlock`, `lock`, `setPassword`, `changePassword`, or `clearPassword` action | Updated lock status; password values are input-only |
| `GET /api/v1/filter-lock/filters` | Requires an unlocked filter lock when a password is configured | Protected category/channel keyword lists and restriction state |
| `PUT /api/v1/filter-lock/filters` | Same unlock requirement; only filter-list and restriction fields accepted | Updated protected settings; locked requests return `423` |
| `GET /api/v1/categories?accountId={dbId}&mode=itv\|vod\|series` | Account row ID and optional catalogue mode (`itv` is the default) | Array of `dbId`, `title`, `alias`, and `activeSub` |
| `POST /api/v1/categories/remove` | `{ "accountId": "{dbId}", "mode": "itv", "categoryIds": ["{categoryDbId}"] }` | Removes selected cached category rows and their cached items using local category row IDs; the `all` category sentinel cannot be removed |
| `GET /api/v1/channels?accountId={dbId}&categoryId={categoryDbId}&mode=itv&page=1&pageSize=100&apiOffset=0` | Account/category row IDs, mode and bounded pagination. Page size must be 1–200. `movieId` is accepted for catalogue workflows that need it. | `{ "items": [...], "nextPage": 2, "hasMore": false, "apiOffset": 0 }`; each item contains `dbId`, `name`, `number`, `logo`, `description`, `watched`, `hd`, `status`, `playable`, and safe bookmark-state fields `isBookmarked`/`bookmarkId` (never the provider command or ID). Uncached rows may include an opaque, short-lived `playbackId` |
| `GET /api/v1/bookmark-categories` | None | Array of saved category `{ "id", "name" }` values in Java service order |
| `POST /api/v1/bookmark-categories` | `{ "name": "Cricket" }` | Creates a saved bookmark category; empty names are rejected |
| `DELETE /api/v1/bookmark-categories?categoryId={id}` | Saved category ID | Deletes the category through the existing service; bookmark rows remain available in All |
| `GET /api/v1/bookmarks?offset=0&limit=100&categoryId={id}` | Bounded page (`limit` 1–200), optional saved-category ID | `{ "items": [...], "offset": 0, "limit": 100, "hasMore": false }`; items expose local `dbId`, saved `categoryId`, `channelName`, `categoryTitle`, `accountName`, `logo`, favourite state, and media mode only |
| `PATCH /api/v1/bookmarks` | `{ "bookmarkId": "{dbId or recentId}", "categoryId": "{savedCategoryId}" }`; an empty `categoryId` assigns it to All | Moves a saved favourite or promotes a Recent snapshot into a saved favourite in the selected category |
| `GET /api/v1/bookmarks/recent` | None | `{ "items": [...], "count": n }`; Java-backed recent-play history is separate from saved bookmarks and is bounded by the existing history service |
| `DELETE /api/v1/bookmarks/recent?bookmarkId={id}` | Optional Recent item ID | Removes one Recent item; omit the ID to clear all Java-backed recent-play history |
| `POST /api/v1/bookmarks` | `{ "accountId": "{dbId}", "categoryId": "{categoryDbId}", "channelId": "{channelDbId}", "bookmarkCategoryId": "{savedCategoryId}" }` for a cached live channel | `{ "status": "saved\|exists", "bookmarkId": "{dbId}" }`; Java resolves the channel from its local row and does not accept a provider URL or command from Flutter |
| `DELETE /api/v1/bookmarks?bookmarkId={dbId}` | Local bookmark row ID | `{ "status": "ok" }`; missing IDs return `400` and unknown bookmarks return `404` |
| `GET /api/v1/watching-now?offset=0&limit=100` | Bounded page (`limit` 1–200) | `{ "series": [...], "vod": [...], "hasMoreSeries": false, "hasMoreVod": false }`; summaries include display metadata and an opaque short-lived `playbackId`, but omit provider IDs, commands, and credentials |
| `DELETE /api/v1/watching-now?playbackId={playbackId}` | Opaque short-lived Watching reference | Removes its server-resolved series or VOD watch state; provider IDs are never accepted from Flutter |
| `GET /api/v1/watching-now/details?playbackId={playbackId}` | Opaque series reference | Series title, poster, plot, rating, genre, release date, and episode count |
| `GET /api/v1/watching-now/episodes?playbackId={playbackId}&offset=0&limit=100` | Opaque series reference and bounded page (`limit` 1–200) | `{ "items": [...], "offset": 0, "nextOffset": 100, "hasMore": false }`; episode items contain title, season, episode number, image, synopsis/rating/release date, and an opaque short-lived playback reference |
| `GET /api/v1/player?mode=itv&accountId={dbId}&categoryId={categoryDbId}&channelId={channelDbId}` | Local row IDs for cached live playback; use the row's opaque `playbackId` for an uncached channel | Existing player response containing the resolved `url` and available media/DRM metadata |
| `GET /api/v1/player?watchingNowId={playbackId}&mode=vod\|series` | Opaque resume reference from a Watching Now summary | Existing player response containing the resolved `url`; series resumes the saved last-watched episode |
| `GET /api/v1/player?bookmarkId={dbId}` | Local bookmark row ID | Existing player response with the resolved `url` and a safe account-action `mode` (`itv`, `vod`, or `series`) when available; provider credentials are not included |

Unauthenticated requests receive `401`; an unknown route or account returns JSON
`404`. Catalogue routes accept only `GET`, reject invalid modes and invalid
pagination with JSON `400`, and reject unsupported methods with `405` and an
`Allow` header. Provider/network failures while listing channels return JSON
`502`. Potentially large channel results are bounded and paginated.

## Existing handlers currently routed through v1

The gateway also forwards compatibility requests to existing Java handlers at
`/api/v1/series/episodes`, `/api/v1/series/details`, `/api/v1/vod/details`,
`/api/v1/epg/live`, `/api/v1/epg/player`,
and `/api/v1/config`. The v1 desktop-specific `/api/v1/settings` route is
separate so it does not change the existing web client's configuration contract.
The forwarded compatibility routes still need complete DTOs and capability tests.

## Current gaps

- Account edit/delete/verification and per-MAC verification are not yet exposed
  by the versioned API. Bulk import creates accounts but does not start cache
  verification/reload jobs.
- Favourites can be listed by saved category, played by opaque bookmark row ID,
  moved between categories, removed, and added from cached live catalogue rows
  using local database IDs. Recent playback is separately listed, removed, or
  cleared. Category create/delete exists;
  rename/reorder and non-cached channel bookmark creation remain unavailable.
- Watching Now has safe, bounded series/VOD summaries, season-filtered episode
  browsing, server-resolved removal, and series metadata. Flutter resumes a
  summary or plays an episode using its opaque reference; watch-state updates
  remain unavailable through desktop operations.
- Playback response headers and a dedicated request DTO are not yet included.
- Update checks use the official GitHub latest-release endpoint directly from
  Flutter; plain-text server mode is not exposed through the desktop API yet.
- The forwarded compatibility routes need explicit DTOs and capability tests.
- JavaFX has not yet been converted to this API client boundary.
- A complete OpenAPI description and consistent error envelope remain pending.

The API is therefore a working migration foundation, not a feature-complete
replacement for the JavaFX-to-core boundary.
