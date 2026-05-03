# UIPTV CSS Application Guide

This guide describes how desktop JavaFX styles are resolved in UIPTV and how to safely customize them.

This guide applies to the **desktop JavaFX application**. The local-network web app (`web/`) and project website (`website/`) use separate HTML/CSS stacks.

## 1. Theme Source Selection

UIPTV resolves exactly one stylesheet source per theme:

1. User override CSS from database (if provided)
2. Otherwise, built-in resource CSS (`application.css` or `dark-application.css`)

Override lookup is theme-aware:

- Light mode uses `lightThemeCssContent`
- Dark mode uses `darkThemeCssContent`

## 2. How User Overrides Work

In **Settings -> Theme**:

1. Upload a light CSS file
2. Upload a dark CSS file
3. Save settings

UIPTV stores both CSS files in the `ThemeCssOverride` database table and applies the selected one directly as an in-memory stylesheet URL (no filesystem write).

If an override is cleared, UIPTV falls back to built-in resource CSS.

## 3. Exporting Built-in CSS

Users can export baseline templates from Settings:

- `Download Default Light CSS`
- `Download Default Dark CSS`

These files are intended as a safe starting point for custom themes.

## 4. Global Styleability Contract

UIPTV applies automatic style hooks to all rendered JavaFX nodes.

Every node receives:

- `.uiptv-node`
- a type class (example: `.uiptv-button`, `.uiptv-v-box`, `.uiptv-table-view`)
- `.uiptv-control` for controls
- optional id class (example: `.uiptv-id-save-button`) if a node id exists

This is handled by `StyleClassDecorator` and is applied to:

- Main app scene
- Player fullscreen/PiP scene roots

## 5. Naming Guidance for New UI

For new UI/widgets:

1. Keep semantic classes for feature-level styling (example: `.reload-summary-box`)
2. Rely on generated `uiptv-*` classes for global targeting
3. Avoid inline `setStyle(...)`
4. Add class placeholders in both `application.css` and `dark-application.css`

## 6. Fonts and Remote URLs

Yes, remote fonts can load in JavaFX CSS via `@font-face` with `url(...)`, subject to:

- Network availability from the app host
- Remote server availability and TLS compatibility
- Supported font formats by JavaFX runtime

Operational cautions:

- App startup/theme switch can block while remote assets load
- Remote providers can change or remove hosted fonts
- Privacy/security policy may require local packaging instead

Recommended production approach:

1. Bundle fonts locally with the app (or host in trusted LAN path)
2. Reference local URLs in CSS for deterministic rendering

## 7. Recent Areas Worth Retesting

Since `v0.1.10`, several desktop areas have changed enough that custom CSS should be rechecked against them:

- parental lock controls and related filter-management prompts
- published M3U selection views
- remote database sync dialogs and progress feedback
- player configuration/help-link layouts
- account reload/verification flows

## 8. CSS Safety Checklist

Before shipping custom CSS:

1. Validate both light and dark files
2. Test table/list/menus/dialogs/player overlays
3. Verify contrast for danger/prominent/selection states
4. Ensure no required selector was removed accidentally
5. Keep comments for any non-obvious override blocks

## 9. Complete Class Reference

This section lists style classes used across the desktop app and widgets.

### 8.1 Auto-generated global hooks (`StyleClassDecorator`)

These are automatically attached at runtime and should exist in both theme files.

- `.uiptv-node`: Base hook for every JavaFX node.
- `.uiptv-control`: Added to all `Control` descendants.
- `.uiptv-v-box`: `VBox` nodes.
- `.uiptv-h-box`: `HBox` nodes.
- `.uiptv-border-pane`: `BorderPane` nodes.
- `.uiptv-stack-pane`: `StackPane` nodes.
- `.uiptv-flow-pane`: `FlowPane` nodes.
- `.uiptv-tile-pane`: `TilePane` nodes.
- `.uiptv-grid-pane`: `GridPane` nodes.
- `.uiptv-region`: `Region` nodes.
- `.uiptv-group`: `Group` nodes.
- `.uiptv-label`: `Label` nodes.
- `.uiptv-text`: `Text` nodes.
- `.uiptv-text-flow`: `TextFlow` nodes.
- `.uiptv-button`: `Button` nodes.
- `.uiptv-hyperlink`: `Hyperlink` nodes.
- `.uiptv-text-field`: `TextField` nodes.
- `.uiptv-text-area`: `TextArea` nodes.
- `.uiptv-password-field`: `PasswordField` nodes.
- `.uiptv-check-box`: `CheckBox` nodes.
- `.uiptv-radio-button`: `RadioButton` nodes.
- `.uiptv-combo-box`: `ComboBox` nodes.
- `.uiptv-list-view`: `ListView` nodes.
- `.uiptv-list-cell`: `ListCell` nodes.
- `.uiptv-table-view`: `TableView` nodes.
- `.uiptv-table-row-cell`: `TableRow` nodes.
- `.uiptv-table-cell`: `TableCell` nodes.
- `.uiptv-tree-view`: `TreeView` nodes.
- `.uiptv-tree-cell`: `TreeCell` nodes.
- `.uiptv-tree-table-view`: `TreeTableView` nodes.
- `.uiptv-scroll-pane`: `ScrollPane` nodes.
- `.uiptv-tab-pane`: `TabPane` nodes.
- `.uiptv-split-pane`: `SplitPane` nodes.
- `.uiptv-accordion`: `Accordion` nodes.
- `.uiptv-titled-pane`: `TitledPane` nodes.
- `.uiptv-menu-bar`: `MenuBar` nodes.
- `.uiptv-menu-button`: `MenuButton` nodes.
- `.uiptv-pagination`: `Pagination` nodes.
- `.uiptv-slider`: `Slider` nodes.
- `.uiptv-progress-indicator`: `ProgressIndicator` nodes.
- `.uiptv-progress-bar`: `ProgressBar` nodes.
- `.uiptv-separator`: `Separator` nodes.
- `.uiptv-image-view`: `ImageView` nodes.
- `.uiptv-svg-path`: `SVGPath` nodes.
- `.uiptv-id-<node-id>`: Optional hook generated from `node.setId(...)`.

### 8.2 Core app classes

- `.uiptv-card`: Card-style content container.
- `.uiptv-outline-pane`: Outlined sub-section container.
- `.transparent-scroll-pane`: Transparent shell for themed scrolling containers.
- `.strong-label`: Strong emphasis labels in headers/section titles.
- `.dim-label`: Secondary/de-emphasized label text.
- `.empty-state-label`: Empty-state text blocks.
- `.titleWindowsBorder`: Legacy title area style hook.
- `.titulo`: Legacy headline style hook.

### 8.3 Action and severity classes

- `.prominent`: Primary positive action button style.
- `.dangerous`: Dangerous/destructive button style.
- `.danger-link`: Destructive action rendered as link.
- `.danger-menu-item`: Destructive context menu item.
- `.no-dim-disabled`: Prevent reduced opacity when disabled.
- `.dialog-button`: Dialog button fallback class.

### 8.4 Navigation and tabs

- `.nav-back-button`: Back navigation button shell.
- `.nav-back-icon`: Back icon glyph style.
- `.watching-now-detail-tabs`: Watching-now inner tab pane style scope.
- `.watching-now-view-link`: “View episode...” link appearance.

### 8.5 Content cards, badges, and metadata

- `.drm-badge`: DRM badge pill.
- `.small-pill-button`: Compact pill-style button.
- `.imdb-pill`: IMDb badge container.
- `.imdb-pill-value`: IMDb value text.
- `.selected-card`: Selected card background.
- `.selected-card-text`: Selected card text color.
- `.selected-card-link`: Selected card hyperlink color.

### 8.6 Embedded player shell (layout-level)

- `.embedded-player-shell`: Outer shell around embedded player.
- `.embedded-player-placeholder`: Placeholder container when idle.
- `.embedded-player-placeholder-icon`: Placeholder icon glyph.

### 8.7 Video player controls and overlays

- `.player-container`: Main player root container.
- `.player-controls-container`: Bottom controls overlay block.
- `.player-icon-button`: Small icon button style.
- `.player-round-control-button`: Circular utility control button.
- `.player-time-label`: Playback time label.
- `.player-error-label`: Playback error text.
- `.player-channel-title`: Current channel headline.
- `.player-stream-info-text`: Secondary stream text in now-playing row.
- `.player-hiddenbar-message-box`: Hidden-controls warning message container.
- `.player-hiddenbar-message-label`: Hidden-controls warning text.
- `.player-hiddenbar-close-icon`: Close icon stroke style for hidden-controls message.
- `.player-tracks-menu`: Audio/subtitle tracks context menu.
- `.player-tracks-menu-item`: Items inside tracks menu.
- `.player-pip-restore-button`: Text fallback styling when PiP restore icon is unavailable.
- `.video-player-slider`: Shared slider style in player UI.

### 8.8 Reload-cache workflow classes

- `.reload-account-row`: Default account row background.
- `.reload-account-row-alt`: Alternating account row background.
- `.reload-summary-box`: Summary panel container.
- `.reload-log-header`: Log header bar.
- `.reload-log-root`: Log panel root container.
- `.reload-warning-label`: Warning label in account-delete prompt.
- `.reload-status-queued`: Status tag for queued jobs.
- `.reload-status-running`: Status tag for running jobs.
- `.reload-status-done`: Status tag for successful jobs.
- `.reload-status-yellow`: Status tag for partial success.
- `.reload-status-bad`: Status tag for failed/bad outcomes.

### 8.9 Progress and async image widgets

- `.progress-bar-container`: Segmented progress wrapper.
- `.progress-bar-segment`: Base segment class.
- `.progress-bar-segment-default`: Default segment fill before status assignment.
- `.success`: Segment success state.
- `.warning`: Segment warning state.
- `.failure`: Segment failure state.
- `.channel-logo-view`: Async image tile container.
- `.default-channel-icon`: Default icon placeholder region.
- `.default-channel-icon-shape`: Placeholder icon fill color hook.

### 8.10 Log/clock utility widgets

- `.log-scroll-pane`: Log pane border shell.
- `.log-message-container`: Log message background container.
- `.log-text`: Log message text.
- `.terminal`: Terminal-like text area style.
- `.clock-face`: Analog clock face stroke/fill.
- `.clock-hand`: Analog clock hand stroke.
- `.valid-text`: Positive validation state text.
- `.invalid-text`: Negative validation state text.
- `.default-text`: Neutral/default status text.

### 8.11 Legacy custom dialog/popup hooks

These classes are kept in CSS as compatibility hooks/placeholders:

- `.custom-dialog-root`
- `.custom-dialog-header`
- `.custom-dialog-title`
- `.custom-dialog-message`
- `.custom-dialog-buttons`
- `.custom-dialog-close`
- `.custom-popup-root`
- `.custom-popup-header`
- `.custom-popup-title`
- `.custom-popup-close`

### 8.12 Update dialog classes

These classes style the release-notification dialog shown by `UpdateChecker`:

- `.update-dialog-stage-root`: Root container for the custom update window.
- `.update-dialog-root`: Root container for update dialog content.
- `.update-dialog-actions`: Footer action row for close/download buttons.
- `.update-dialog-hero`: Highlighted banner area at the top of the update window.
- `.update-dialog-icon`: Circular accent icon container.
- `.update-dialog-icon-glyph`: SVG exclamation glyph inside the accent icon.
- `.update-dialog-badge`: Small release-status badge, such as `NEW RELEASE`.
- `.update-dialog-version-chip`: Version pill shown on the right side of the hero row.
- `.update-dialog-title`: Main update headline.
- `.update-dialog-notes-card`: Framed release-notes panel.
- `.update-dialog-notes-title`: Release-notes heading.
- `.update-dialog-notes-area`: Scrollable text area containing the release notes.

### 8.13 JavaFX structural selectors used by themes

These are framework selectors (not app-owned class names) and are intentionally themed:

- `.root`
- `.label`
- `.button`
- `.text-field`
- `.text-area`
- `.table-view`
- `.table-row-cell`
- `.list-cell`
- `.tab-pane > .tab-content-area`
- `.scroll-bar .thumb`
- `.scroll-bar .track`
- `.separator *.line`
- `.menu-bar`
- `.menu-item`
- `.dialog-pane`
- `.dialog-pane > .header-panel`

### 8.14 Notes for custom themes

1. Keep both theme files in sync for selector availability.
2. Prefer overriding existing hooks instead of introducing ad-hoc selectors.
3. Use `.uiptv-node` + type hooks for broad rules and semantic classes for feature-level rules.
4. Avoid selectors based on transient child order; prefer stable class names.
