import 'dart:async';
import 'dart:io';

import 'package:file_selector/file_selector.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

Future<void> showDesktopSettingsDialog({
  required BuildContext context,
  required UiptvApiClient api,
  VoidCallback? onRestored,
  ValueChanged<Map<String, dynamic>>? onSettingsSaved,
}) {
  final screenSize = MediaQuery.sizeOf(context);
  return showDialog<void>(
    context: context,
    builder: (context) => Dialog(
      insetPadding: const EdgeInsets.all(24),
      clipBehavior: Clip.antiAlias,
      child: SizedBox(
        width: (screenSize.width - 48).clamp(280.0, 1120.0),
        height: (screenSize.height - 48).clamp(280.0, 820.0),
        child: DesktopSettingsScreen(
          api: api,
          onRestored: onRestored,
          onSettingsSaved: onSettingsSaved,
        ),
      ),
    ),
  );
}

class DesktopSettingsScreen extends StatefulWidget {
  const DesktopSettingsScreen({
    super.key,
    required this.api,
    this.onRestored,
    this.onSettingsSaved,
    this.initialTab = 'all',
  });

  final UiptvApiClient api;
  final VoidCallback? onRestored;
  final ValueChanged<Map<String, dynamic>>? onSettingsSaved;
  final String initialTab;

  @override
  State<DesktopSettingsScreen> createState() => _DesktopSettingsScreenState();
}

class _DesktopSettingsScreenState extends State<DesktopSettingsScreen> {
  static const _translationKeys = [
    'autoSettings',
    'commonAll',
    'commonClose',
    'commonSave',
    'commonSearch',
    'commonLight',
    'commonDark',
    'commonSystem',
    'autoRestore',
    'autoPlayer1',
    'autoPlayer2',
    'autoPlayer3',
    'autoEmbeddedPlayer',
    'configDefaultWebBrowserPlayer',
    'configVideoPlayers',
    'configAddPlayerPathsHint',
    'configWideView',
    'configResolveChainAndDeepRedirects',
    'configFilters',
    'filterLockStateToggleLabel',
    'filterLockStateLocked',
    'filterLockStateUnlocked',
    'filterLockStateNotSet',
    'configFiltersHelpText',
    'configFilterCategoriesPrompt',
    'configFilterChannelsPrompt',
    'filterLockChangePasswordAction',
    'filterLockChangePasswordTitle',
    'filterLockLockNowAction',
    'filterLockDisablePasswordAction',
    'filterLockDisablePasswordPrompt',
    'filterLockDisablePasswordTitle',
    'filterLockDisablePasswordWarning',
    'filterLockNewPasswordPrompt',
    'filterLockCurrentPasswordPrompt',
    'filterLockConfirmPasswordPrompt',
    'filterLockPasswordPrompt',
    'filterLockPasswordRequirements',
    'filterLockPasswordSetSuccess',
    'filterLockPasswordChangeSuccess',
    'filterLockPasswordDisableSuccess',
    'filterLockPasswordTooShort',
    'filterLockPasswordConfirmMismatch',
    'filterLockPasswordInvalid',
    'filterLockCurrentPasswordInvalid',
    'filterLockPasswordNotSet',
    'filterLockHiddenCategoriesPrompt',
    'filterLockSetPasswordAction',
    'filterLockSetPasswordTitle',
    'filterLockUnlockAction',
    'filterLockUnlockTitle',
    'filterLockStatusLocked',
    'filterLockStatusNotSet',
    'filterLockStatusUnlocked',
    'autoEnableParentalLockAccess',
    'autoDisableParentalLockAccess',
    'configDarkTheme',
    'configThemeZoom',
    'configLanguage',
    'configPlainTextMode',
    'configEnableThumbnails',
    'configShowBookmarkAndAccountCounts',
    'configEnableEpg',
    'configCacheExpiresInDays',
    'configCacheExpiryPrompt',
    'configDatabaseSyncTitle',
    'configDatabaseSyncDescription',
    'configExportDatabase',
    'configImportDatabase',
    'configExportDatabaseSuccess',
    'configExportDatabaseFailed',
    'configImportDatabaseSuccess',
    'configImportDatabaseFailed',
    'configRestoreBackupConfirm',
    'configDatabaseRestoreSummary',
    'configDatabaseSyncInProgress',
    'commonConfirm',
    'autoCancel',
    'configWebServer',
    'configServerPortPrompt',
    'configHttpsServerPortPrompt',
    'configAutoRunServerOnStartup',
    'configEnableHttpsServer',
    'configWebPlaybackProxyEnabled',
    'configStartServer',
    'configStopServer',
    'configTmdbMetadata',
    'configTmdbReadAccessToken',
    'configTmdbReadAccessTokenPrompt',
    'configTmdbApiGuideLink',
    'configTmdbApiKeyPageLink',
  ];

  static const _settingsSubsections = <String, List<(String, String, String)>>{
    'players': [
      ('selection', 'configVideoPlayers', 'Player selection'),
      ('paths', 'configAddPlayerPathsHint', 'Player paths'),
      ('playback', 'configWideView', 'Playback behavior'),
    ],
    'filters': [
      ('access', 'filterLockStateToggleLabel', 'Parental lock access'),
      ('categories', 'configFilterCategoriesPrompt', 'Protected categories'),
      ('channels', 'configFilterChannelsPrompt', 'Protected channels'),
    ],
    'theme': [
      ('appearance', 'configDarkTheme', 'Appearance'),
      ('language', 'configLanguage', 'Language'),
      ('display', 'configThemeZoom', 'Display'),
      ('catalog', 'configPlainTextMode', 'Catalogue'),
      ('cache', 'configCacheExpiresInDays', 'Cache'),
    ],
    'backup': [
      ('create', 'configExportDatabase', 'Create backup'),
      ('restore', 'configImportDatabase', 'Restore backup'),
    ],
    'server': [
      ('ports', 'configServerPortPrompt', 'Server ports'),
      ('startup', 'configAutoRunServerOnStartup', 'Startup'),
      ('proxy', 'configWebPlaybackProxyEnabled', 'Playback proxy'),
      ('status', 'configStartServer', 'Server status'),
    ],
    'tmdb': [
      ('credentials', 'configTmdbReadAccessToken', 'API credentials'),
      ('links', 'configTmdbApiGuideLink', 'Developer links'),
    ],
  };

  final TextEditingController _searchController = TextEditingController();
  final TextEditingController _player1Controller = TextEditingController();
  final TextEditingController _player2Controller = TextEditingController();
  final TextEditingController _player3Controller = TextEditingController();
  final TextEditingController _httpPortController = TextEditingController();
  final TextEditingController _httpsPortController = TextEditingController();
  final TextEditingController _cacheExpiryController = TextEditingController();
  final TextEditingController _tmdbTokenController = TextEditingController();
  final TextEditingController _filterCategoriesController =
      TextEditingController();
  final TextEditingController _filterChannelsController =
      TextEditingController();
  final ScrollController _settingsScrollController = ScrollController();
  final Map<String, GlobalKey> _anchorKeys = {};

  late Future<_SettingsData> _initialSettings;
  late Future<Map<String, dynamic>> _serverStatus;
  late Future<Map<String, dynamic>> _filterLockStatus;
  late Future<Map<String, dynamic>> _filterSettings;
  Timer? _filterLockRefreshTimer;
  Map<String, dynamic> _settings = const {};
  Map<String, String> _labels = const {};
  String _selectedTab = 'all';
  String _selectedDefaultPlayer = 'embedded';
  bool _initialized = false;
  bool _saving = false;
  bool _serverBusy = false;
  bool _filterBusy = false;
  bool _filterSettingsInitialized = false;
  bool _filterRestrictionsActive = true;
  bool _backupBusy = false;

  @override
  void initState() {
    super.initState();
    _selectedTab = widget.initialTab;
    _initialSettings = _loadSettings();
    _serverStatus = widget.api.internalServerStatus();
    _filterLockStatus = widget.api.filterLockStatus();
    _filterSettings = widget.api.filterLockFilters();
    _filterLockRefreshTimer = Timer.periodic(
      const Duration(seconds: 20),
      (_) => _refreshFilterLockStatus(),
    );
  }

  @override
  void dispose() {
    _settingsScrollController.dispose();
    _searchController.dispose();
    _player1Controller.dispose();
    _player2Controller.dispose();
    _player3Controller.dispose();
    _httpPortController.dispose();
    _httpsPortController.dispose();
    _cacheExpiryController.dispose();
    _tmdbTokenController.dispose();
    _filterCategoriesController.dispose();
    _filterChannelsController.dispose();
    _filterLockRefreshTimer?.cancel();
    super.dispose();
  }

  Future<_SettingsData> _loadSettings() async {
    final settings = await widget.api.settings();
    final labels = await widget.api.translations(_translationKeys);
    return _SettingsData(settings: settings, labels: labels);
  }

  void _hydrate(Map<String, dynamic> settings) {
    _settings = Map<String, dynamic>.from(settings);
    _player1Controller.text = settings['playerPath1'] as String? ?? '';
    _player2Controller.text = settings['playerPath2'] as String? ?? '';
    _player3Controller.text = settings['playerPath3'] as String? ?? '';
    final defaultPath = settings['defaultPlayerPath'] as String? ?? '';
    if (defaultPath == _player1Controller.text) {
      _selectedDefaultPlayer = 'player1';
    } else if (defaultPath == _player2Controller.text) {
      _selectedDefaultPlayer = 'player2';
    } else if (defaultPath == _player3Controller.text) {
      _selectedDefaultPlayer = 'player3';
    } else if (defaultPath == settings['webBrowserPlayerPath']) {
      _selectedDefaultPlayer = 'browser';
    } else {
      _selectedDefaultPlayer = 'embedded';
    }
    _settings['embeddedPlayer'] = _selectedDefaultPlayer == 'embedded';
    _httpPortController.text = settings['serverPort'] as String? ?? '';
    _httpsPortController.text = settings['httpsServerPort'] as String? ?? '';
    _cacheExpiryController.text = '${settings['cacheExpiryDays'] ?? 30}';
    _tmdbTokenController.clear();
    _initialized = true;
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_SettingsData>(
      future: _initialSettings,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting &&
            snapshot.data == null) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(child: Text(snapshot.error.toString()));
        }
        final data = snapshot.data;
        if (data == null) return const SizedBox.shrink();
        if (!_initialized) {
          _hydrate(data.settings);
          _labels = data.labels;
        }
        return _buildSettings(context);
      },
    );
  }

  Widget _buildSettings(BuildContext context) {
    final sections =
        <
          ({
            String id,
            String title,
            Widget child,
            List<(String, String, String)> subsections,
          })
        >[
          (
            id: 'players',
            title: _text('configVideoPlayers', 'Video Players'),
            child: _videoPlayersCard(),
            subsections: _settingsSubsections['players']!,
          ),
          (
            id: 'filters',
            title: _text('configFilters', 'Parental Lock'),
            child: _parentalLockCard(),
            subsections: _settingsSubsections['filters']!,
          ),
          (
            id: 'theme',
            title: _text('configDarkTheme', 'Theme'),
            child: _themeCard(),
            subsections: _settingsSubsections['theme']!,
          ),
          (
            id: 'backup',
            title: _text('configDatabaseSyncTitle', 'Backup & Restore'),
            child: _backupCard(),
            subsections: _settingsSubsections['backup']!,
          ),
          (
            id: 'server',
            title: _text('configWebServer', 'Internal Web Server'),
            child: _serverCard(),
            subsections: _settingsSubsections['server']!,
          ),
          (
            id: 'tmdb',
            title: _text('configTmdbMetadata', 'TMDB Metadata'),
            child: _tmdbCard(),
            subsections: _settingsSubsections['tmdb']!,
          ),
        ];
    final query = _searchController.text.trim().toLowerCase();
    final visibleSections = sections
        .where((section) {
          if (query.isEmpty) return true;
          return section.title.toLowerCase().contains(query) ||
              section.subsections.any(
                (subsection) =>
                    _subsectionLabel(subsection).toLowerCase().contains(query),
              );
        })
        .toList(growable: false);

    return LayoutBuilder(
      builder: (context, constraints) {
        final sidebarWidth = constraints.maxWidth < 720 ? 158.0 : 224.0;
        return Column(
          children: [
            _settingsDialogHeader(),
            const Divider(height: 1),
            Expanded(
              child: Row(
                children: [
                  SizedBox(
                    width: sidebarWidth,
                    child: _settingsSidebar(visibleSections, query),
                  ),
                  const VerticalDivider(width: 1, thickness: 1),
                  Expanded(child: _settingsContent(visibleSections)),
                ],
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _settingsDialogHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 12, 12),
      child: Row(
        children: [
          Expanded(
            child: Text(
              _text('autoSettings', 'Settings'),
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          IconButton(
            key: const ValueKey('settings-close'),
            tooltip: _text('commonClose', 'Close'),
            onPressed: () => Navigator.of(context).maybePop(),
            icon: const Icon(Icons.close),
          ),
        ],
      ),
    );
  }

  String _subsectionLabel((String, String, String) subsection) =>
      _text(subsection.$2, subsection.$3);

  GlobalKey _anchorKey(String id) => _anchorKeys.putIfAbsent(id, GlobalKey.new);

  Widget _anchor(String id, Widget child) =>
      KeyedSubtree(key: _anchorKey(id), child: child);

  void _navigateTo(String id, String sectionId) {
    setState(() => _selectedTab = sectionId);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final target = _anchorKey(id).currentContext;
      if (target != null) {
        unawaited(
          Scrollable.ensureVisible(
            target,
            duration: const Duration(milliseconds: 320),
            curve: Curves.easeInOut,
            alignment: 0.035,
          ),
        );
      }
    });
  }

  Widget _settingsSidebar(
    List<
      ({
        String id,
        String title,
        Widget child,
        List<(String, String, String)> subsections,
      })
    >
    visibleSections,
    String query,
  ) {
    final colors = Theme.of(context).colorScheme;
    return Material(
      color: colors.surfaceContainerLow,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(10, 12, 10, 10),
            child: _settingsSearchField(),
          ),
          Expanded(
            child: ListView(
              padding: const EdgeInsets.fromLTRB(6, 0, 6, 12),
              children: [
                for (final section in visibleSections)
                  _settingsNavigationSection(section, query, colors),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _settingsNavigationSection(
    ({
      String id,
      String title,
      Widget child,
      List<(String, String, String)> subsections,
    })
    section,
    String query,
    ColorScheme colors,
  ) {
    final sectionMatches = section.title.toLowerCase().contains(query);
    final subsections = section.subsections.where((subsection) {
      return query.isEmpty ||
          sectionMatches ||
          _subsectionLabel(subsection).toLowerCase().contains(query);
    });
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        ListTile(
          key: ValueKey('settings-nav-${section.id}'),
          dense: true,
          minTileHeight: 38,
          contentPadding: const EdgeInsets.symmetric(horizontal: 8),
          selected: _selectedTab == section.id,
          selectedTileColor: colors.primaryContainer,
          selectedColor: colors.onPrimaryContainer,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          leading: Icon(_settingsTabIcon(section.id), size: 18),
          title: Text(
            section.title,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium,
          ),
          onTap: () => _navigateTo(section.id, section.id),
        ),
        for (final subsection in subsections)
          Padding(
            padding: const EdgeInsets.only(left: 14),
            child: Row(
              children: [
                CustomPaint(
                  size: const Size(14, 32),
                  painter: _SettingsSubsectionConnectorPainter(
                    colors.outlineVariant,
                  ),
                ),
                Expanded(
                  child: TextButton(
                    key: ValueKey(
                      'settings-nav-sub-${section.id}-${subsection.$1}',
                    ),
                    style: TextButton.styleFrom(
                      alignment: Alignment.centerLeft,
                      minimumSize: const Size(0, 32),
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      foregroundColor: colors.onSurfaceVariant,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    onPressed: () => _navigateTo(
                      '${section.id}-${subsection.$1}',
                      section.id,
                    ),
                    child: Text(
                      _subsectionLabel(subsection),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.labelMedium,
                    ),
                  ),
                ),
              ],
            ),
          ),
      ],
    );
  }

  Widget _settingsSearchField() {
    return TextField(
      controller: _searchController,
      onChanged: (_) => setState(() {}),
      decoration: InputDecoration(
        isDense: true,
        hintText: _text('commonSearch', 'Search'),
        prefixIcon: const Icon(Icons.search),
      ),
    );
  }

  Widget _settingsContent(
    List<
      ({
        String id,
        String title,
        Widget child,
        List<(String, String, String)> subsections,
      })
    >
    visibleSections,
  ) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = (constraints.maxWidth - 28).clamp(1.0, 960.0);
        return Scrollbar(
          controller: _settingsScrollController,
          thumbVisibility: true,
          child: SingleChildScrollView(
            controller: _settingsScrollController,
            padding: const EdgeInsets.fromLTRB(14, 14, 18, 24),
            child: Align(
              alignment: Alignment.topLeft,
              child: SizedBox(
                width: width,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    for (final section in visibleSections)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: _anchor(section.id, section.child),
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  IconData _settingsTabIcon(String id) => switch (id) {
    'all' => Icons.tune,
    'players' => Icons.play_circle_outline,
    'filters' => Icons.lock_outline,
    'theme' => Icons.palette_outlined,
    'backup' => Icons.backup_outlined,
    'server' => Icons.dns_outlined,
    'tmdb' => Icons.movie_outlined,
    _ => Icons.settings_outlined,
  };

  Widget _videoPlayersCard() {
    return _card('configVideoPlayers', 'Video Players', [
      Text(
        _text(
          'configAddPlayerPathsHint',
          'Add player paths and select the default player.',
        ),
      ),
      const SizedBox(height: 10),
      _anchor(
        'players-selection',
        RadioGroup<String>(
          groupValue: _selectedDefaultPlayer,
          onChanged: (selection) {
            if (selection == null) return;
            setState(() {
              _selectedDefaultPlayer = selection;
              _settings['embeddedPlayer'] = selection == 'embedded';
              if (selection != 'embedded') _settings['wideView'] = false;
            });
          },
          child: Column(
            children: [
              _playerChoice('player1', _text('autoPlayer1', 'Player 1')),
              _anchor(
                'players-paths',
                _pathField('autoPlayer1', 'Player 1', _player1Controller),
              ),
              _playerChoice('player2', _text('autoPlayer2', 'Player 2')),
              _pathField('autoPlayer2', 'Player 2', _player2Controller),
              _playerChoice('player3', _text('autoPlayer3', 'Player 3')),
              _pathField('autoPlayer3', 'Player 3', _player3Controller),
              _playerChoice(
                'embedded',
                _text('autoEmbeddedPlayer', 'Embedded Player (VLC)'),
              ),
              _playerChoice(
                'browser',
                _text('configDefaultWebBrowserPlayer', 'Web Browser Player'),
              ),
            ],
          ),
        ),
      ),
      _anchor(
        'players-playback',
        Column(
          children: [
            if (_selectedDefaultPlayer == 'embedded')
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(_text('configWideView', 'Wide View')),
                value: _settings['wideView'] as bool? ?? false,
                onChanged: (value) =>
                    setState(() => _settings['wideView'] = value),
              ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                _text(
                  'configResolveChainAndDeepRedirects',
                  'Resolve redirect chains',
                ),
              ),
              value:
                  _settings['resolveChainAndDeepRedirects'] as bool? ?? false,
              onChanged: (value) => setState(() {
                _settings['resolveChainAndDeepRedirects'] = value;
              }),
            ),
          ],
        ),
      ),
      _saveActions(),
    ]);
  }

  Widget _playerChoice(String value, String title) {
    return RadioListTile<String>(
      contentPadding: EdgeInsets.zero,
      title: Text(title),
      value: value,
    );
  }

  Widget _pathField(
    String key,
    String fallback,
    TextEditingController controller,
  ) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: TextField(
        controller: controller,
        decoration: InputDecoration(
          labelText: _text(key, fallback),
          suffixIcon: IconButton(
            onPressed: () async {
              final file = await openFile();
              if (file != null && mounted) {
                setState(() => controller.text = file.path);
              }
            },
            icon: const Icon(Icons.more_horiz),
          ),
        ),
      ),
    );
  }

  Widget _parentalLockCard() {
    return _card('configFilters', 'Parental Lock', [
      FutureBuilder<Map<String, dynamic>>(
        future: _filterLockStatus,
        builder: (context, statusSnapshot) {
          if (statusSnapshot.connectionState == ConnectionState.waiting &&
              statusSnapshot.data == null) {
            return const Center(child: CircularProgressIndicator());
          }
          if (statusSnapshot.hasError) {
            return Text(statusSnapshot.error.toString());
          }
          final status = statusSnapshot.data ?? const {};
          final configured = status['passwordConfigured'] as bool? ?? false;
          final unlocked = status['unlocked'] as bool? ?? false;
          final statusKey = !configured
              ? 'filterLockStatusNotSet'
              : unlocked
              ? 'filterLockStatusUnlocked'
              : 'filterLockStatusLocked';
          final statusFallback = !configured
              ? 'No parental lock password is configured.'
              : unlocked
              ? 'Parental lock controls are unlocked.'
              : 'Parental lock controls are locked.';
          final statusMessage = _filterLockStatusText(
            statusKey,
            statusFallback,
            status,
          );
          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _anchor(
                'filters-access',
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: Text(
                    _text('filterLockStateToggleLabel', 'Parental lock access'),
                  ),
                  subtitle: Text(statusMessage),
                  leading: Icon(
                    unlocked ? Icons.lock_open_outlined : Icons.lock_outline,
                    color: unlocked ? Colors.green : null,
                  ),
                ),
              ),
              if (!unlocked)
                FilledButton(
                  onPressed: _filterBusy
                      ? null
                      : () => _editFilterLockPassword(
                          configured ? 'unlock' : 'setPassword',
                          status,
                        ),
                  child: Text(
                    _text(
                      configured
                          ? 'filterLockUnlockAction'
                          : 'filterLockSetPasswordAction',
                      configured ? 'Unlock' : 'Set parental lock password',
                    ),
                  ),
                ),
              if (unlocked) ...[
                _filterSettingsEditor(),
                if (configured)
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      OutlinedButton(
                        onPressed: _filterBusy
                            ? null
                            : () => _editFilterLockPassword(
                                'changePassword',
                                status,
                              ),
                        child: Text(
                          _text(
                            'filterLockChangePasswordAction',
                            'Change Password',
                          ),
                        ),
                      ),
                      OutlinedButton(
                        onPressed: _filterBusy
                            ? null
                            : () => _editFilterLockPassword(
                                'clearPassword',
                                status,
                              ),
                        child: Text(
                          _text(
                            'filterLockDisablePasswordAction',
                            'Disable parental lock password',
                          ),
                        ),
                      ),
                      TextButton(
                        onPressed: _filterBusy
                            ? null
                            : () => _applyFilterLockAction('lock'),
                        child: Text(
                          _text('filterLockLockNowAction', 'Lock now'),
                        ),
                      ),
                    ],
                  ),
                if (!configured)
                  OutlinedButton(
                    onPressed: _filterBusy
                        ? null
                        : () => _editFilterLockPassword('setPassword', status),
                    child: Text(
                      _text(
                        'filterLockSetPasswordAction',
                        'Set parental lock password',
                      ),
                    ),
                  ),
              ],
              const SizedBox(height: 8),
              Text(_text('configFiltersHelpText', '')),
            ],
          );
        },
      ),
    ]);
  }

  Widget _filterSettingsEditor() {
    return FutureBuilder<Map<String, dynamic>>(
      future: _filterSettings,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting &&
            snapshot.data == null) {
          return const Padding(
            padding: EdgeInsets.all(12),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (snapshot.hasError) {
          return Text(
            _text(
              'filterLockHiddenCategoriesPrompt',
              snapshot.error.toString(),
            ),
          );
        }
        final filters = snapshot.data ?? const {};
        if (!_filterSettingsInitialized) {
          _filterCategoriesController.text =
              filters['filterCategoriesList'] as String? ?? '';
          _filterChannelsController.text =
              filters['filterChannelsList'] as String? ?? '';
          _filterRestrictionsActive =
              filters['restrictionsActive'] as bool? ?? true;
          _filterSettingsInitialized = true;
        }
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                _text(
                  'autoEnableParentalLockAccess',
                  'Enable parental lock access',
                ),
              ),
              value: _filterRestrictionsActive,
              onChanged: (value) {
                setState(() {
                  _filterRestrictionsActive = value;
                });
              },
            ),
            _anchor(
              'filters-categories',
              TextField(
                controller: _filterCategoriesController,
                maxLines: 3,
                decoration: InputDecoration(
                  labelText: _text(
                    'configFilterCategoriesPrompt',
                    'Protected categories',
                  ),
                ),
              ),
            ),
            const SizedBox(height: 8),
            _anchor(
              'filters-channels',
              TextField(
                controller: _filterChannelsController,
                maxLines: 3,
                decoration: InputDecoration(
                  labelText: _text(
                    'configFilterChannelsPrompt',
                    'Protected channels',
                  ),
                ),
              ),
            ),
            Align(
              alignment: Alignment.centerRight,
              child: FilledButton(
                onPressed: _filterBusy ? null : _saveFilterSettings,
                child: _filterBusy
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(_text('commonSave', 'Save')),
              ),
            ),
          ],
        );
      },
    );
  }

  String _filterLockStatusText(
    String key,
    String fallback,
    Map<String, dynamic> status,
  ) {
    final text = _text(key, fallback);
    if (key == 'filterLockPasswordRequirements') {
      return text
          .replaceAll('{0}', '${status['minimumPasswordLength'] ?? 6}')
          .replaceAll('{1}', '${status['unlockWindowMinutes'] ?? 10}');
    }
    return text.replaceAll('{0}', '${status['unlockWindowMinutes'] ?? 10}');
  }

  Future<void> _editFilterLockPassword(
    String action,
    Map<String, dynamic> status,
  ) async {
    final currentPasswordController = TextEditingController();
    final newPasswordController = TextEditingController();
    final confirmPasswordController = TextEditingController();
    final needsCurrent =
        action == 'unlock' ||
        action == 'changePassword' ||
        action == 'clearPassword';
    final needsNew = action == 'setPassword' || action == 'changePassword';
    final titleKey = switch (action) {
      'unlock' => 'filterLockUnlockTitle',
      'setPassword' => 'filterLockSetPasswordTitle',
      'changePassword' => 'filterLockChangePasswordTitle',
      _ => 'filterLockDisablePasswordTitle',
    };
    final result = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(_text(titleKey, _text('configFilters', 'Parental Lock'))),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                action == 'clearPassword'
                    ? _text('filterLockDisablePasswordWarning', '')
                    : _filterLockStatusText(
                        'filterLockPasswordRequirements',
                        'Use at least {0} characters. Unlock stays active for {1} minutes.',
                        status,
                      ),
              ),
              if (action == 'clearPassword')
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_text('filterLockDisablePasswordPrompt', '')),
                ),
              if (needsCurrent)
                TextField(
                  controller: currentPasswordController,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: _text(
                      action == 'unlock'
                          ? 'filterLockPasswordPrompt'
                          : 'filterLockCurrentPasswordPrompt',
                      'Current password',
                    ),
                  ),
                ),
              if (needsNew)
                TextField(
                  controller: newPasswordController,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: _text(
                      'filterLockNewPasswordPrompt',
                      'New password',
                    ),
                  ),
                ),
              if (needsNew)
                TextField(
                  controller: confirmPasswordController,
                  obscureText: true,
                  decoration: InputDecoration(
                    labelText: _text(
                      'filterLockConfirmPasswordPrompt',
                      'Confirm new password',
                    ),
                  ),
                ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(_text('autoCancel', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(_text('commonConfirm', 'Confirm')),
          ),
        ],
      ),
    );
    if (result != true) {
      currentPasswordController.dispose();
      newPasswordController.dispose();
      confirmPasswordController.dispose();
      return;
    }
    final currentPassword = currentPasswordController.text;
    final newPassword = newPasswordController.text;
    final confirmation = confirmPasswordController.text;
    currentPasswordController.dispose();
    newPasswordController.dispose();
    confirmPasswordController.dispose();
    if (needsNew && newPassword != confirmation) {
      _showMessage(
        _text('filterLockPasswordConfirmMismatch', 'Passwords do not match'),
      );
      return;
    }
    await _applyFilterLockAction(
      action,
      currentPassword: needsCurrent ? currentPassword : null,
      newPassword: needsNew ? newPassword : null,
    );
  }

  Future<void> _applyFilterLockAction(
    String action, {
    String? currentPassword,
    String? newPassword,
  }) async {
    setState(() => _filterBusy = true);
    try {
      final status = await widget.api.filterLockAction(
        action: action,
        currentPassword: currentPassword,
        newPassword: newPassword,
      );
      if (!mounted) return;
      setState(() {
        _filterLockStatus = Future.value(status);
        _filterSettingsInitialized = false;
        if (status['unlocked'] as bool? ?? false) {
          _filterSettings = widget.api.filterLockFilters();
        } else {
          _filterCategoriesController.clear();
          _filterChannelsController.clear();
        }
      });
      final successKey = switch (action) {
        'setPassword' => 'filterLockPasswordSetSuccess',
        'changePassword' => 'filterLockPasswordChangeSuccess',
        'clearPassword' => 'filterLockPasswordDisableSuccess',
        _ => null,
      };
      if (successKey != null) {
        _showMessage(_text(successKey, 'Parental lock settings updated'));
      }
    } catch (error) {
      if (mounted) {
        _showMessage(
          error is UiptvApiException ? error.message : error.toString(),
        );
      }
    } finally {
      if (mounted) setState(() => _filterBusy = false);
    }
  }

  Future<void> _refreshFilterLockStatus() async {
    try {
      final status = await widget.api.filterLockStatus();
      if (!mounted) return;
      setState(() {
        _filterLockStatus = Future.value(status);
        if (!(status['unlocked'] as bool? ?? false)) {
          _filterSettingsInitialized = false;
          _filterCategoriesController.clear();
          _filterChannelsController.clear();
        }
      });
    } catch (_) {
      return;
    }
  }

  Future<void> _saveFilterSettings() async {
    setState(() => _filterBusy = true);
    try {
      final filters = await widget.api.updateFilterLockFilters(
        categories: _filterCategoriesController.text,
        channels: _filterChannelsController.text,
        restrictionsActive: _filterRestrictionsActive,
      );
      if (!mounted) return;
      setState(() {
        _filterSettings = Future.value(filters);
        _filterRestrictionsActive =
            filters['restrictionsActive'] as bool? ?? true;
        _filterSettingsInitialized = true;
      });
      _showMessage(_text('commonSave', 'Saved'));
    } catch (error) {
      if (mounted) {
        _showMessage(
          error is UiptvApiException ? error.message : error.toString(),
        );
      }
    } finally {
      if (mounted) setState(() => _filterBusy = false);
    }
  }

  Widget _themeCard() {
    final zoomOptions =
        (_settings['uiZoomOptions'] as List<dynamic>? ?? const [])
            .whereType<num>()
            .map((value) => value.toInt())
            .toList(growable: false);
    final selectedZoom = (_settings['uiZoomPercent'] as num?)?.toInt() ?? 100;
    final zoomValues = zoomOptions.contains(selectedZoom)
        ? zoomOptions
        : [selectedZoom, ...zoomOptions];
    final languageOptions =
        (_settings['languageOptions'] as List<dynamic>? ?? const [])
            .whereType<Map>()
            .map((value) => Map<String, dynamic>.from(value))
            .toList(growable: false);
    final currentLanguage = _settings['languageLocale'] as String? ?? '';
    if (!languageOptions.any(
      (language) => language['tag'] == currentLanguage,
    )) {
      languageOptions.insert(0, {
        'tag': currentLanguage,
        'name': currentLanguage,
      });
    }

    return _card('configDarkTheme', 'Theme', [
      _anchor(
        'theme-appearance',
        Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(_text('configDarkTheme', 'Theme')),
            const SizedBox(height: 6),
            SegmentedButton<String>(
              segments: [
                ButtonSegment(
                  value: 'light',
                  label: Text(_text('commonLight', 'Light')),
                ),
                ButtonSegment(
                  value: 'dark',
                  label: Text(_text('commonDark', 'Dark')),
                ),
                ButtonSegment(
                  value: 'system',
                  label: Text(_text('commonSystem', 'System')),
                ),
              ],
              selected: {_settings['themeMode'] as String? ?? 'system'},
              onSelectionChanged: (selection) {
                setState(() => _settings['themeMode'] = selection.first);
              },
            ),
          ],
        ),
      ),
      const SizedBox(height: 10),
      _anchor(
        'theme-language',
        DropdownButtonFormField<String>(
          initialValue: currentLanguage.isEmpty ? null : currentLanguage,
          decoration: InputDecoration(
            labelText: _text('configLanguage', 'Language'),
          ),
          items: [
            for (final language in languageOptions)
              DropdownMenuItem(
                value: language['tag'] as String,
                child: Text(language['name'] as String),
              ),
          ],
          onChanged: (value) {
            if (value != null) {
              setState(() => _settings['languageLocale'] = value);
            }
          },
        ),
      ),
      const SizedBox(height: 10),
      _anchor(
        'theme-display',
        DropdownButtonFormField<int>(
          initialValue: zoomValues.contains(selectedZoom)
              ? selectedZoom
              : zoomValues.first,
          decoration: InputDecoration(
            labelText: _text('configThemeZoom', 'Zoom'),
          ),
          items: [
            for (final zoom in zoomValues)
              DropdownMenuItem(value: zoom, child: Text('$zoom%')),
          ],
          onChanged: (value) {
            if (value != null) {
              setState(() => _settings['uiZoomPercent'] = value);
            }
          },
        ),
      ),
      _anchor(
        'theme-catalog',
        Column(
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(_text('configPlainTextMode', 'Plain text mode')),
              value: !(_settings['enableThumbnails'] as bool? ?? true),
              onChanged: (value) =>
                  setState(() => _settings['enableThumbnails'] = !value),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                _text(
                  'configShowBookmarkAndAccountCounts',
                  'Show Favourites and account counts',
                ),
              ),
              value:
                  _settings['showBookmarkAndAccountCounts'] as bool? ?? false,
              onChanged: (value) => setState(() {
                _settings['showBookmarkAndAccountCounts'] = value;
              }),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(_text('configEnableEpg', 'Enable EPG')),
              value: _settings['epgEnabled'] as bool? ?? false,
              onChanged: (value) =>
                  setState(() => _settings['epgEnabled'] = value),
            ),
          ],
        ),
      ),
      _anchor(
        'theme-cache',
        TextField(
          controller: _cacheExpiryController,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(
            labelText: _text(
              'configCacheExpiresInDays',
              'Cache Expires in days',
            ),
            hintText: _text('configCacheExpiryPrompt', ''),
          ),
        ),
      ),
      _saveActions(),
    ]);
  }

  Widget _backupCard() {
    return _card('configDatabaseSyncTitle', 'Backup & Restore', [
      Text(
        _text(
          'configDatabaseSyncDescription',
          'Create or restore full UIPTV backups.',
        ),
      ),
      const SizedBox(height: 12),
      if (_backupBusy) const LinearProgressIndicator(),
      _anchor(
        'backup-create',
        OutlinedButton.icon(
          onPressed: _backupBusy ? null : _createBackup,
          icon: const Icon(Icons.upload_file),
          label: Text(_text('configExportDatabase', 'Create Backup')),
        ),
      ),
      _anchor(
        'backup-restore',
        OutlinedButton.icon(
          onPressed: _backupBusy ? null : _restoreBackup,
          icon: const Icon(Icons.restore),
          label: Text(_text('configImportDatabase', 'Restore Backup')),
        ),
      ),
    ]);
  }

  Future<void> _createBackup() async {
    final suggestedName =
        'uiptv-backup-${DateTime.now().millisecondsSinceEpoch}.zip';
    final location = await getSaveLocation(
      suggestedName: suggestedName,
      acceptedTypeGroups: const [
        XTypeGroup(label: 'UIPTV backup archive', extensions: ['zip']),
      ],
    );
    if (location == null) return;
    setState(() => _backupBusy = true);
    try {
      final archive = await widget.api.createBackupArchive();
      final selectedPath = location.path.toLowerCase().endsWith('.zip')
          ? location.path
          : '${location.path}.zip';
      await XFile.fromData(
        archive,
        mimeType: 'application/zip',
        name: suggestedName,
      ).saveTo(selectedPath);
      if (mounted) {
        _showMessage(_text('configExportDatabaseSuccess', 'Backup created.'));
      }
    } catch (error) {
      if (mounted) {
        _showMessage(
          _text('configExportDatabaseFailed', 'Backup creation failed.'),
        );
      }
    } finally {
      if (mounted) setState(() => _backupBusy = false);
    }
  }

  Future<void> _restoreBackup() async {
    final file = await openFile(
      acceptedTypeGroups: const [
        XTypeGroup(label: 'UIPTV backup archive', extensions: ['zip']),
      ],
    );
    if (file == null || !mounted) return;
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_text('commonConfirm', 'Confirm')),
        content: Text(
          _text('configRestoreBackupConfirm', 'Restore this backup?'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(_text('autoCancel', 'Cancel')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(_text('configImportDatabase', 'Restore Backup')),
          ),
        ],
      ),
    );
    if (confirmed != true) return;
    setState(() => _backupBusy = true);
    try {
      final report = await widget.api.restoreBackupArchive(
        await file.readAsBytes(),
      );
      if (!mounted) return;
      await _restoreSettings();
      if (!mounted) return;
      widget.onRestored?.call();
      _showMessage(
        _format(
          _text('configDatabaseRestoreSummary', 'Restored backup: {0} ({1})'),
          [file.name, _sizeLabel(report['databaseBytes'] as int? ?? 0)],
        ),
      );
    } catch (error) {
      if (mounted) {
        _showMessage(
          _text('configImportDatabaseFailed', 'Backup restore failed.'),
        );
      }
    } finally {
      if (mounted) setState(() => _backupBusy = false);
    }
  }

  String _sizeLabel(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${bytes ~/ 1024} KB';
    return '${bytes ~/ (1024 * 1024)} MB';
  }

  String _format(String template, List<String> values) {
    var result = template;
    for (var index = 0; index < values.length; index++) {
      result = result.replaceAll('{$index}', values[index]);
    }
    return result;
  }

  Widget _serverCard() {
    return _card('configWebServer', 'Internal Web Server', [
      _anchor(
        'server-ports',
        Column(
          children: [
            TextField(
              controller: _httpPortController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: 'HTTP',
                hintText: _text('configServerPortPrompt', ''),
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _httpsPortController,
              keyboardType: TextInputType.number,
              decoration: InputDecoration(
                labelText: 'HTTPS',
                hintText: _text('configHttpsServerPortPrompt', ''),
              ),
            ),
          ],
        ),
      ),
      _anchor(
        'server-startup',
        Column(
          children: [
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                _text(
                  'configAutoRunServerOnStartup',
                  'Automatically run server on startup',
                ),
              ),
              value: _settings['autoRunServerOnStartup'] as bool? ?? false,
              onChanged: (value) => setState(() {
                _settings['autoRunServerOnStartup'] = value;
              }),
            ),
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: Text(
                _text('configEnableHttpsServer', 'Enable HTTPS server'),
              ),
              value: _settings['httpsServerEnabled'] as bool? ?? false,
              onChanged: (value) => setState(() {
                _settings['httpsServerEnabled'] = value;
              }),
            ),
          ],
        ),
      ),
      _anchor(
        'server-proxy',
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          title: Text(
            _text(
              'configWebPlaybackProxyEnabled',
              'Enable browser stream proxy',
            ),
          ),
          value: _settings['webPlaybackProxyEnabled'] as bool? ?? false,
          onChanged: (value) => setState(() {
            _settings['webPlaybackProxyEnabled'] = value;
          }),
        ),
      ),
      _anchor(
        'server-status',
        FutureBuilder<Map<String, dynamic>>(
          future: _serverStatus,
          builder: (context, snapshot) {
            if (snapshot.hasError) return Text(snapshot.error.toString());
            final status = snapshot.data ?? const {};
            final running = status['running'] as bool? ?? false;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if ((status['httpUrl'] as String? ?? '').isNotEmpty)
                  SelectableText(status['httpUrl'] as String),
                if ((status['httpsUrl'] as String? ?? '').isNotEmpty)
                  SelectableText(status['httpsUrl'] as String),
                FilledButton(
                  onPressed: _serverBusy ? null : () => _toggleServer(!running),
                  child: Text(
                    _text(
                      running ? 'configStopServer' : 'configStartServer',
                      running ? 'Stop Server' : 'Start Server',
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
      _saveActions(),
    ]);
  }

  Widget _tmdbCard() {
    final tokenConfigured = _settings['tmdbTokenConfigured'] as bool? ?? false;
    return _card('configTmdbMetadata', 'TMDB Metadata', [
      _anchor(
        'tmdb-credentials',
        TextField(
          controller: _tmdbTokenController,
          obscureText: true,
          decoration: InputDecoration(
            labelText: _text(
              'configTmdbReadAccessToken',
              'TMDB Read Access Token',
            ),
            hintText: tokenConfigured
                ? '••••••••••••••'
                : _text('configTmdbReadAccessTokenPrompt', ''),
          ),
        ),
      ),
      const SizedBox(height: 8),
      _anchor(
        'tmdb-links',
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextButton(
              onPressed: () =>
                  _openTmdbUrl('https://developer.themoviedb.org/docs'),
              child: Text(_text('configTmdbApiGuideLink', 'TMDB API Guide')),
            ),
            TextButton(
              onPressed: () =>
                  _openTmdbUrl('https://www.themoviedb.org/settings/api'),
              child: Text(
                _text('configTmdbApiKeyPageLink', 'Get TMDB API Key or Token'),
              ),
            ),
          ],
        ),
      ),
      _saveActions(),
    ]);
  }

  Widget _card(String titleKey, String fallback, List<Widget> children) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              _text(titleKey, fallback),
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 10),
            ...children,
          ],
        ),
      ),
    );
  }

  Widget _saveActions() {
    return Align(
      alignment: Alignment.centerRight,
      child: Wrap(
        spacing: 8,
        children: [
          OutlinedButton(
            onPressed: _saving ? null : _restoreSettings,
            child: Text(_text('autoRestore', 'Restore')),
          ),
          FilledButton(
            onPressed: _saving ? null : () => _saveSettings(),
            child: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(_text('commonSave', 'Save')),
          ),
        ],
      ),
    );
  }

  Map<String, dynamic> _settingsPayload() => {
    'playerPath1': _player1Controller.text,
    'playerPath2': _player2Controller.text,
    'playerPath3': _player3Controller.text,
    'defaultPlayerPath': switch (_selectedDefaultPlayer) {
      'player1' => _player1Controller.text,
      'player2' => _player2Controller.text,
      'player3' => _player3Controller.text,
      'browser' =>
        _settings['webBrowserPlayerPath'] as String? ??
            '__web_browser_player__',
      _ => _settings['embeddedPlayerPath'] as String? ?? '__embedded_player__',
    },
    'embeddedPlayer': _selectedDefaultPlayer == 'embedded',
    'wideView': _settings['wideView'] as bool? ?? false,
    'resolveChainAndDeepRedirects':
        _settings['resolveChainAndDeepRedirects'] as bool? ?? false,
    'enableThumbnails': _settings['enableThumbnails'] as bool? ?? true,
    'showBookmarkAndAccountCounts':
        _settings['showBookmarkAndAccountCounts'] as bool? ?? false,
    'epgEnabled': _settings['epgEnabled'] as bool? ?? false,
    'themeMode': _settings['themeMode'] as String? ?? 'system',
    'languageLocale': _settings['languageLocale'] as String? ?? 'en-US',
    'uiZoomPercent': '${_settings['uiZoomPercent'] ?? 100}',
    'cacheExpiryDays': _cacheExpiryController.text.trim(),
    'serverPort': _httpPortController.text.trim(),
    'httpsServerPort': _httpsPortController.text.trim(),
    'httpsServerEnabled': _settings['httpsServerEnabled'] as bool? ?? false,
    'autoRunServerOnStartup':
        _settings['autoRunServerOnStartup'] as bool? ?? false,
    'webPlaybackProxyEnabled':
        _settings['webPlaybackProxyEnabled'] as bool? ?? false,
    if (_tmdbTokenController.text.isNotEmpty)
      'tmdbReadAccessToken': _tmdbTokenController.text,
  };

  Future<Map<String, dynamic>?> _saveSettings({
    bool showFeedback = true,
  }) async {
    setState(() => _saving = true);
    try {
      final saved = await widget.api.updateSettings(_settingsPayload());
      final labels = await widget.api.translations(_translationKeys);
      if (!mounted) return saved;
      setState(() {
        _settings = saved;
        _labels = labels;
        _tmdbTokenController.clear();
        if (saved['serverPort'] != null) {
          _httpPortController.text = saved['serverPort'] as String;
        }
        if (saved['httpsServerPort'] != null) {
          _httpsPortController.text = saved['httpsServerPort'] as String;
        }
        _cacheExpiryController.text = '${saved['cacheExpiryDays'] ?? 30}';
        _serverStatus = widget.api.internalServerStatus();
      });
      widget.onSettingsSaved?.call(saved);
      if (showFeedback) _showMessage(_text('commonSave', 'Saved'));
      return saved;
    } catch (error) {
      if (mounted) _showMessage(error.toString());
      return null;
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Future<void> _restoreSettings() async {
    try {
      final settings = await widget.api.settings();
      final labels = await widget.api.translations(_translationKeys);
      if (!mounted) return;
      setState(() {
        _hydrate(settings);
        _labels = labels;
        _serverStatus = widget.api.internalServerStatus();
        _filterLockStatus = widget.api.filterLockStatus();
        _filterSettings = widget.api.filterLockFilters();
        _filterSettingsInitialized = false;
      });
      widget.onSettingsSaved?.call(settings);
    } catch (error) {
      if (mounted) _showMessage(error.toString());
    }
  }

  Future<void> _toggleServer(bool running) async {
    setState(() => _serverBusy = true);
    try {
      final saved = await _saveSettings(showFeedback: false);
      if (saved == null) return;
      final status = await widget.api.setInternalServerRunning(running);
      if (!mounted) return;
      setState(() => _serverStatus = Future.value(status));
    } catch (error) {
      if (mounted) _showMessage(error.toString());
    } finally {
      if (mounted) setState(() => _serverBusy = false);
    }
  }

  Future<void> _openTmdbUrl(String value) async {
    final uri = Uri.tryParse(value);
    if (uri == null ||
        uri.scheme != 'https' ||
        !{
          'developer.themoviedb.org',
          'www.themoviedb.org',
        }.contains(uri.host)) {
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
    } catch (_) {
      return;
    }
  }

  String _text(String key, String fallback) => _labels[key] ?? fallback;

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }
}

class _SettingsData {
  const _SettingsData({required this.settings, required this.labels});

  final Map<String, dynamic> settings;
  final Map<String, String> labels;
}

class _SettingsSubsectionConnectorPainter extends CustomPainter {
  const _SettingsSubsectionConnectorPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = color
      ..strokeWidth = 1.2
      ..style = PaintingStyle.stroke;
    final centerY = size.height / 2;
    canvas.drawLine(Offset(3, 0), Offset(3, size.height), paint);
    canvas.drawLine(Offset(3, centerY), Offset(size.width, centerY), paint);
    canvas.drawCircle(
      Offset(3, centerY),
      2.2,
      Paint()
        ..color = color
        ..style = PaintingStyle.fill,
    );
  }

  @override
  bool shouldRepaint(_SettingsSubsectionConnectorPainter oldDelegate) =>
      oldDelegate.color != color;
}
