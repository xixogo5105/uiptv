import 'dart:async';
import 'dart:io';
import 'dart:ui' show ImageFilter;

import 'package:flutter/gestures.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';
import 'package:window_manager/window_manager.dart';

import '../api/uiptv_api_client.dart';
import '../player/desktop_player_layout.dart';
import '../player/desktop_player_stream_info.dart';
import '../player/uiptv_player.dart';
import 'desktop_favourites_layout.dart';
import 'desktop_account_browser_layout.dart';
import 'desktop_account_card.dart';
import 'desktop_account_mode_pill_bar.dart';
import 'desktop_responsive_card_grid.dart';
import 'desktop_account_type_filter.dart';
import 'desktop_account_editor.dart';
import 'bulk_account_import_screen.dart';
import 'desktop_cache_reload_screen.dart';
import 'desktop_about_dialog.dart';
import 'desktop_update_available_dialog.dart';
import 'desktop_watching_now_screen.dart';
import 'desktop_logs_screen.dart';
import 'desktop_settings_screen.dart';
import 'desktop_navigation_button.dart';
import '../widgets/desktop_settings_menu.dart';
import '../widgets/desktop_context_menu_region.dart';

enum _DesktopSection {
  favourites,
  accounts,
  watchingNow,
  settings,
  import,
  logs,
  cacheReload,
}

class UiptvDesktopShell extends StatefulWidget {
  const UiptvDesktopShell({
    super.key,
    required this.api,
    this.initialStreamUrl = '',
    this.onThemeModeChanged,
  });

  final UiptvApiClient api;
  final String initialStreamUrl;
  final ValueChanged<String>? onThemeModeChanged;

  @override
  State<UiptvDesktopShell> createState() => _UiptvDesktopShellState();
}

class _UiptvDesktopShellState extends State<UiptvDesktopShell>
    with WindowListener {
  static const _recentBookmarkCategoryId = '__recent_bookmarks__';

  _DesktopSection _section = _DesktopSection.favourites;
  late final UiptvPlayer _player;
  final TextEditingController _streamUrlController = TextEditingController();
  final TextEditingController _catalogueSearchController =
      TextEditingController();
  final TextEditingController _accountSearchController =
      TextEditingController();
  final TextEditingController _favouriteSearchController =
      TextEditingController();
  late Future<List<UiptvAccountSummary>> _accounts;
  late Future<List<UiptvBookmarkCategory>> _bookmarkCategories;
  late Future<List<UiptvBookmarkSummary>> _bookmarks;
  late Future<UiptvWatchingNowSnapshot> _watchingNow;
  UiptvAccountSummary? _selectedAccount;
  UiptvCategorySummary? _selectedCategory;
  bool _showAccountEditor = false;
  String? _editingAccountId;
  String _accountSortMode = 'DEFAULT';
  String _accountTypeFilter = 'ALL';
  Future<List<UiptvCategorySummary>>? _categories;
  Future<UiptvChannelPage>? _channels;
  final List<UiptvChannelSummary> _additionalChannels = [];
  UiptvChannelPage? _lastChannelPage;
  bool _loadingMoreChannels = false;
  bool _loadingMoreWatchingNow = false;
  bool _checkingUpdates = false;
  bool _showPlayer = false;
  bool _playerFloating = false;
  bool _playerFullscreen = false;
  bool _wideView = false;
  bool _stayOnTop = false;
  bool _menuActionBusy = false;
  double _volume = 50;
  bool _playerMuted = true;
  bool _playerHovered = false;
  bool _playerControlsHovered = false;
  Timer? _playerIdleTimer;
  final FocusNode _playerFocusNode = FocusNode(debugLabel: 'UIPTV player');
  bool _playerControlsHidden = false;
  bool _repeatEnabled = false;
  bool _playerGuideLoading = false;
  int _playerGuideRequestGeneration = 0;
  Map<String, dynamic>? _playerGuide;
  String? _playerGuideError;
  int _aspectRatioMode = 0;
  ({String accountId, String categoryId, String channelId})?
  _playerGuideContext;
  String? _playerGuideBookmarkId;
  String? _playerGuideChannelName;
  List<UiptvChannelSummary> _playerChannelQueue = const [];
  int _playerChannelIndex = -1;
  bool _playerChannelNavigationBusy = false;
  String? _playerChannelAccountDbId;
  String? _playerChannelCategoryDbId;
  String _playerChannelMode = 'itv';
  String _selectedMode = 'itv';
  String _favouriteCategory = _recentBookmarkCategoryId;
  Map<String, String> _bookmarkTranslations = const {};
  Map<String, dynamic> _menuSettings = const {};
  List<String> _cacheReloadInitialAccountIds = const [];

  void _refreshPlayerStatus() {
    if (!mounted) {
      return;
    }
    setState(() {});
  }

  Future<void> _loadPlayerLayoutSettings() async {
    try {
      final settings = await widget.api.settings();
      if (mounted) {
        setState(() {
          _menuSettings = settings;
          _wideView = settings['wideView'] as bool? ?? false;
        });
        if (_showPlayer &&
            !_wideView &&
            settings['epgEnabled'] == true &&
            (_playerGuideContext != null || _playerGuideBookmarkId != null)) {
          unawaited(_loadPlayerGuide());
        }
      }
    } catch (_) {
      return;
    }
  }

  Future<void> _toggleWideView() async {
    final previous = _wideView;
    final next = !previous;
    setState(() => _wideView = next);
    try {
      final settings = await widget.api.updateSettings({'wideView': next});
      if (mounted) {
        _settingsSaved(settings);
        if (previous &&
            !next &&
            settings['epgEnabled'] == true &&
            (_playerGuideContext != null || _playerGuideBookmarkId != null)) {
          unawaited(_loadPlayerGuide());
        }
      }
    } catch (_) {
      if (mounted) {
        setState(() => _wideView = previous);
      }
    }
  }

  Future<void> _setPlayerFullscreen(bool fullscreen) async {
    if (_playerFullscreen == fullscreen) {
      return;
    }
    try {
      await windowManager.setFullScreen(fullscreen);
      if (mounted) {
        setState(() {
          _playerFullscreen = fullscreen;
          if (fullscreen) _playerFloating = false;
        });
      }
    } catch (_) {
      return;
    }
  }

  Future<void> _togglePlayerFullscreen() =>
      _setPlayerFullscreen(!_playerFullscreen);

  Future<void> _toggleFloatingPlayer() async {
    final floating = !_playerFloating;
    if (_playerFullscreen) {
      await _setPlayerFullscreen(false);
    }
    if (mounted) {
      setState(() => _playerFloating = floating);
    }
  }

  Future<void> _closePlayer() async {
    if (_playerFullscreen) {
      await _setPlayerFullscreen(false);
    }
    if (mounted) {
      setState(() {
        _showPlayer = false;
        _playerFloating = false;
      });
    }
  }

  void _toggleAspectRatio() {
    setState(() => _aspectRatioMode = (_aspectRatioMode + 1) % 3);
  }

  Future<void> _toggleRepeat() async {
    final enabled = !_repeatEnabled;
    setState(() => _repeatEnabled = enabled);
    await _player.setPlaylistMode(
      enabled ? PlaylistMode.single : PlaylistMode.none,
    );
  }

  Future<Map<String, dynamic>?> _loadPlayerGuide({
    bool reportErrors = false,
  }) async {
    final guideContext = _playerGuideContext;
    final bookmarkId = _playerGuideBookmarkId;
    if (guideContext == null && bookmarkId == null) {
      return null;
    }
    final requestGeneration = ++_playerGuideRequestGeneration;
    setState(() {
      _playerGuideLoading = true;
      _playerGuideError = null;
    });
    try {
      final guide = await widget.api.playerGuide(
        accountId: guideContext?.accountId,
        categoryId: guideContext?.categoryId,
        channelId: guideContext?.channelId,
        bookmarkId: bookmarkId,
      );
      if (!mounted || requestGeneration != _playerGuideRequestGeneration) {
        return null;
      }
      setState(() => _playerGuide = guide);
      return guide;
    } catch (error) {
      if (mounted && requestGeneration == _playerGuideRequestGeneration) {
        setState(() => _playerGuideError = error.toString());
        if (reportErrors) _showMenuError(error);
      }
      return null;
    } finally {
      if (mounted && requestGeneration == _playerGuideRequestGeneration) {
        setState(() => _playerGuideLoading = false);
      }
    }
  }

  Future<void> _showPlayerGuide() async {
    final guide = await _loadPlayerGuide(reportErrors: true);
    if (!mounted || guide == null) return;

    Widget programme(dynamic value) {
      if (value is! Map) {
        return Text(
          _bookmarkText('epgNoGuide', 'No programme guide available'),
        );
      }
      final item = Map<String, dynamic>.from(value);
      final title = item['title']?.toString() ?? '';
      final description = item['description']?.toString() ?? '';
      return Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.w600)),
          if (description.isNotEmpty) ...[
            const SizedBox(height: 4),
            Text(description),
          ],
        ],
      );
    }

    await showDialog<void>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          (guide['channelName'] as String?) ??
              _playerGuideChannelName ??
              _bookmarkText('epgOpenGuide', 'View Guide'),
        ),
        content: SizedBox(
          width: 440,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _bookmarkText('epgNow', 'Now'),
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              programme(guide['now']),
              const SizedBox(height: 16),
              Text(
                _bookmarkText('epgNext', 'Next'),
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              programme(guide['next']),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(_bookmarkText('commonClose', 'Close')),
          ),
        ],
      ),
    );
  }

  Widget _buildInlinePlayerGuide() {
    final guide = _playerGuide;
    Widget guideLine(String key, String fallback, dynamic programme) {
      final item = programme is Map
          ? Map<String, dynamic>.from(programme)
          : null;
      final title =
          item?['title']?.toString() ??
          _bookmarkText('epgNoGuide', 'No programme guide available');
      final description = item?['description']?.toString() ?? '';
      return Row(
        children: [
          SizedBox(
            width: 52,
            child: Text(
              _bookmarkText(key, fallback),
              style: Theme.of(context).textTheme.labelMedium,
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
                if (description.isNotEmpty)
                  Text(
                    description,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
              ],
            ),
          ),
        ],
      );
    }

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        border: Border(
          top: BorderSide(color: Theme.of(context).colorScheme.outlineVariant),
        ),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (_playerGuideLoading) const LinearProgressIndicator(minHeight: 2),
          if (_playerGuideError != null && guide == null)
            Text(
              _playerGuideError!,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall,
            )
          else ...[
            guideLine('epgNow', 'Now', guide?['now']),
            const SizedBox(height: 4),
            guideLine('epgNext', 'Next', guide?['next']),
          ],
        ],
      ),
    );
  }

  @override
  void onWindowEnterFullScreen() {
    if (mounted) setState(() => _playerFullscreen = true);
  }

  @override
  void onWindowLeaveFullScreen() {
    if (mounted) setState(() => _playerFullscreen = false);
  }

  void _refreshAfterDatabaseRestore() {
    setState(() {
      _accounts = widget.api.accounts();
      _bookmarkCategories = widget.api.bookmarkCategories();
      _bookmarks = _loadFavouriteItems(_favouriteCategory);
      _watchingNow = widget.api.watchingNow();
      _selectedAccount = null;
      _selectedCategory = null;
      _categories = null;
      _channels = null;
      _additionalChannels.clear();
    });
  }

  void _showAboutDialog() {
    showDialog<void>(
      context: context,
      builder: (context) => DesktopAboutDialog(api: widget.api),
    );
  }

  void _settingsSaved(Map<String, dynamic> settings) {
    final wasWideView = _wideView;
    final wasEpgEnabled = _menuSettings['epgEnabled'] as bool? ?? false;
    final wideView = settings['wideView'] as bool? ?? _wideView;
    final epgEnabled = settings['epgEnabled'] as bool? ?? false;
    if (mounted) {
      setState(() {
        _menuSettings = settings;
        _wideView = wideView;
        if (!epgEnabled) {
          _playerGuideRequestGeneration++;
          _playerGuideLoading = false;
          _playerGuide = null;
          _playerGuideError = null;
        }
      });
    }
    widget.onThemeModeChanged?.call(
      settings['themeMode'] as String? ?? 'system',
    );
    if (mounted &&
        _showPlayer &&
        epgEnabled &&
        !wideView &&
        (wasWideView || !wasEpgEnabled && epgEnabled) &&
        (_playerGuideContext != null || _playerGuideBookmarkId != null)) {
      unawaited(_loadPlayerGuide());
    }
  }

  Future<void> _updateMenuSettings(Map<String, dynamic> updates) async {
    if (_menuActionBusy) return;
    setState(() => _menuActionBusy = true);
    try {
      final settings = await widget.api.updateSettings(updates);
      if (mounted) _settingsSaved(settings);
    } catch (error) {
      _showMenuError(error);
    } finally {
      if (mounted) setState(() => _menuActionBusy = false);
    }
  }

  Future<void> _togglePlainTextMode() async {
    final enabled = _menuSettings['enableThumbnails'] as bool? ?? true;
    await _updateMenuSettings({'enableThumbnails': !enabled});
  }

  Future<void> _toggleEpg() async {
    final enabled = _menuSettings['epgEnabled'] as bool? ?? false;
    await _updateMenuSettings({'epgEnabled': !enabled});
  }

  Future<void> _cycleThemeMode() async {
    final mode = _menuSettings['themeMode'] as String? ?? 'system';
    final next = switch (mode) {
      'light' => 'dark',
      'dark' => 'system',
      _ => 'light',
    };
    await _updateMenuSettings({'themeMode': next});
  }

  Future<void> _toggleStayOnTop() async {
    final next = !_stayOnTop;
    try {
      await windowManager.setAlwaysOnTop(next);
      if (mounted) setState(() => _stayOnTop = next);
    } catch (error) {
      _showMenuError(error);
    }
  }

  Future<void> _toggleParentalLockAccess() async {
    if (_menuActionBusy) return;
    setState(() => _menuActionBusy = true);
    try {
      final status = await widget.api.filterLockStatus();
      if (status['passwordConfigured'] == true && status['unlocked'] != true) {
        final password = await _askForFilterLockPassword();
        if (password == null) return;
        await widget.api.filterLockAction(
          action: 'unlock',
          currentPassword: password,
        );
      }
      final filters = await widget.api.filterLockFilters();
      final current = filters['restrictionsActive'] as bool? ?? true;
      final saved = await widget.api.updateFilterLockFilters(
        categories: filters['filterCategoriesList'] as String? ?? '',
        channels: filters['filterChannelsList'] as String? ?? '',
        restrictionsActive: !current,
      );
      if (mounted) {
        setState(() {
          _menuSettings = {
            ..._menuSettings,
            'pauseFiltering': !(saved['restrictionsActive'] as bool? ?? true),
          };
        });
        final messageKey = saved['restrictionsActive'] == true
            ? 'autoParentalLockAccessEnabled'
            : 'autoParentalLockAccessDisabled';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(_bookmarkText(messageKey, 'Saved'))),
        );
      }
    } catch (error) {
      _showMenuError(error);
    } finally {
      if (mounted) setState(() => _menuActionBusy = false);
    }
  }

  Future<String?> _askForFilterLockPassword() async {
    final controller = TextEditingController();
    try {
      return await showDialog<String>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(
            _bookmarkText('filterLockUnlockTitle', 'Unlock parental lock'),
          ),
          content: TextField(
            controller: controller,
            autofocus: true,
            obscureText: true,
            decoration: InputDecoration(
              labelText: _bookmarkText(
                'filterLockPasswordPrompt',
                'Parental lock password',
              ),
            ),
            onSubmitted: (value) => Navigator.pop(context, value),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: Text(_bookmarkText('autoCancel', 'Cancel')),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, controller.text),
              child: Text(_bookmarkText('commonConfirm', 'Unlock')),
            ),
          ],
        ),
      );
    } finally {
      controller.dispose();
    }
  }

  Future<void> _switchToLightweightMode() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          _bookmarkText(
            'configLightweightModeSwitchTitle',
            'Switch to Lightweight Mode',
          ),
        ),
        content: Text(
          _bookmarkText(
            'configLightweightModeSwitchConfirm',
            'Switching to Lightweight Mode will close UIPTV. Restart it to use the lightweight interface. Continue?',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_bookmarkText('autoCancel', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(
              _bookmarkText(
                'configLightweightModeSwitchTitle',
                'Switch to Lightweight Mode',
              ),
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.updateSettings({'lightweightModeEnabled': true});
      await windowManager.close();
    } catch (error) {
      _showMenuError(error);
    }
  }

  Future<void> _openExternalUrl(String value) async {
    final uri = Uri.tryParse(value);
    if (uri == null || uri.scheme != 'https' || uri.host != 'github.com') {
      return;
    }
    try {
      if (Platform.isMacOS) {
        await Process.start('open', [uri.toString()]);
      } else if (Platform.isWindows) {
        await Process.start('cmd', ['/c', 'start', '', uri.toString()]);
      } else if (Platform.isLinux) {
        await Process.start('xdg-open', [uri.toString()]);
      }
    } catch (error) {
      _showMenuError(error);
    }
  }

  Future<void> _checkForUpdates() async {
    if (_checkingUpdates) return;
    setState(() => _checkingUpdates = true);
    try {
      final about = await widget.api.about();
      final update = await widget.api.checkForUpdates(
        currentVersion: about['version'] as String? ?? '0',
      );
      if (!mounted) return;
      if (!update.isAvailable) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _bookmarkText(
                'autoYouAreCurrentlyOnTheLatestVersion',
                'You are currently on the latest version.',
              ),
            ),
          ),
        );
        return;
      }
      final download = await showDesktopUpdateAvailableDialog(
        context: context,
        update: update,
        labels: _bookmarkTranslations,
      );
      if (download == true && mounted) await _openExternalUrl(update.url);
    } catch (error) {
      _showMenuError(error);
    } finally {
      if (mounted) setState(() => _checkingUpdates = false);
    }
  }

  void _showMenuError(Object error) {
    if (!mounted) return;
    final message = error is UiptvApiException
        ? error.message
        : error.toString();
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  void initState() {
    super.initState();
    windowManager.addListener(this);
    _player = UiptvPlayer(onStateChanged: _refreshPlayerStatus);
    _accounts = widget.api.accounts();
    _bookmarkCategories = widget.api.bookmarkCategories();
    _bookmarks = _loadFavouriteItems(_favouriteCategory);
    _watchingNow = widget.api.watchingNow();
    _loadPlayerLayoutSettings();
    _loadBookmarkTranslations();
    if (widget.initialStreamUrl.isNotEmpty) {
      _streamUrlController.text = widget.initialStreamUrl;
      WidgetsBinding.instance.addPostFrameCallback((_) {
        setState(() => _showPlayer = true);
        _player.playUrl(widget.initialStreamUrl);
      });
    }
  }

  @override
  void dispose() {
    windowManager.removeListener(this);
    _playerIdleTimer?.cancel();
    _player.dispose();
    _playerFocusNode.dispose();
    _streamUrlController.dispose();
    _catalogueSearchController.dispose();
    _accountSearchController.dispose();
    _favouriteSearchController.dispose();
    super.dispose();
  }

  void _loadBookmarkTranslations() {
    widget.api
        .translations(const [
          'bookmarkRecent',
          'autoSeasonNumber',
          'autoFavoritesEmptyDetail',
          'autoRecentItemsEmptyDetail',
          'autoManageCategories',
          'autoCategoryName',
          'autoAdd',
          'autoRemove',
          'autoManage',
          'autoCancel',
          'autoClear',
          'commonAll',
          'commonClose',
          'commonConfirm',
          'commonError',
          'commonSearch',
          'configClearRecent',
          'configRecentClearConfirm',
          'configRecentCleared',
          'configRecentClearFailed',
          'autoYouAreCurrentlyOnTheLatestVersion',
          'autoUpdateCheckFailed',
          'autoRemoveFromWatchingNowConfirm',
          'autoUpdateAvailablePrompt',
          'autoUiptvVersion',
          'autoOk',
          'autoEpisodeNumber',
          'autoEpisodes',
          'autoViewEpisodes',
          'autoReloadFromServer',
          'autoRatingPrefix',
          'autoGenrePrefix',
          'autoReleasePrefix',
          'autoWatchingNow',
          'configWideView',
          'autoFit',
          'playerScaleCrop',
          'playerScaleStretch',
          'autoAudioTracksSection',
          'autoNoAudioTracks',
          'autoNoOptions',
          'commonSystem',
          'commonDisabled',
          'autoPlay',
          'autoStop',
          'autoHideThisBar',
          'epgNow',
          'epgNext',
          'epgNoGuide',
          'epgOpenGuide',
          'playerPreviousItem',
          'playerNextItem',
          'autoAccount',
          'autoNewAccount',
          'autoEditManageAccount',
          'autoDeleteAccount',
          'autoTvChannels',
          'autoVod',
          'autoSeries',
          'autoBack',
          'autoCategories',
          'autoNothingFoundFor',
          'autoManageMACAddresses',
          'autoAddMACAddressEsCommaSeparated',
          'autoReloadCache',
          'autoAdd',
          'autoPinAccountOnTop',
          'commonSave',
          'commonConfirm',
          'manageAccountNameMustBeUniquePrompt',
          'manageUserNamePrompt',
          'managePasswordPrompt',
          'manageUrlPrompt',
          'manageEpgPrompt',
          'managePrimaryMacAddressHint',
          'manageMacAddressListPrompt',
          'manageSerialNumberPrompt',
          'manageDeviceId1Prompt',
          'manageDeviceId2Prompt',
          'manageSignaturePrompt',
          'manageM3u8FilePathUrlPrompt',
          'manageHttpMethodPrompt',
          'manageTimezonePrompt',
          'manageAccountDisableCaching',
          'manageResolveChainAndDeepRedirects',
          'manageWebPlaybackProxy',
          'autoEnableParentalLockAccess',
          'autoDisableParentalLockAccess',
          'autoParentalLockAccessEnabled',
          'autoParentalLockAccessDisabled',
          'autoSettings',
          'autoImportBulkAccounts',
          'autoReloadAccountsCache',
          'autoLogs',
          'autoEnablePlainTextMode',
          'autoDisablePlainTextMode',
          'epgEnableMode',
          'epgDisableMode',
          'autoEnableDarkTheme',
          'autoDisableDarkTheme',
          'autoUseSystemTheme',
          'autoStayOnTop',
          'configLightweightModeSwitchTitle',
          'configLightweightModeSwitchConfirm',
          'autoHelp',
          'autoCheckForUpdates2',
          'autoAbout',
          'filterLockUnlockTitle',
          'filterLockPasswordPrompt',
          'commonConfirm',
          'autoCancel',
          'filterLockStateToggleLabel',
          'autoInputCannotBeEmpty',
          'autoSortDefault',
          'autoSortDefaultCompact',
          'autoSortNameAscending',
          'autoSortNameAscendingCompact',
          'autoSortNameDescending',
          'autoSortNameDescendingCompact',
        ])
        .then((translations) {
          if (mounted) {
            setState(() => _bookmarkTranslations = translations);
          }
        })
        .catchError((Object _) {});
  }

  String _bookmarkText(String key, String fallback) =>
      _bookmarkTranslations[key] ?? fallback;

  Future<List<UiptvBookmarkSummary>> _loadFavouriteItems(String categoryId) {
    if (categoryId == _recentBookmarkCategoryId) {
      return widget.api.recentBookmarks();
    }
    return widget.api.allBookmarks(
      categoryId: categoryId.isEmpty ? null : categoryId,
    );
  }

  Future<void> _clearRecentBookmarks() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_bookmarkText('commonConfirm', 'Confirm')),
        content: Text(
          _bookmarkText(
            'configRecentClearConfirm',
            'Are you sure you want to clear recently played items?',
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_bookmarkText('autoCancel', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(
              _bookmarkText('configClearRecent', 'Clear Recent Items'),
            ),
          ),
        ],
      ),
    );
    if (confirmed != true) {
      return;
    }
    try {
      await widget.api.clearRecentBookmarks();
      if (!mounted) return;
      setState(() => _bookmarks = _loadFavouriteItems(_favouriteCategory));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _bookmarkText(
              'configRecentCleared',
              'Recently played items cleared',
            ),
          ),
        ),
      );
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            _bookmarkText(
              'configRecentClearFailed',
              'An error occurred while clearing recently played items.',
            ),
          ),
        ),
      );
    }
  }

  Future<void> _manageBookmarkCategories(
    List<UiptvBookmarkCategory> initialCategories,
  ) async {
    final nameController = TextEditingController();
    var categories = List<UiptvBookmarkCategory>.of(initialCategories);

    Future<List<UiptvBookmarkCategory>> reloadCategories() async {
      final updated = await widget.api.bookmarkCategories();
      if (mounted) {
        setState(() => _bookmarkCategories = Future.value(updated));
      }
      return updated;
    }

    Future<void> addCategory(StateSetter updateDialog) async {
      final name = nameController.text.trim();
      if (name.isEmpty) return;
      try {
        await widget.api.addBookmarkCategory(name);
        nameController.clear();
        final updated = await reloadCategories();
        updateDialog(() => categories = updated);
      } catch (error) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text(error.toString())));
        }
      }
    }

    Future<void> removeCategory(
      UiptvBookmarkCategory category,
      StateSetter updateDialog,
    ) async {
      try {
        await widget.api.deleteBookmarkCategory(category.id);
        final updated = await reloadCategories();
        updateDialog(() => categories = updated);
        if (!mounted) return;
        setState(() {
          if (_favouriteCategory == category.id) {
            _favouriteCategory = '';
          }
          _bookmarks = _loadFavouriteItems(_favouriteCategory);
        });
      } catch (error) {
        if (mounted) {
          ScaffoldMessenger.of(context)
              .showSnackBar(SnackBar(content: Text(error.toString())));
        }
      }
    }

    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, updateDialog) => AlertDialog(
          title: Text(
            _bookmarkText('autoManageCategories', 'Manage Categories'),
          ),
          content: SizedBox(
            width: 440,
            height: 400,
            child: Column(
              children: [
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        controller: nameController,
                        textInputAction: TextInputAction.done,
                        onSubmitted: (_) => addCategory(updateDialog),
                        decoration: InputDecoration(
                          labelText: _bookmarkText(
                            'autoCategoryName',
                            'Category Name',
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    FilledButton(
                      onPressed: () => addCategory(updateDialog),
                      child: Text(_bookmarkText('autoAdd', 'Add')),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Expanded(
                  child: ListView.separated(
                    itemCount: categories.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final category = categories[index];
                      return ListTile(
                        title: Text(category.name),
                        trailing: IconButton(
                          tooltip: _bookmarkText('autoRemove', 'Remove'),
                          icon: const Icon(Icons.delete_outline),
                          onPressed: () =>
                              removeCategory(category, updateDialog),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(_bookmarkText('commonClose', 'Close')),
            ),
          ],
        ),
      ),
    );
    nameController.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final desktopContent = DesktopPlayerLayout(
      content: _outlinedPanel(_buildSection()),
      player: _buildPlayerPanel(),
      showPlayer: _showPlayer,
      isFullscreen: _playerFullscreen,
      isFloating: _playerFloating,
      wideView: _wideView,
    );
    return Scaffold(
      extendBodyBehindAppBar: !_playerFullscreen,
      appBar: _playerFullscreen
          ? null
          : AppBar(
              toolbarHeight: 54,
              backgroundColor: Colors.transparent,
              surfaceTintColor: Colors.transparent,
              flexibleSpace: ClipRect(
                child: BackdropFilter(
                  filter: ImageFilter.blur(sigmaX: 18, sigmaY: 18),
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: Theme.of(context).appBarTheme.backgroundColor,
                      border: Border(
                        bottom: BorderSide(
                          color: Theme.of(context).colorScheme.outlineVariant
                              .withValues(alpha: 0.55),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
              titleSpacing: 14,
              title: Row(
                children: [
                  Image.asset(
                    'assets/icons/uiptv-app.png',
                    key: const Key('uiptv-app-logo'),
                    width: 25,
                    height: 28,
                    fit: BoxFit.contain,
                  ),
                  const SizedBox(width: 8),
                  const Text('UIPTV', style: TextStyle(fontSize: 16)),
                  const SizedBox(width: 24),
                  _navigationButton(
                    _DesktopSection.favourites,
                    Icons.favorite,
                    _bookmarkText('autoFavoritesTitle', 'Favourites'),
                  ),
                  _navigationButton(
                    _DesktopSection.accounts,
                    Icons.person,
                    _bookmarkText('autoAccount', 'Accounts'),
                  ),
                  _navigationButton(
                    _DesktopSection.watchingNow,
                    Icons.play_arrow,
                    _bookmarkText('autoWatchingNow', 'Watching'),
                  ),
                ],
              ),
              actions: [
                IconButton(
                  tooltip: _showPlayer ? 'Hide player' : 'Show player',
                  icon: Icon(
                    _showPlayer
                        ? Icons.visibility_off_outlined
                        : Icons.visibility_outlined,
                  ),
                  onPressed: () => setState(() => _showPlayer = !_showPlayer),
                ),
                const SizedBox(width: 10),
                _buildSettingsMenu(),
              ],
            ),
      body: _playerFullscreen
          ? desktopContent
          : Stack(
              children: [
                Positioned.fill(child: _buildGlassBackdrop(context)),
                Positioned.fill(
                  child: Padding(
                    padding: const EdgeInsets.only(top: 54),
                    child: desktopContent,
                  ),
                ),
              ],
            ),
    );
  }

  Widget _navigationButton(
    _DesktopSection section,
    IconData icon,
    String label,
  ) {
    return DesktopNavigationButton(
      icon: icon,
      label: label,
      selected: _section == section,
      iconOnly: DesktopNavigationButton.usesIconOnlyNavigation(
        MediaQuery.sizeOf(context).width,
      ),
      onPressed: () => setState(() => _section = section),
    );
  }

  Widget _buildSettingsMenu() => DesktopSettingsMenu(
    labels: _bookmarkTranslations,
    pauseFiltering: _menuSettings['pauseFiltering'] as bool? ?? false,
    thumbnailsEnabled: _menuSettings['enableThumbnails'] as bool? ?? true,
    epgEnabled: _menuSettings['epgEnabled'] as bool? ?? false,
    themeMode: _menuSettings['themeMode'] as String? ?? 'system',
    stayOnTop: _stayOnTop,
    enabled: !_menuActionBusy,
    onSelected: _handleSettingsMenuAction,
  );

  void _handleSettingsMenuAction(DesktopSettingsMenuAction action) {
    switch (action) {
      case DesktopSettingsMenuAction.settings:
        setState(() => _section = _DesktopSection.settings);
      case DesktopSettingsMenuAction.import:
        setState(() => _section = _DesktopSection.import);
      case DesktopSettingsMenuAction.cacheReload:
        setState(() {
          _cacheReloadInitialAccountIds = const [];
          _section = _DesktopSection.cacheReload;
        });
      case DesktopSettingsMenuAction.logs:
        setState(() => _section = _DesktopSection.logs);
      case DesktopSettingsMenuAction.parentalLock:
        _toggleParentalLockAccess();
      case DesktopSettingsMenuAction.plainTextMode:
        _togglePlainTextMode();
      case DesktopSettingsMenuAction.epg:
        _toggleEpg();
      case DesktopSettingsMenuAction.theme:
        _cycleThemeMode();
      case DesktopSettingsMenuAction.stayOnTop:
        _toggleStayOnTop();
      case DesktopSettingsMenuAction.lightweightMode:
        _switchToLightweightMode();
      case DesktopSettingsMenuAction.help:
        _openExternalUrl(
          'https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md',
        );
      case DesktopSettingsMenuAction.updates:
        _checkForUpdates();
      case DesktopSettingsMenuAction.about:
        _showAboutDialog();
    }
  }

  Widget _outlinedPanel(Widget child) {
    final colorScheme = Theme.of(context).colorScheme;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final borderRadius = BorderRadius.circular(14);
    return ClipRRect(
      borderRadius: borderRadius,
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 22, sigmaY: 22),
        child: Material(
          clipBehavior: Clip.antiAlias,
          color: colorScheme.surface.withValues(alpha: isDark ? 0.86 : 0.80),
          shape: RoundedRectangleBorder(
            side: BorderSide(
              color: colorScheme.outlineVariant.withValues(alpha: 0.72),
            ),
            borderRadius: borderRadius,
          ),
          child: child,
        ),
      ),
    );
  }

  Widget _buildGlassBackdrop(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Stack(
      fit: StackFit.expand,
      children: [
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: isDark
                  ? const [
                      Color(0xFF1A2530),
                      Color(0xFF11161B),
                      Color(0xFF201D27),
                    ]
                  : const [
                      Color(0xFFF9FCFF),
                      Color(0xFFEAF2FA),
                      Color(0xFFF7F2F9),
                    ],
            ),
          ),
        ),
        Positioned(
          top: -190,
          left: -180,
          width: 620,
          height: 520,
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: isDark
                    ? const [Color(0x332F9CAB), Colors.transparent]
                    : const [Color(0x385AAEE0), Colors.transparent],
              ),
            ),
          ),
        ),
        Positioned(
          right: -210,
          bottom: -240,
          width: 700,
          height: 620,
          child: DecoratedBox(
            decoration: BoxDecoration(
              gradient: RadialGradient(
                colors: isDark
                    ? const [Color(0x282E657A), Colors.transparent]
                    : const [Color(0x28B8A2E1), Colors.transparent],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _playerActionButton({
    String? tooltip,
    required VoidCallback? onPressed,
    required Widget icon,
  }) {
    return IconButton(
      tooltip: tooltip,
      onPressed: onPressed,
      color: Colors.white,
      icon: icon,
      iconSize: 18,
      visualDensity: VisualDensity.compact,
      constraints: const BoxConstraints.tightFor(width: 32, height: 32),
      padding: EdgeInsets.zero,
      style: IconButton.styleFrom(
        hoverColor: const Color(0x24FFFFFF),
        highlightColor: const Color(0x18FFFFFF),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      ),
    );
  }

  Widget _playerAssetButton({
    required String icon,
    String? tooltip,
    required VoidCallback? onPressed,
  }) {
    return _playerActionButton(
      tooltip: tooltip,
      onPressed: onPressed,
      icon: Image.asset(
        'assets/icons/videoPlayer/$icon',
        width: 18,
        height: 18,
        color: Colors.white,
      ),
    );
  }

  String _audioTrackLabel(AudioTrack track) {
    if (track.id == 'auto') {
      return _bookmarkText('commonSystem', 'System');
    }
    if (track.id == 'no') {
      return _bookmarkText('commonDisabled', 'Disabled');
    }
    final title = [
      track.title,
      track.language,
    ].whereType<String>().where((value) => value.isNotEmpty).join(' · ');
    return title.isEmpty ? track.id : title;
  }

  Widget _playerTrackButton() {
    final tracks = _player.tracks.audio;
    return PopupMenuButton<String>(
      tooltip: _bookmarkText('autoAudioTracksSection', 'Audio'),
      icon: const Icon(Icons.audiotrack, color: Colors.white, size: 18),
      padding: EdgeInsets.zero,
      constraints: const BoxConstraints.tightFor(width: 32, height: 32),
      iconSize: 18,
      onSelected: (id) {
        for (final track in tracks) {
          if (track.id == id) {
            _player.setAudioTrack(track);
            break;
          }
        }
      },
      itemBuilder: (context) => [
        PopupMenuItem<String>(
          enabled: false,
          child: Text(_bookmarkText('autoAudioTracksSection', 'Audio')),
        ),
        if (tracks.isEmpty)
          PopupMenuItem<String>(
            enabled: false,
            child: Text(_bookmarkText('autoNoAudioTracks', 'No audio tracks')),
          ),
        for (final track in tracks)
          CheckedPopupMenuItem<String>(
            value: track.id,
            checked: _player.player.state.track.audio.id == track.id,
            child: Text(_audioTrackLabel(track)),
          ),
      ],
    );
  }

  Widget _buildPlayerPanel() {
    final videoSurface = _buildPlayerVideoSurface();
    final showGuide = DesktopPlayerLayout.showsInlineEpg(
      wideView: _wideView,
      isFullscreen: _playerFullscreen,
      isFloating: _playerFloating,
    );
    if (!_showPlayer ||
        _playerFullscreen ||
        _playerFloating ||
        !showGuide ||
        _menuSettings['epgEnabled'] != true ||
        (_playerGuideContext == null && _playerGuideBookmarkId == null)) {
      return videoSurface;
    }
    return Column(
      children: [
        Expanded(child: videoSurface),
        _buildInlinePlayerGuide(),
      ],
    );
  }

  Widget _buildPlayerVideoSurface() {
    final isOnDemand = DesktopPlayerLayout.showsTimeline(
      mode: _playerChannelMode,
    );
    final guideSummary = isOnDemand
        ? ''
        : DesktopPlayerStreamInfo.guideSummary(_playerGuide);
    final guideChannelName =
        _playerGuide?['channelName']?.toString().trim() ?? '';
    final streamTitle = _playerGuideChannelName?.trim().isNotEmpty == true
        ? _playerGuideChannelName!.trim()
        : guideChannelName;
    final streamDetails = DesktopPlayerStreamInfo.build(
      width: _player.videoWidth,
      height: _player.videoHeight,
      videoCodec: _player.videoCodec,
      audioCodec: _player.audioCodec,
    );
    final totalMs = _player.duration.inMilliseconds;
    final currentMs = _player.position.inMilliseconds.clamp(
      0,
      totalMs > 0 ? totalMs : 1,
    );
    String clock(Duration value) {
      final minutes = value.inMinutes;
      final seconds = value.inSeconds % 60;
      return '$minutes:${seconds.toString().padLeft(2, '0')}';
    }

    final aspectIcon = switch (_aspectRatioMode) {
      1 => 'video-scale-crop.png',
      2 => 'video-scale-stretch.png',
      _ => 'video-scale-fit.png',
    };
    final aspectTooltip = switch (_aspectRatioMode) {
      1 => _bookmarkText('playerScaleCrop', 'Crop'),
      2 => _bookmarkText('playerScaleStretch', 'Stretch'),
      _ => _bookmarkText('autoFit', 'Fit'),
    };

    return Material(
      color: Colors.black,
      clipBehavior: Clip.antiAlias,
      elevation: _playerFullscreen ? 0 : 12,
      shadowColor: Colors.black45,
      borderRadius: BorderRadius.circular(_playerFullscreen ? 0 : 12),
      child: Focus(
        focusNode: _playerFocusNode,
        onKeyEvent: _handlePlayerKeyEvent,
        child: MouseRegion(
          onEnter: (_) => _handlePlayerPointerActivity(),
          onExit: (_) {
            _playerIdleTimer?.cancel();
            if (_playerHovered) setState(() => _playerHovered = false);
          },
          child: Listener(
            onPointerHover: (_) => _handlePlayerPointerActivity(),
            onPointerSignal: (event) {
              if (event is PointerScrollEvent) {
                _adjustPlayerVolume(event.scrollDelta.dy < 0 ? 5 : -5);
                _handlePlayerPointerActivity();
              }
            },
            child: Stack(
              children: [
                Positioned.fill(
                  child: GestureDetector(
                    behavior: HitTestBehavior.opaque,
                    onTapDown: (_) {
                      _playerFocusNode.requestFocus();
                      _handlePlayerPointerActivity();
                    },
                    onDoubleTap: _togglePlayerFullscreen,
                    onSecondaryTap: () {
                      _playerFocusNode.requestFocus();
                      _revealPlayerControls();
                    },
                    child: Video(
                      controller: _player.controller,
                      controls: NoVideoControls,
                      fit: switch (_aspectRatioMode) {
                        1 => BoxFit.cover,
                        2 => BoxFit.fill,
                        _ => BoxFit.contain,
                      },
                    ),
                  ),
                ),
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: LayoutBuilder(
                      builder: (context, playerConstraints) {
                        final moveSeekButtonsToTimeline =
                            DesktopPlayerLayout.movesSeekButtonsToTimeline(
                              playerWidth: playerConstraints.maxWidth,
                            );
                        return Align(
                          alignment: Alignment.bottomCenter,
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(maxWidth: 640),
                            child: AnimatedOpacity(
                              opacity: _playerHovered && !_playerControlsHidden
                                  ? 1
                                  : 0,
                              duration: const Duration(milliseconds: 160),
                              child: MouseRegion(
                                onEnter: (_) => _handlePlayerControlsEnter(),
                                onExit: (_) => _handlePlayerControlsExit(),
                                child: IgnorePointer(
                                  ignoring:
                                      !_playerHovered || _playerControlsHidden,
                                  child: ClipRRect(
                                    borderRadius: BorderRadius.circular(12),
                                    child: BackdropFilter(
                                      filter: ImageFilter.blur(
                                        sigmaX: 14,
                                        sigmaY: 14,
                                      ),
                                      child: Container(
                                        width: double.infinity,
                                        padding: const EdgeInsets.symmetric(
                                          horizontal: 12,
                                          vertical: 5,
                                        ),
                                        decoration: BoxDecoration(
                                          color: const Color(0xAA1A1D20),
                                          border: Border.all(
                                            color: const Color(0x30FFFFFF),
                                          ),
                                          borderRadius: BorderRadius.circular(
                                            12,
                                          ),
                                          boxShadow: const [
                                            BoxShadow(
                                              color: Color(0x55000000),
                                              blurRadius: 16,
                                              offset: Offset(0, 6),
                                            ),
                                          ],
                                        ),
                                        child: Column(
                                          mainAxisSize: MainAxisSize.min,
                                          children: [
                                            if (guideSummary.isNotEmpty)
                                              SizedBox(
                                                width: double.infinity,
                                                child: Text(
                                                  guideSummary,
                                                  maxLines: 1,
                                                  overflow:
                                                      TextOverflow.ellipsis,
                                                  style: const TextStyle(
                                                    color: Colors.white70,
                                                    fontSize: 12,
                                                    height: 1.15,
                                                  ),
                                                ),
                                              ),
                                            if (streamTitle.isNotEmpty ||
                                                streamDetails.isNotEmpty)
                                              SizedBox(
                                                width: double.infinity,
                                                child: RichText(
                                                  maxLines: 2,
                                                  overflow:
                                                      TextOverflow.ellipsis,
                                                  text: TextSpan(
                                                    style:
                                                        DefaultTextStyle.of(
                                                          context,
                                                        ).style.copyWith(
                                                          color: Colors.white,
                                                          fontSize: 12,
                                                        ),
                                                    children: [
                                                      if (!isOnDemand &&
                                                          streamTitle
                                                              .isNotEmpty)
                                                        const WidgetSpan(
                                                          alignment:
                                                              PlaceholderAlignment
                                                                  .middle,
                                                          child: Padding(
                                                            padding:
                                                                EdgeInsets.only(
                                                                  right: 6,
                                                                ),
                                                            child: DecoratedBox(
                                                              decoration:
                                                                  BoxDecoration(
                                                                    color: Color(
                                                                      0xFF55D98B,
                                                                    ),
                                                                    shape: BoxShape
                                                                        .circle,
                                                                  ),
                                                              child: SizedBox(
                                                                width: 7,
                                                                height: 7,
                                                              ),
                                                            ),
                                                          ),
                                                        ),
                                                      if (streamTitle
                                                          .isNotEmpty)
                                                        TextSpan(
                                                          text: streamTitle,
                                                          style:
                                                              const TextStyle(
                                                                color: Colors
                                                                    .white,
                                                                fontSize: 12,
                                                                height: 1.15,
                                                                fontWeight:
                                                                    FontWeight
                                                                        .w700,
                                                              ),
                                                        ),
                                                      if (streamTitle
                                                              .isNotEmpty &&
                                                          streamDetails
                                                              .isNotEmpty)
                                                        const TextSpan(
                                                          text: '  ',
                                                        ),
                                                      if (streamDetails
                                                          .isNotEmpty)
                                                        TextSpan(
                                                          text: streamDetails,
                                                          style:
                                                              const TextStyle(
                                                                color: Colors
                                                                    .white70,
                                                                fontSize: 8,
                                                                height: 1.15,
                                                              ),
                                                        ),
                                                    ],
                                                  ),
                                                ),
                                              ),
                                            if (_player.error != null)
                                              SizedBox(
                                                width: double.infinity,
                                                child: Text(
                                                  _bookmarkText(
                                                    'autoAnErrorOccurredDuringPlayback',
                                                    'Playback error',
                                                  ),
                                                  maxLines: 1,
                                                  overflow:
                                                      TextOverflow.ellipsis,
                                                  style: const TextStyle(
                                                    color: Color(0xFFFF8A80),
                                                    fontSize: 11,
                                                  ),
                                                ),
                                              ),
                                            SingleChildScrollView(
                                              scrollDirection: Axis.horizontal,
                                              child: Row(
                                                mainAxisSize: MainAxisSize.min,
                                                children: [
                                                  _playerAssetButton(
                                                    icon: _player.playing
                                                        ? 'pause.png'
                                                        : 'play.png',
                                                    tooltip: _player.playing
                                                        ? null
                                                        : _bookmarkText(
                                                            'autoPlay',
                                                            'Play',
                                                          ),
                                                    onPressed: _player.playing
                                                        ? _player.pause
                                                        : _player.play,
                                                  ),
                                                  _playerAssetButton(
                                                    icon: 'stop.png',
                                                    tooltip: _bookmarkText(
                                                      'autoStop',
                                                      'Stop',
                                                    ),
                                                    onPressed: _closePlayer,
                                                  ),
                                                  if (_playerChannelIndex > 0)
                                                    _playerActionButton(
                                                      tooltip: _bookmarkText(
                                                        'playerPreviousItem',
                                                        'Previous item',
                                                      ),
                                                      onPressed: () =>
                                                          _navigatePlayerChannel(
                                                            -1,
                                                          ),
                                                      icon: const Icon(
                                                        Icons.skip_previous,
                                                      ),
                                                    ),
                                                  if (_playerChannelIndex >=
                                                          0 &&
                                                      _playerChannelIndex + 1 <
                                                          _playerChannelQueue
                                                              .length)
                                                    _playerActionButton(
                                                      tooltip: _bookmarkText(
                                                        'playerNextItem',
                                                        'Next item',
                                                      ),
                                                      onPressed: () =>
                                                          _navigatePlayerChannel(
                                                            1,
                                                          ),
                                                      icon: const Icon(
                                                        Icons.skip_next,
                                                      ),
                                                    ),
                                                  if (!isOnDemand ||
                                                      !moveSeekButtonsToTimeline)
                                                    _playerAssetButton(
                                                      icon:
                                                          'reverse-rewind.png',
                                                      onPressed: () =>
                                                          _player.seekBy(
                                                            const Duration(
                                                              seconds: -15,
                                                            ),
                                                          ),
                                                    ),
                                                  if (!isOnDemand ||
                                                      !moveSeekButtonsToTimeline)
                                                    _playerAssetButton(
                                                      icon: 'fast-forward.png',
                                                      onPressed: () =>
                                                          _player.seekBy(
                                                            const Duration(
                                                              seconds: 15,
                                                            ),
                                                          ),
                                                    ),
                                                  _playerAssetButton(
                                                    icon: _repeatEnabled
                                                        ? 'repeat-on.png'
                                                        : 'repeat-off.png',
                                                    onPressed: _toggleRepeat,
                                                  ),
                                                  _playerAssetButton(
                                                    icon: 'reload.png',
                                                    onPressed: _player.reload,
                                                  ),
                                                  _playerAssetButton(
                                                    icon: _playerFullscreen
                                                        ? 'fullscreen-exit.png'
                                                        : 'fullscreen.png',
                                                    onPressed:
                                                        _togglePlayerFullscreen,
                                                  ),
                                                  _playerAssetButton(
                                                    icon: _playerFloating
                                                        ? 'picture-in-picture-exit.png'
                                                        : 'picture-in-picture.png',
                                                    onPressed:
                                                        _toggleFloatingPlayer,
                                                  ),
                                                  const SizedBox(width: 8),
                                                  _playerAssetButton(
                                                    icon: _playerMuted
                                                        ? 'mute-on.png'
                                                        : 'mute-off.png',
                                                    onPressed:
                                                        _togglePlayerMute,
                                                  ),
                                                  RotatedBox(
                                                    quarterTurns: 3,
                                                    child: SizedBox(
                                                      width: 52,
                                                      height: 20,
                                                      child: Slider(
                                                        value: _volume,
                                                        min: 0,
                                                        max: 100,
                                                        onChanged:
                                                            _setPlayerVolume,
                                                      ),
                                                    ),
                                                  ),
                                                  _playerActionButton(
                                                    tooltip: _bookmarkText(
                                                      'configWideView',
                                                      'Wide View',
                                                    ),
                                                    onPressed: _toggleWideView,
                                                    icon: Icon(
                                                      _wideView
                                                          ? Icons.view_sidebar
                                                          : Icons.view_column,
                                                    ),
                                                  ),
                                                  _playerAssetButton(
                                                    icon: aspectIcon,
                                                    tooltip: aspectTooltip,
                                                    onPressed:
                                                        _toggleAspectRatio,
                                                  ),
                                                  _playerTrackButton(),
                                                  _playerAssetButton(
                                                    icon: 'arrow-down.png',
                                                    tooltip: _bookmarkText(
                                                      'autoHideThisBar',
                                                      'Hide this bar',
                                                    ),
                                                    onPressed:
                                                        _hidePlayerControls,
                                                  ),
                                                  if (DesktopPlayerLayout.showsEpgControl(
                                                        wideView: _wideView,
                                                        isFullscreen:
                                                            _playerFullscreen,
                                                        isFloating:
                                                            _playerFloating,
                                                      ) &&
                                                      _menuSettings['epgEnabled'] ==
                                                          true &&
                                                      (_playerGuideContext !=
                                                              null ||
                                                          _playerGuideBookmarkId !=
                                                              null))
                                                    _playerActionButton(
                                                      tooltip: _bookmarkText(
                                                        'epgOpenGuide',
                                                        'View Guide',
                                                      ),
                                                      onPressed:
                                                          _playerGuideLoading
                                                          ? null
                                                          : _showPlayerGuide,
                                                      icon: const Icon(
                                                        Icons.article_outlined,
                                                      ),
                                                    ),
                                                ],
                                              ),
                                            ),
                                            if (isOnDemand)
                                              Row(
                                                children: [
                                                  if (moveSeekButtonsToTimeline)
                                                    _playerAssetButton(
                                                      icon:
                                                          'reverse-rewind.png',
                                                      onPressed: () =>
                                                          _player.seekBy(
                                                            const Duration(
                                                              seconds: -15,
                                                            ),
                                                          ),
                                                    ),
                                                  if (moveSeekButtonsToTimeline)
                                                    _playerAssetButton(
                                                      icon: 'fast-forward.png',
                                                      onPressed: () =>
                                                          _player.seekBy(
                                                            const Duration(
                                                              seconds: 15,
                                                            ),
                                                          ),
                                                    ),
                                                  Text(
                                                    clock(_player.position),
                                                    style: const TextStyle(
                                                      color: Colors.white,
                                                      fontSize: 10,
                                                    ),
                                                  ),
                                                  Expanded(
                                                    child: Slider(
                                                      value: currentMs
                                                          .toDouble(),
                                                      min: 0,
                                                      max: totalMs > 0
                                                          ? totalMs.toDouble()
                                                          : 1,
                                                      onChanged: totalMs <= 0
                                                          ? null
                                                          : (
                                                              value,
                                                            ) => _player.seek(
                                                              Duration(
                                                                milliseconds:
                                                                    value
                                                                        .round(),
                                                              ),
                                                            ),
                                                    ),
                                                  ),
                                                  Text(
                                                    totalMs > 0
                                                        ? clock(
                                                            _player.duration,
                                                          )
                                                        : '--:--',
                                                    style: const TextStyle(
                                                      color: Colors.white,
                                                      fontSize: 10,
                                                    ),
                                                  ),
                                                ],
                                              ),
                                          ],
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  KeyEventResult _handlePlayerKeyEvent(FocusNode node, KeyEvent event) {
    if (event is! KeyDownEvent) return KeyEventResult.ignored;
    if (event.logicalKey == LogicalKeyboardKey.keyF) {
      _togglePlayerFullscreen();
    } else if (event.logicalKey == LogicalKeyboardKey.keyM) {
      _togglePlayerMute();
    } else if (event.logicalKey == LogicalKeyboardKey.keyB) {
      _revealPlayerControls();
    } else if (event.logicalKey == LogicalKeyboardKey.escape &&
        _playerFullscreen) {
      _togglePlayerFullscreen();
    } else {
      return KeyEventResult.ignored;
    }
    return KeyEventResult.handled;
  }

  void _setPlayerVolume(double value) {
    final next = value.clamp(0, 100).toDouble();
    setState(() => _volume = next);
    unawaited(_player.setVolume(next));
  }

  void _togglePlayerMute() {
    setState(() => _playerMuted = !_playerMuted);
    unawaited(_player.setMuted(_playerMuted));
  }

  void _adjustPlayerVolume(double change) {
    _setPlayerVolume(_volume + change);
  }

  void _handlePlayerPointerActivity() {
    if (!_showPlayer || _playerControlsHidden) return;
    _playerIdleTimer?.cancel();
    if (_playerControlsHovered) return;
    if (!_playerHovered) setState(() => _playerHovered = true);
    _playerIdleTimer = Timer(const Duration(seconds: 5), () {
      if (!mounted || _playerControlsHidden || _playerControlsHovered) return;
      setState(() => _playerHovered = false);
    });
  }

  void _handlePlayerControlsEnter() {
    _playerControlsHovered = true;
    _playerIdleTimer?.cancel();
  }

  void _handlePlayerControlsExit() {
    _playerControlsHovered = false;
    _handlePlayerPointerActivity();
  }

  void _revealPlayerControls() {
    setState(() {
      _playerControlsHidden = false;
      _playerHovered = true;
    });
    _handlePlayerPointerActivity();
  }

  void _hidePlayerControls() {
    _playerIdleTimer?.cancel();
    setState(() {
      _playerControlsHidden = true;
      _playerHovered = false;
    });
  }

  Widget _buildSection() {
    switch (_section) {
      case _DesktopSection.accounts:
        return _buildAccountBrowser();
      case _DesktopSection.settings:
        return DesktopSettingsScreen(
          api: widget.api,
          onRestored: _refreshAfterDatabaseRestore,
          onSettingsSaved: _settingsSaved,
        );
      case _DesktopSection.favourites:
        return FutureBuilder<List<UiptvBookmarkCategory>>(
          future: _bookmarkCategories,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return _error(snapshot.error);
            }
            final categories = snapshot.data ?? const [];
            final categoryOptions = <UiptvBookmarkCategory>[
              UiptvBookmarkCategory(
                id: _recentBookmarkCategoryId,
                name: _bookmarkText('bookmarkRecent', 'Recent'),
              ),
              ...categories,
              UiptvBookmarkCategory(
                id: '',
                name: _bookmarkText('commonAll', 'All'),
              ),
            ];
            return Column(
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 10, 12, 6),
                  child: Row(
                    children: [
                      Expanded(
                        child: DesktopBookmarkCategorySelector(
                          categories: categoryOptions,
                          selectedCategoryId: _favouriteCategory,
                          onSelected: (category) => setState(() {
                            _favouriteCategory = category.id;
                            _bookmarks = _loadFavouriteItems(category.id);
                          }),
                        ),
                      ),
                      if (_favouriteCategory == _recentBookmarkCategoryId)
                        TextButton.icon(
                          onPressed: _clearRecentBookmarks,
                          icon: const Icon(Icons.clear),
                          label: Text(_bookmarkText('autoClear', 'Clear')),
                        ),
                      const SizedBox(width: 6),
                      OutlinedButton.icon(
                        onPressed: () => _manageBookmarkCategories(categories),
                        icon: const Icon(Icons.tune),
                        label: Text(_bookmarkText('autoManage', 'Manage')),
                      ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 2, 12, 8),
                  child: TextField(
                    controller: _favouriteSearchController,
                    onChanged: (_) => setState(() {}),
                    decoration: InputDecoration(
                      hintText: _bookmarkText('commonSearch', 'Search'),
                      prefixIcon: const Icon(Icons.search),
                      suffixIcon: _favouriteSearchController.text.isEmpty
                          ? null
                          : IconButton(
                              tooltip: _bookmarkText('autoClear', 'Clear'),
                              onPressed: () {
                                _favouriteSearchController.clear();
                                setState(() {});
                              },
                              icon: const Icon(Icons.close),
                            ),
                    ),
                  ),
                ),
                Expanded(
                  child: FutureBuilder<List<UiptvBookmarkSummary>>(
                    future: _bookmarks,
                    builder: (context, bookmarkSnapshot) {
                      if (bookmarkSnapshot.connectionState ==
                          ConnectionState.waiting) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (bookmarkSnapshot.hasError) {
                        return _error(bookmarkSnapshot.error);
                      }
                      final bookmarks = bookmarkSnapshot.data ?? const [];
                      if (bookmarks.isEmpty) {
                        final emptyKey =
                            _favouriteCategory == _recentBookmarkCategoryId
                            ? 'autoRecentItemsEmptyDetail'
                            : 'autoFavoritesEmptyDetail';
                        final fallback =
                            _favouriteCategory == _recentBookmarkCategoryId
                            ? 'Play a channel or title to build this list.'
                            : 'Save a channel or title from an account to see it here.';
                        return Center(
                          child: Padding(
                            padding: const EdgeInsets.all(24),
                            child: Text(_bookmarkText(emptyKey, fallback)),
                          ),
                        );
                      }
                      final query = _favouriteSearchController.text
                          .trim()
                          .toLowerCase();
                      final visibleBookmarks = bookmarks
                          .where((bookmark) {
                            return query.isEmpty ||
                                <String>[
                                  bookmark.channelName,
                                  bookmark.accountName,
                                  bookmark.categoryTitle,
                                ].any(
                                  (value) =>
                                      value.toLowerCase().contains(query),
                                );
                          })
                          .toList(growable: false);
                      return LayoutBuilder(
                        builder: (context, constraints) => GridView.builder(
                          padding: const EdgeInsets.all(14),
                          gridDelegate: desktopBookmarkGridDelegate(
                            availableWidth: constraints.maxWidth,
                          ),
                          itemCount: visibleBookmarks.length,
                          itemBuilder: (context, index) {
                            final bookmark = visibleBookmarks[index];
                            final colorScheme = Theme.of(context).colorScheme;
                            return Material(
                              color: colorScheme.surface,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(16),
                                side: BorderSide(
                                  color: colorScheme.outlineVariant,
                                ),
                              ),
                              child: InkWell(
                                borderRadius: BorderRadius.circular(16),
                                onTap: () => _playBookmark(
                                  bookmark.dbId,
                                  channelName: bookmark.channelName,
                                ),
                                child: Padding(
                                  padding: const EdgeInsets.all(12),
                                  child: Row(
                                    children: [
                                      SizedBox(
                                        width: 52,
                                        height: 52,
                                        child: bookmark.logo.isEmpty
                                            ? const Icon(
                                                Icons.live_tv_outlined,
                                                size: 34,
                                                color: Color(0xFF6D7B87),
                                              )
                                            : Image.network(
                                                bookmark.logo,
                                                fit: BoxFit.contain,
                                                errorBuilder: (_, _, _) =>
                                                    const Icon(
                                                      Icons.live_tv_outlined,
                                                      size: 34,
                                                    ),
                                              ),
                                      ),
                                      const SizedBox(width: 12),
                                      Expanded(
                                        child: Column(
                                          mainAxisAlignment:
                                              MainAxisAlignment.center,
                                          crossAxisAlignment:
                                              CrossAxisAlignment.start,
                                          children: [
                                            Text(
                                              bookmark.channelName,
                                              maxLines: 2,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(
                                                fontWeight: FontWeight.w600,
                                              ),
                                            ),
                                            const SizedBox(height: 5),
                                            Text(
                                              [
                                                    bookmark.accountName,
                                                    bookmark.categoryTitle,
                                                  ]
                                                  .where(
                                                    (value) => value.isNotEmpty,
                                                  )
                                                  .join(' • '),
                                              maxLines: 1,
                                              overflow: TextOverflow.ellipsis,
                                              style: const TextStyle(
                                                fontSize: 12,
                                              ),
                                            ),
                                          ],
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      );
                    },
                  ),
                ),
              ],
            );
          },
        );
      case _DesktopSection.watchingNow:
        return FutureBuilder<UiptvWatchingNowSnapshot>(
          future: _watchingNow,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snapshot.hasError) {
              return _error(snapshot.error);
            }
            final current = snapshot.data;
            return DesktopWatchingNowScreen(
              entries: current?.entries ?? const [],
              translations: _bookmarkTranslations,
              onPlay: _playWatchingNow,
              onPlayEpisode: _playWatchingEpisode,
              onRemove: _removeWatchingNow,
              loadEpisodes: (entry) =>
                  widget.api.watchingNowEpisodes(entry.playbackId),
              loadDetails: (entry) =>
                  widget.api.watchingNowDetails(entry.playbackId),
              onLoadMore: current == null
                  ? () {}
                  : () => _loadMoreWatchingNow(current),
              loadingMore: _loadingMoreWatchingNow,
              hasMore:
                  current?.hasMoreSeries == true || current?.hasMoreVod == true,
            );
          },
        );
      case _DesktopSection.import:
        return BulkAccountImportScreen(
          api: widget.api,
          onImported: (verificationAccountIds) {
            setState(() {
              _accounts = widget.api.accounts();
              if (verificationAccountIds.isNotEmpty) {
                _cacheReloadInitialAccountIds = verificationAccountIds;
                _section = _DesktopSection.cacheReload;
              }
            });
          },
        );
      case _DesktopSection.logs:
        return DesktopLogsScreen(api: widget.api);
      case _DesktopSection.cacheReload:
        return DesktopCacheReloadScreen(
          api: widget.api,
          initialSelectedAccountIds: _cacheReloadInitialAccountIds,
        );
    }
  }

  Widget _buildAccountBrowser() {
    return LayoutBuilder(
      builder: (context, constraints) {
        return DesktopAccountBrowserLayout(
          showEditor: _showAccountEditor,
          hasSelectedAccount: _selectedAccount != null,
          hasSelectedCategory: _selectedCategory != null,
          editor: _accountEditorPane(),
          accountPane: _buildAccountsPane(),
          catalog: _buildAccountCatalogContent(
            singleColumn: DesktopAccountBrowserLayout.usesSingleColumn(
              constraints.maxWidth,
            ),
          ),
        );
      },
    );
  }

  Widget _accountEditorPane() => DesktopAccountEditor(
    key: ValueKey(_editingAccountId ?? 'new-account'),
    api: widget.api,
    translations: _bookmarkTranslations,
    accountId: _editingAccountId,
    onSaved: _accountSaved,
    onCancel: () => setState(() => _showAccountEditor = false),
  );

  Future<void> _accountSaved(Map<String, dynamic> accountData) async {
    final account = UiptvAccountSummary.fromJson(accountData);
    setState(() {
      _showAccountEditor = false;
      _editingAccountId = null;
      _accounts = widget.api.accounts();
      _selectedAccount = account;
      _selectedCategory = null;
      _selectedMode = 'itv';
      _categories = widget.api.categories(accountId: account.dbId, mode: 'itv');
      _channels = null;
    });
  }

  Widget _buildAccountsPane() {
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 8, 8, 4),
          child: Row(
            children: [
              Expanded(
                child: Text(
                  _bookmarkText('autoAccount', 'Account'),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 0, 10, 6),
          child: Row(
            children: [
              Expanded(
                child: DesktopAccountTypeFilter(
                  selectedType: _accountTypeFilter,
                  allLabel: _bookmarkText('commonAll', 'All'),
                  onSelected: (value) =>
                      setState(() => _accountTypeFilter = value),
                ),
              ),
              const SizedBox(width: 8),
              PopupMenuButton<String>(
                tooltip: _bookmarkText('autoSortDefault', 'Sort accounts'),
                onSelected: (mode) => setState(() => _accountSortMode = mode),
                itemBuilder: (_) => [
                  PopupMenuItem(
                    value: 'DEFAULT',
                    child: Text(_bookmarkText('autoSortDefault', 'Default')),
                  ),
                  PopupMenuItem(
                    value: 'ASCENDING',
                    child: Text(
                      _bookmarkText('autoSortNameAscending', 'Name ascending'),
                    ),
                  ),
                  PopupMenuItem(
                    value: 'DESCENDING',
                    child: Text(
                      _bookmarkText(
                        'autoSortNameDescending',
                        'Name descending',
                      ),
                    ),
                  ),
                ],
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 10,
                    vertical: 8,
                  ),
                  decoration: BoxDecoration(
                    border: Border.all(
                      color: Theme.of(context).colorScheme.outlineVariant,
                    ),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.sort, size: 18),
                      const SizedBox(width: 6),
                      Text(
                        _accountSortMode == 'DEFAULT'
                            ? _bookmarkText('autoSortDefaultCompact', 'Default')
                            : _accountSortMode == 'ASCENDING'
                            ? _bookmarkText(
                                'autoSortNameAscendingCompact',
                                'A–Z',
                              )
                            : _bookmarkText(
                                'autoSortNameDescendingCompact',
                                'Z–A',
                              ),
                      ),
                      const SizedBox(width: 4),
                      const Icon(Icons.arrow_drop_down, size: 18),
                    ],
                  ),
                ),
              ),
              const SizedBox(width: 4),
              IconButton(
                tooltip: _bookmarkText('autoNewAccount', 'Add'),
                onPressed: () => setState(() {
                  _showAccountEditor = true;
                  _editingAccountId = null;
                }),
                icon: const Icon(Icons.add),
              ),
            ],
          ),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(10, 2, 10, 8),
          child: TextField(
            controller: _accountSearchController,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              hintText: _bookmarkText('commonSearch', 'Search'),
              prefixIcon: const Icon(Icons.search),
              isDense: true,
              border: const OutlineInputBorder(),
              suffixIcon: _accountSearchController.text.isEmpty
                  ? null
                  : IconButton(
                      tooltip: _bookmarkText('autoClear', 'Clear'),
                      onPressed: () {
                        _accountSearchController.clear();
                        setState(() {});
                      },
                      icon: const Icon(Icons.close),
                    ),
            ),
          ),
        ),
        Expanded(
          child: FutureBuilder<List<UiptvAccountSummary>>(
            future: _accounts,
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting) {
                return const Center(child: CircularProgressIndicator());
              }
              if (snapshot.hasError) return _error(snapshot.error);
              final query = _accountSearchController.text.trim().toLowerCase();
              final allAccounts =
                  snapshot.data ?? const <UiptvAccountSummary>[];
              final accounts = allAccounts
                  .where(
                    (account) =>
                        query.isEmpty ||
                        account.accountName.toLowerCase().contains(query),
                  )
                  .where((account) {
                    return switch (_accountTypeFilter) {
                      'ALL' => true,
                      'M3U8' =>
                        account.type == 'M3U8_LOCAL' ||
                            account.type == 'M3U8_URL',
                      _ => account.type == _accountTypeFilter,
                    };
                  })
                  .toList();
              if (accounts.isEmpty) {
                final emptyTerm = query.isEmpty
                    ? _bookmarkText('autoAccount', 'accounts')
                    : query;
                return Center(
                  child: Text(
                    _bookmarkText(
                      'autoNothingFoundFor',
                      'Nothing found for {0}',
                    ).replaceFirst('{0}', emptyTerm),
                  ),
                );
              }
              final originalOrder = {
                for (var index = 0; index < allAccounts.length; index++)
                  allAccounts[index].dbId: index,
              };
              accounts.sort((a, b) {
                if (_accountSortMode == 'DEFAULT') {
                  if (a.pinToTop != b.pinToTop) return a.pinToTop ? -1 : 1;
                  return originalOrder[a.dbId]!.compareTo(
                    originalOrder[b.dbId]!,
                  );
                }
                final comparison = a.accountName.toLowerCase().compareTo(
                  b.accountName.toLowerCase(),
                );
                return _accountSortMode == 'DESCENDING'
                    ? -comparison
                    : comparison;
              });
              return LayoutBuilder(
                builder: (context, constraints) {
                  return GridView.builder(
                    padding: const EdgeInsets.fromLTRB(8, 2, 8, 8),
                    gridDelegate: desktopAccountGridDelegate(
                      availableWidth: constraints.maxWidth,
                    ),
                    itemCount: accounts.length,
                    itemBuilder: (context, index) {
                      final account = accounts[index];
                      final selected = _selectedAccount?.dbId == account.dbId;
                      final menuItems = _accountMenuItems(account);
                      return DesktopContextMenuRegion(
                        items: menuItems,
                        onSelected: (action) =>
                            _handleAccountMenu(account, action),
                        child: DesktopAccountCard(
                          account: account,
                          selected: selected,
                          categoriesLabel: _bookmarkText(
                            'autoCategories',
                            'Categories',
                          ),
                          channelsLabel: _bookmarkText(
                            'autoChannels',
                            'Channels',
                          ),
                          manageLabel: _bookmarkText('autoManage', 'Manage'),
                          menuItems: menuItems,
                          onMenuSelected: (action) =>
                              _handleAccountMenu(account, action),
                          onTap: () => _selectAccount(account),
                        ),
                      );
                    },
                  );
                },
              );
            },
          ),
        ),
      ],
    );
  }

  List<PopupMenuEntry<String>> _accountMenuItems(UiptvAccountSummary account) {
    final items = <PopupMenuEntry<String>>[
      PopupMenuItem(
        value: 'manage',
        child: Text(
          _bookmarkText('autoEditManageAccount', 'Edit/Manage Account'),
        ),
      ),
      PopupMenuItem(
        value: 'itv',
        child: Text(_bookmarkText('autoTvChannels', 'TV Channels')),
      ),
    ];
    if (account.type == 'STALKER_PORTAL' || account.type == 'XTREME_API') {
      items
        ..add(
          PopupMenuItem(
            value: 'vod',
            child: Text(_bookmarkText('autoVod', 'VOD')),
          ),
        )
        ..add(
          PopupMenuItem(
            value: 'series',
            child: Text(_bookmarkText('autoSeries', 'Series')),
          ),
        );
    }
    if (const {
      'STALKER_PORTAL',
      'XTREME_API',
      'M3U8_URL',
      'M3U8_LOCAL',
    }.contains(account.type)) {
      items.add(
        PopupMenuItem(
          value: 'reload',
          child: Text(_bookmarkText('autoReloadCache', 'Reload Cache')),
        ),
      );
    }
    items.add(const PopupMenuDivider());
    items.add(
      PopupMenuItem(
        value: 'delete',
        child: Text(_bookmarkText('autoDeleteAccount', 'Delete Account')),
      ),
    );
    return items;
  }

  void _selectAccount(UiptvAccountSummary account, {String mode = 'itv'}) {
    setState(() {
      _selectedAccount = account;
      _selectedMode = mode;
      _selectedCategory = null;
      _catalogueSearchController.clear();
      _categories = widget.api.categories(accountId: account.dbId, mode: mode);
      _channels = null;
      _additionalChannels.clear();
      _lastChannelPage = null;
    });
  }

  void _handleAccountMenu(UiptvAccountSummary account, String action) {
    switch (action) {
      case 'manage':
        setState(() {
          _showAccountEditor = true;
          _editingAccountId = account.dbId;
        });
        break;
      case 'itv':
      case 'vod':
      case 'series':
        _selectAccount(account, mode: action);
        break;
      case 'reload':
        setState(() {
          _cacheReloadInitialAccountIds = [account.dbId];
          _section = _DesktopSection.cacheReload;
        });
        break;
      case 'delete':
        _deleteAccount(account);
        break;
    }
  }

  Future<void> _deleteAccount(UiptvAccountSummary account) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_bookmarkText('commonConfirm', 'Confirm')),
        content: Text(
          '${_bookmarkText('autoDeleteAccount', 'Delete Account')}: ${account.accountName}?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_bookmarkText('autoCancel', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(_bookmarkText('autoDeleteAccount', 'Delete Account')),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await widget.api.deleteAccount(account.dbId);
      if (!mounted) return;
      setState(() {
        _accounts = widget.api.accounts();
        if (_selectedAccount?.dbId == account.dbId) {
          _selectedAccount = null;
          _selectedCategory = null;
          _categories = null;
          _channels = null;
        }
        if (_editingAccountId == account.dbId) _showAccountEditor = false;
      });
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  Widget _buildAccountCatalogContent({required bool singleColumn}) {
    final selectedAccount = _selectedAccount;
    final selectedCategory = _selectedCategory;
    if (selectedAccount != null && selectedCategory != null) {
      return Column(
        children: [
          ListTile(
            leading: IconButton(
              tooltip: _bookmarkText('autoBack', 'Back'),
              onPressed: () => setState(() {
                _selectedCategory = null;
                _channels = null;
                _catalogueSearchController.clear();
                _additionalChannels.clear();
                _lastChannelPage = null;
              }),
              icon: const Icon(Icons.arrow_back),
            ),
            title: Text(
              selectedCategory.title,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              selectedAccount.accountName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: singleColumn
                ? IconButton(
                    key: const Key('account-catalog-close'),
                    tooltip: _bookmarkText('autoClose', 'Close'),
                    onPressed: _closeAccountBrowser,
                    icon: const Icon(Icons.close),
                  )
                : null,
          ),
          Expanded(
            child: FutureBuilder<UiptvChannelPage>(
              future: _channels,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _error(snapshot.error);
                }
                final firstPage = snapshot.data;
                if (firstPage == null ||
                    firstPage.items.isEmpty && _additionalChannels.isEmpty) {
                  return const Center(child: Text('No channels'));
                }
                final channels = [...firstPage.items, ..._additionalChannels];
                final query = _catalogueSearchController.text
                    .trim()
                    .toLowerCase();
                final visibleChannels = query.isEmpty
                    ? channels
                    : channels
                          .where(
                            (channel) =>
                                channel.name.toLowerCase().contains(query) ||
                                channel.description.toLowerCase().contains(
                                  query,
                                ),
                          )
                          .toList();
                final page = _lastChannelPage ?? firstPage;
                return Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(14, 4, 14, 8),
                      child: TextField(
                        controller: _catalogueSearchController,
                        onChanged: (_) => setState(() {}),
                        decoration: InputDecoration(
                          prefixIcon: Icon(Icons.search),
                          hintText: _bookmarkText('commonSearch', 'Search'),
                          isDense: true,
                        ),
                      ),
                    ),
                    Expanded(
                      child: NotificationListener<ScrollNotification>(
                        onNotification: (notification) {
                          if (notification.metrics.extentAfter < 300 &&
                              page.hasMore &&
                              !_loadingMoreChannels) {
                            _loadMoreChannels(
                              page,
                              selectedAccount,
                              selectedCategory,
                            );
                          }
                          return false;
                        },
                        child: GridView.builder(
                          padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
                          gridDelegate: desktopChannelGridDelegate(),
                          itemCount:
                              visibleChannels.length +
                              (_loadingMoreChannels ? 1 : 0),
                          itemBuilder: (context, index) {
                            if (index >= visibleChannels.length) {
                              return const Center(
                                child: CircularProgressIndicator(),
                              );
                            }
                            return _channelCard(
                              visibleChannels[index],
                              selectedAccount,
                              selectedCategory,
                              channelQueue: channels,
                            );
                          },
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
        ],
      );
    }

    if (selectedAccount != null) {
      return Column(
        children: [
          ListTile(
            leading: singleColumn
                ? IconButton(
                    tooltip: _bookmarkText('autoBack', 'Back'),
                    onPressed: _closeAccountBrowser,
                    icon: const Icon(Icons.arrow_back),
                  )
                : null,
            title: Text(
              selectedAccount.accountName,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            subtitle: Text(
              selectedAccount.typeDisplay.isNotEmpty
                  ? selectedAccount.typeDisplay
                  : selectedAccount.type,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            trailing: singleColumn
                ? IconButton(
                    key: const Key('account-catalog-close'),
                    tooltip: _bookmarkText('autoClose', 'Close'),
                    onPressed: _closeAccountBrowser,
                    icon: const Icon(Icons.close),
                  )
                : null,
          ),
          _accountModePills(selectedAccount, fillAvailable: singleColumn),
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 4, 14, 6),
            child: TextField(
              controller: _catalogueSearchController,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search),
                hintText: _bookmarkText('commonSearch', 'Search'),
                isDense: true,
                border: const OutlineInputBorder(),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 3),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text(
                '${_bookmarkText('autoCategories', 'Categories')} - ${selectedAccount.accountName} [${selectedAccount.type}]',
                style: Theme.of(context).textTheme.titleSmall
                    ?.copyWith(fontWeight: FontWeight.w600),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
          Expanded(
            child: FutureBuilder<List<UiptvCategorySummary>>(
              future: _categories,
              builder: (context, snapshot) {
                if (snapshot.connectionState == ConnectionState.waiting) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _error(snapshot.error);
                }
                final categories = snapshot.data ?? const [];
                final query = _catalogueSearchController.text
                    .trim()
                    .toLowerCase();
                final visibleCategories = categories
                    .where(
                      (category) =>
                          query.isEmpty ||
                          category.title.toLowerCase().contains(query) ||
                          category.alias.toLowerCase().contains(query),
                    )
                    .toList();
                if (visibleCategories.isEmpty) {
                  final emptyTerm = categories.isEmpty
                      ? _bookmarkText('autoCategories', 'categories')
                      : query;
                  return Center(
                    child: Text(
                      _bookmarkText(
                        'autoNothingFoundFor',
                        'Nothing found for {0}',
                      ).replaceFirst('{0}', emptyTerm),
                    ),
                  );
                }
                return ListView.builder(
                  padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
                  itemCount: visibleCategories.length,
                  itemBuilder: (context, index) {
                    final category = visibleCategories[index];
                    return _categoryRow(
                      selectedAccount,
                      category,
                      onTap: () => setState(() {
                        _selectedCategory = category;
                        _catalogueSearchController.clear();
                        _additionalChannels.clear();
                        _lastChannelPage = null;
                        _channels = widget.api.channels(
                          accountId: selectedAccount.dbId,
                          categoryDbId: category.dbId,
                          mode: _selectedMode,
                        );
                      }),
                    );
                  },
                );
              },
            ),
          ),
        ],
      );
    }

    return const SizedBox.shrink();
  }

  Widget _categoryRow(
    UiptvAccountSummary account,
    UiptvCategorySummary category, {
    required VoidCallback onTap,
  }) {
    final menuItems = category.dbId == 'all'
        ? const <PopupMenuEntry<String>>[]
        : <PopupMenuEntry<String>>[
            PopupMenuItem(
              value: 'remove-cache',
              child: Text(_bookmarkText('autoRemove', 'Remove from cache')),
            ),
          ];
    final card = Card(
      margin: const EdgeInsets.symmetric(vertical: 3),
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant),
      ),
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.only(left: 14, right: 4),
          child: Row(
            children: [
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 11),
                  child: Text(
                    category.title,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ),
              if (menuItems.isNotEmpty)
                PopupMenuButton<String>(
                  tooltip: _bookmarkText('autoManage', 'Manage'),
                  onSelected: (_) =>
                      _removeCategoryFromCache(account, category),
                  itemBuilder: (_) => menuItems,
                ),
            ],
          ),
        ),
      ),
    );
    return DesktopContextMenuRegion(
      items: menuItems,
      onSelected: (_) => _removeCategoryFromCache(account, category),
      child: card,
    );
  }

  Future<void> _removeCategoryFromCache(
    UiptvAccountSummary account,
    UiptvCategorySummary category,
  ) async {
    try {
      await widget.api.removeCachedCategories(
        accountId: account.dbId,
        mode: _selectedMode,
        categoryIds: [category.dbId],
      );
      if (!mounted) return;
      setState(() {
        _categories = widget.api.categories(
          accountId: account.dbId,
          mode: _selectedMode,
        );
        if (_selectedCategory?.dbId == category.dbId) {
          _selectedCategory = null;
          _channels = null;
        }
      });
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(error.toString())));
    }
  }

  Widget _channelCard(
    UiptvChannelSummary channel,
    UiptvAccountSummary account,
    UiptvCategorySummary category, {
    List<UiptvChannelSummary>? channelQueue,
  }) {
    final menuItems = <PopupMenuEntry<String>>[
      PopupMenuItem(
        value: 'play',
        child: Text(_bookmarkText('autoPlay', 'Play')),
      ),
      if (channel.dbId.isNotEmpty)
        PopupMenuItem(
          value: 'favourite',
          child: Text(_bookmarkText('autoAdd', 'Add to favourites')),
        ),
    ];
    void onMenuSelected(String action) {
      if (action == 'play') {
        _playChannel(
          account.dbId,
          category.dbId,
          channel.playbackId.isEmpty ? channel.dbId : channel.playbackId,
          mode: _selectedMode,
          guideChannelDbId: channel.dbId,
          channelName: channel.name,
          channelQueue: channelQueue,
        );
      } else if (action == 'favourite') {
        _addLiveBookmark(account.dbId, category.dbId, channel.dbId);
      }
    }

    final card = Material(
      color: Theme.of(context).colorScheme.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Theme.of(context).colorScheme.outlineVariant),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => _playChannel(
          account.dbId,
          category.dbId,
          channel.playbackId.isEmpty ? channel.dbId : channel.playbackId,
          mode: _selectedMode,
          guideChannelDbId: channel.dbId,
          channelName: channel.name,
          channelQueue: channelQueue,
        ),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            children: [
              SizedBox(
                width: 52,
                height: 52,
                child: channel.logo.isEmpty
                    ? const Icon(
                        Icons.live_tv_outlined,
                        size: 34,
                        color: Color(0xFF6D7B87),
                      )
                    : Image.network(
                        channel.logo,
                        fit: BoxFit.contain,
                        errorBuilder: (_, _, _) =>
                            const Icon(Icons.live_tv_outlined, size: 34),
                      ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      channel.name,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      channel.number.isEmpty
                          ? channel.description
                          : '${channel.number}  ${channel.description}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12),
                    ),
                  ],
                ),
              ),
              PopupMenuButton<String>(
                tooltip: _bookmarkText('autoManage', 'Actions'),
                onSelected: onMenuSelected,
                itemBuilder: (_) => menuItems,
              ),
            ],
          ),
        ),
      ),
    );
    return DesktopContextMenuRegion(
      items: menuItems,
      onSelected: onMenuSelected,
      child: card,
    );
  }

  Future<void> _playChannel(
    String accountDbId,
    String categoryDbId,
    String channelDbId, {
    String mode = 'itv',
    String? guideChannelDbId,
    String? channelName,
    List<UiptvChannelSummary>? channelQueue,
  }) async {
    try {
      final playback = await widget.api.resolveLivePlayback(
        accountId: accountDbId,
        categoryDbId: categoryDbId,
        channelDbId: channelDbId,
        mode: mode,
      );
      final url = playback['url'] as String?;
      if (url == null || url.isEmpty) {
        throw UiptvApiException(
          422,
          'The selected channel did not resolve to a stream',
        );
      }
      await _player.playUrl(url);
      if (mounted) {
        setState(() {
          _showPlayer = true;
          _playerGuideRequestGeneration++;
          _playerGuideLoading = false;
          _playerGuideContext = mode == 'itv' && guideChannelDbId != null
              ? (
                  accountId: accountDbId,
                  categoryId: categoryDbId,
                  channelId: guideChannelDbId,
                )
              : null;
          _playerGuideBookmarkId = null;
          _playerGuideChannelName = channelName;
          _playerGuide = null;
          _playerGuideError = null;
          _playerChannelQueue = List.unmodifiable(channelQueue ?? const []);
          _playerChannelIndex = _playerChannelQueue.indexWhere(
            (channel) => channel.dbId == guideChannelDbId,
          );
          _playerChannelAccountDbId = accountDbId;
          _playerChannelCategoryDbId = categoryDbId;
          _playerChannelMode = mode;
        });
        if (mode == 'itv' &&
            !_wideView &&
            _menuSettings['epgEnabled'] == true) {
          unawaited(_loadPlayerGuide());
        }
      }
    } catch (error) {
      if (!mounted) {
        return;
      }
      final message = error is UiptvApiException
          ? error.message
          : 'Unable to start channel playback';
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text(message)));
    }
  }

  Future<void> _navigatePlayerChannel(int direction) async {
    final nextIndex = _playerChannelIndex + direction;
    if (_playerChannelNavigationBusy ||
        nextIndex < 0 ||
        nextIndex >= _playerChannelQueue.length ||
        _playerChannelAccountDbId == null ||
        _playerChannelCategoryDbId == null) {
      return;
    }
    final channel = _playerChannelQueue[nextIndex];
    setState(() => _playerChannelNavigationBusy = true);
    try {
      await _playChannel(
        _playerChannelAccountDbId!,
        _playerChannelCategoryDbId!,
        channel.playbackId.isEmpty ? channel.dbId : channel.playbackId,
        mode: _playerChannelMode,
        guideChannelDbId: channel.dbId,
        channelName: channel.name,
        channelQueue: _playerChannelQueue,
      );
    } finally {
      if (mounted) setState(() => _playerChannelNavigationBusy = false);
    }
  }

  Future<void> _playBookmark(String bookmarkDbId, {String? channelName}) async {
    try {
      final playback = await widget.api.resolveBookmarkPlayback(bookmarkDbId);
      final url = playback['url'] as String?;
      if (url == null || url.isEmpty) {
        throw UiptvApiException(
          422,
          'The selected favourite did not resolve to a stream',
        );
      }
      final mode = (playback['mode'] as String? ?? 'itv').toLowerCase();
      final playbackTitle = (playback['title'] as String?) ?? channelName ?? '';
      await _player.playUrl(url);
      if (mounted) {
        final canLoadGuide =
            mode == 'itv' && _favouriteCategory != _recentBookmarkCategoryId;
        setState(() {
          _showPlayer = true;
          _playerChannelMode = mode;
          _playerChannelQueue = const [];
          _playerChannelIndex = -1;
          _playerChannelAccountDbId = null;
          _playerChannelCategoryDbId = null;
          _playerGuideRequestGeneration++;
          _playerGuideLoading = false;
          _playerGuideContext = null;
          _playerGuideBookmarkId = canLoadGuide ? bookmarkDbId : null;
          _playerGuideChannelName = playbackTitle.isEmpty
              ? null
              : playbackTitle;
          _playerGuide = null;
          _playerGuideError = null;
        });
        if (canLoadGuide && !_wideView && _menuSettings['epgEnabled'] == true) {
          unawaited(_loadPlayerGuide());
        }
      }
    } catch (error) {
      if (mounted) {
        final message = error is UiptvApiException
            ? error.message
            : 'Unable to start favourite playback';
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    }
  }

  Future<void> _addLiveBookmark(
    String accountDbId,
    String categoryDbId,
    String channelDbId,
  ) async {
    try {
      await widget.api.addLiveBookmark(
        accountDbId: accountDbId,
        categoryDbId: categoryDbId,
        channelDbId: channelDbId,
      );
      if (mounted) {
        setState(() => _bookmarks = _loadFavouriteItems(_favouriteCategory));
        ScaffoldMessenger.of(context)
            .showSnackBar(const SnackBar(content: Text('Added to favourites')));
      }
    } catch (error) {
      if (mounted) {
        final message = error is UiptvApiException
            ? error.message
            : 'Unable to add favourite';
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    }
  }

  Future<void> _loadMoreWatchingNow(UiptvWatchingNowSnapshot current) async {
    setState(() => _loadingMoreWatchingNow = true);
    try {
      final next = await widget.api.watchingNow(offset: current.offset + 100);
      if (!mounted) {
        return;
      }
      final entries = [...current.entries, ...next.entries]
        ..sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
      setState(() {
        _watchingNow = Future.value(
          UiptvWatchingNowSnapshot(
            entries: entries,
            offset: next.offset,
            hasMoreSeries: next.hasMoreSeries,
            hasMoreVod: next.hasMoreVod,
          ),
        );
      });
    } catch (error) {
      if (mounted) {
        final message = error is UiptvApiException
            ? error.message
            : 'Unable to load more watching history';
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    } finally {
      if (mounted) {
        setState(() => _loadingMoreWatchingNow = false);
      }
    }
  }

  Widget _accountModePills(
    UiptvAccountSummary account, {
    required bool fillAvailable,
  }) {
    final supportsVodAndSeries =
        account.type.toUpperCase().contains('STALKER') ||
        account.type.toUpperCase().contains('XTREME');
    return DesktopAccountModePillBar(
      selectedMode: _selectedMode,
      supportsVodAndSeries: supportsVodAndSeries,
      fillAvailable: fillAvailable,
      labels: {
        'autoAccount': _bookmarkText('autoAccount', 'Accounts'),
        'autoTvChannels': _bookmarkText('autoTvChannels', 'TV Channels'),
        'autoVod': _bookmarkText('autoVod', 'VOD'),
        'autoSeries': _bookmarkText('autoSeries', 'Series'),
      },
      onSelected: (mode) {
        if (mode == 'accounts') {
          _closeAccountBrowser();
          return;
        }
        setState(() {
          _selectedMode = mode;
          _selectedCategory = null;
          _channels = null;
          _additionalChannels.clear();
          _lastChannelPage = null;
          _categories = widget.api.categories(
            accountId: account.dbId,
            mode: mode,
          );
        });
      },
    );
  }

  void _closeAccountBrowser() {
    setState(() {
      _selectedAccount = null;
      _selectedCategory = null;
      _selectedMode = 'itv';
      _categories = null;
      _channels = null;
      _catalogueSearchController.clear();
      _additionalChannels.clear();
      _lastChannelPage = null;
    });
  }

  Future<void> _playWatchingNow(UiptvWatchingNowEntry entry) async {
    await _playWatchingReference(
      entry.playbackId,
      entry.isSeries ? 'series' : 'vod',
    );
  }

  Future<void> _playWatchingEpisode(UiptvWatchingNowEpisode episode) async {
    await _playWatchingReference(episode.playbackId, 'series');
  }

  Future<void> _removeWatchingNow(UiptvWatchingNowEntry entry) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_bookmarkText('autoRemove', 'Remove')),
        content: Text(
          _bookmarkText(
            'autoRemoveFromWatchingNowConfirm',
            'Remove "{0}" from Watching Now?',
          ).replaceAll('{0}', entry.title),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_bookmarkText('commonClose', 'Close')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(_bookmarkText('commonConfirm', 'Confirm')),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    try {
      await widget.api.removeWatchingNow(entry.playbackId);
      if (mounted) setState(() => _watchingNow = widget.api.watchingNow());
    } catch (error) {
      if (mounted) {
        final message = error is UiptvApiException
            ? error.message
            : 'Unable to remove this item from Watching Now';
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    }
  }

  Future<void> _playWatchingReference(String playbackId, String mode) async {
    try {
      final playback = await widget.api.resolveWatchingNowPlayback(
        playbackId,
        mode: mode,
      );
      final url = playback['url'] as String?;
      if (url == null || url.isEmpty) {
        throw UiptvApiException(
          422,
          'The selected item did not resolve to a stream',
        );
      }
      await _player.playUrl(url);
      if (mounted) {
        setState(() {
          _showPlayer = true;
          _playerGuideRequestGeneration++;
          _playerGuideLoading = false;
          _playerGuideContext = null;
          _playerGuideBookmarkId = null;
          _playerGuideChannelName = null;
          _playerGuide = null;
          _playerGuideError = null;
        });
      }
    } catch (error) {
      if (mounted) {
        final message = error is UiptvApiException
            ? error.message
            : 'Unable to resume playback';
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    }
  }

  Future<void> _loadMoreChannels(
    UiptvChannelPage currentPage,
    UiptvAccountSummary account,
    UiptvCategorySummary category,
  ) async {
    setState(() => _loadingMoreChannels = true);
    try {
      final nextPage = await widget.api.channels(
        accountId: account.dbId,
        categoryDbId: category.dbId,
        mode: _selectedMode,
        page: currentPage.nextPage,
        apiOffset: currentPage.apiOffset,
      );
      if (!mounted || _selectedCategory?.dbId != category.dbId) {
        return;
      }
      setState(() {
        _additionalChannels.addAll(nextPage.items);
        _lastChannelPage = nextPage;
        if (_playerChannelIndex >= 0 &&
            _playerChannelAccountDbId == account.dbId &&
            _playerChannelCategoryDbId == category.dbId) {
          final knownIds = _playerChannelQueue
              .map((channel) => channel.dbId)
              .toSet();
          _playerChannelQueue = List.unmodifiable([
            ..._playerChannelQueue,
            ...nextPage.items.where(
              (channel) => !knownIds.contains(channel.dbId),
            ),
          ]);
        }
      });
    } catch (error) {
      if (mounted) {
        final message = error is UiptvApiException
            ? error.message
            : 'Unable to load more channels';
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(message)));
      }
    } finally {
      if (mounted) {
        setState(() => _loadingMoreChannels = false);
      }
    }
  }

  Widget _error(Object? error) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Text(
          error is UiptvApiException
              ? error.message
              : 'Unable to connect to the local UIPTV API',
          textAlign: TextAlign.center,
        ),
      ),
    );
  }
}
