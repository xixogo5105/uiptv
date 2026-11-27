// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:media_kit_video/media_kit_video.dart';

import '../api/uiptv_api_client.dart';
import '../player/uiptv_player.dart';
import 'bulk_account_import_screen.dart';
import 'desktop_cache_reload_screen.dart';
import 'desktop_about_dialog.dart';
import 'desktop_logs_screen.dart';
import 'desktop_settings_screen.dart';

enum _DesktopSection {
  favourites,
  accounts,
  watchingNow,
  settings,
  import,
  logs,
  cacheReload,
}

enum _DesktopMenuAction { settings, import, logs, cacheReload, about }

class UiptvDesktopShell extends StatefulWidget {
  const UiptvDesktopShell({
    super.key,
    required this.api,
    this.initialStreamUrl = '',
  });

  final UiptvApiClient api;
  final String initialStreamUrl;

  @override
  State<UiptvDesktopShell> createState() => _UiptvDesktopShellState();
}

class _UiptvDesktopShellState extends State<UiptvDesktopShell> {
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
  Future<List<UiptvCategorySummary>>? _categories;
  Future<UiptvChannelPage>? _channels;
  final List<UiptvChannelSummary> _additionalChannels = [];
  final Map<String, Future<List<UiptvWatchingNowEpisode>>> _watchingEpisodes =
      {};
  final Map<String, String> _watchingSeasonSelections = {};
  UiptvChannelPage? _lastChannelPage;
  String? _expandedWatchingEntryId;
  bool _loadingMoreChannels = false;
  bool _loadingMoreWatchingNow = false;
  bool _showPlayer = false;
  bool _playerExpanded = false;
  bool _playerFullscreen = false;
  double _volume = 80;
  bool _playerHovered = false;
  String _selectedMode = 'itv';
  String _favouriteCategory = _recentBookmarkCategoryId;
  Map<String, String> _bookmarkTranslations = const {};
  List<String> _cacheReloadInitialAccountIds = const [];

  void _refreshPlayerStatus() {
    if (!mounted) {
      return;
    }
    setState(() {});
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
      _watchingEpisodes.clear();
      _watchingSeasonSelections.clear();
      _expandedWatchingEntryId = null;
    });
  }

  void _showAboutDialog() {
    showDialog<void>(
      context: context,
      builder: (context) => DesktopAboutDialog(api: widget.api),
    );
  }

  @override
  void initState() {
    super.initState();
    _player = UiptvPlayer(onStateChanged: _refreshPlayerStatus);
    _accounts = widget.api.accounts();
    _bookmarkCategories = widget.api.bookmarkCategories();
    _bookmarks = _loadFavouriteItems(_favouriteCategory);
    _watchingNow = widget.api.watchingNow();
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
    _player.dispose();
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
          'commonSearch',
          'configClearRecent',
          'configRecentClearConfirm',
          'configRecentCleared',
          'configRecentClearFailed',
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
    return Scaffold(
      appBar: _playerFullscreen
          ? null
          : AppBar(
              toolbarHeight: 54,
              titleSpacing: 14,
              title: Row(
                children: [
                  const Icon(Icons.live_tv, size: 23),
                  const SizedBox(width: 8),
                  const Text('UIPTV', style: TextStyle(fontSize: 16)),
                  const SizedBox(width: 24),
                  _navigationButton(
                    _DesktopSection.favourites,
                    Icons.favorite,
                    'Favourites',
                  ),
                  _navigationButton(
                    _DesktopSection.accounts,
                    Icons.person,
                    'Accounts',
                  ),
                  _navigationButton(
                    _DesktopSection.watchingNow,
                    Icons.play_arrow,
                    'Watching',
                  ),
                ],
              ),
              actions: [
                IconButton(
                  tooltip: _showPlayer ? 'Hide player' : 'Show player',
                  icon: Icon(
                    _showPlayer
                        ? Icons.picture_in_picture_alt
                        : Icons.picture_in_picture_alt_outlined,
                  ),
                  onPressed: () => setState(() => _showPlayer = !_showPlayer),
                ),
                PopupMenuButton<_DesktopMenuAction>(
                  tooltip: 'More',
                  icon: const Icon(Icons.settings_outlined),
                  onSelected: (action) {
                    switch (action) {
                      case _DesktopMenuAction.settings:
                        setState(() => _section = _DesktopSection.settings);
                      case _DesktopMenuAction.import:
                        setState(() => _section = _DesktopSection.import);
                      case _DesktopMenuAction.logs:
                        setState(() => _section = _DesktopSection.logs);
                      case _DesktopMenuAction.cacheReload:
                        setState(() {
                          _cacheReloadInitialAccountIds = const [];
                          _section = _DesktopSection.cacheReload;
                        });
                      case _DesktopMenuAction.about:
                        _showAboutDialog();
                    }
                  },
                  itemBuilder: (context) => [
                    PopupMenuItem(
                      value: _DesktopMenuAction.settings,
                      child: _menuEntry(Icons.settings_outlined, 'Settings'),
                    ),
                    PopupMenuItem(
                      value: _DesktopMenuAction.import,
                      child: _menuEntry(Icons.file_download_outlined, 'Import'),
                    ),
                    PopupMenuItem(
                      value: _DesktopMenuAction.logs,
                      child: _menuEntry(Icons.article_outlined, 'Logs'),
                    ),
                    PopupMenuItem(
                      value: _DesktopMenuAction.cacheReload,
                      child: _menuEntry(
                        Icons.sync,
                        _bookmarkText(
                          'autoReloadAccountsCache',
                          'Reload Accounts Cache',
                        ),
                      ),
                    ),
                    PopupMenuItem(
                      value: _DesktopMenuAction.about,
                      child: _menuEntry(Icons.info_outline, 'About'),
                    ),
                  ],
                ),
                const SizedBox(width: 10),
              ],
            ),
      body: LayoutBuilder(
        builder: (context, constraints) {
          final pipWidth = constraints.maxWidth < 760
              ? constraints.maxWidth - 28
              : _playerExpanded
              ? constraints.maxWidth - 36
              : 420.0;
          final pipHeight = _playerExpanded
              ? constraints.maxHeight - 36
              : pipWidth * 9 / 16 + 50;
          return Stack(
            children: [
              if (!_playerFullscreen)
                Padding(
                  padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
                  child: _outlinedPanel(_buildSection()),
                ),
              if (_showPlayer)
                _playerFullscreen
                    ? Positioned.fill(child: _buildPlayerPanel())
                    : Positioned(
                        right: 18,
                        bottom: 18,
                        width: pipWidth,
                        height: pipHeight,
                        child: _buildPlayerPanel(),
                      ),
            ],
          );
        },
      ),
    );
  }

  Widget _navigationButton(
    _DesktopSection section,
    IconData icon,
    String label,
  ) {
    final selected = _section == section;
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: Material(
        color: selected ? const Color(0xFFEAF1FF) : Colors.transparent,
        borderRadius: BorderRadius.circular(20),
        child: InkWell(
          borderRadius: BorderRadius.circular(20),
          onTap: () => setState(() => _section = section),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: selected ? const Color(0xFFB9CCFF) : Colors.transparent,
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 18),
                const SizedBox(width: 7),
                Text(label, style: const TextStyle(fontSize: 13)),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _outlinedPanel(Widget child) {
    return Material(
      clipBehavior: Clip.antiAlias,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        side: const BorderSide(color: Color(0xFFE1E6EB)),
        borderRadius: BorderRadius.circular(12),
      ),
      child: child,
    );
  }

  Widget _menuEntry(IconData icon, String label) {
    return Row(children: [Icon(icon), const SizedBox(width: 12), Text(label)]);
  }

  Widget _buildPlayerPanel() {
    final playerStatus =
        _player.error ??
        '${_player.buffering
                ? 'Buffering'
                : _player.playing
                ? 'Playing'
                : 'Stopped'}'
            '${_player.width != null && _player.height != null ? ' • ${_player.width}x${_player.height}' : ''}';
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

    return Material(
      clipBehavior: Clip.antiAlias,
      elevation: 12,
      shadowColor: Colors.black45,
      borderRadius: BorderRadius.circular(12),
      child: _outlinedPanel(
        GestureDetector(
          onDoubleTap: () => setState(() {
            _playerFullscreen = !_playerFullscreen;
            _playerExpanded = false;
          }),
          child: MouseRegion(
            onEnter: (_) => setState(() => _playerHovered = true),
            onExit: (_) => setState(() => _playerHovered = false),
            child: Stack(
              children: [
                Positioned.fill(
                  child: ColoredBox(
                    color: Colors.black,
                    child: Video(
                      controller: _player.controller,
                      controls: NoVideoControls,
                    ),
                  ),
                ),
                Positioned(
                  left: 0,
                  right: 0,
                  bottom: 0,
                  child: AnimatedOpacity(
                    opacity: _playerHovered ? 1 : 0,
                    duration: const Duration(milliseconds: 160),
                    child: IgnorePointer(
                      ignoring: !_playerHovered,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: const BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.topCenter,
                            end: Alignment.bottomCenter,
                            colors: [Colors.transparent, Color(0xE6000000)],
                          ),
                        ),
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Row(
                              children: [
                                IconButton(
                                  tooltip: _player.playing ? 'Pause' : 'Play',
                                  onPressed: _player.playing
                                      ? _player.pause
                                      : _player.play,
                                  color: Colors.white,
                                  icon: Icon(
                                    _player.playing
                                        ? Icons.pause
                                        : Icons.play_arrow,
                                  ),
                                ),
                                IconButton(
                                  tooltip: 'Stop',
                                  onPressed: _player.stop,
                                  color: Colors.white,
                                  icon: const Icon(Icons.stop),
                                ),
                                Text(
                                  clock(_player.position),
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 11,
                                  ),
                                ),
                                Expanded(
                                  child: Slider(
                                    value: currentMs.toDouble(),
                                    min: 0,
                                    max: totalMs > 0 ? totalMs.toDouble() : 1,
                                    onChanged: totalMs <= 0
                                        ? null
                                        : (v) => _player.seek(
                                            Duration(milliseconds: v.round()),
                                          ),
                                  ),
                                ),
                                Text(
                                  clock(_player.duration),
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 11,
                                  ),
                                ),
                                IconButton(
                                  tooltip: _volume == 0 ? 'Unmute' : 'Mute',
                                  icon: Icon(
                                    _volume == 0
                                        ? Icons.volume_off
                                        : Icons.volume_up,
                                  ),
                                  color: Colors.white,
                                  onPressed: () {
                                    final value = _volume == 0 ? 80.0 : 0.0;
                                    setState(() => _volume = value);
                                    _player.setVolume(value);
                                  },
                                ),
                                SizedBox(
                                  width: 76,
                                  child: Slider(
                                    value: _volume,
                                    min: 0,
                                    max: 100,
                                    onChanged: (v) {
                                      setState(() => _volume = v);
                                      _player.setVolume(v);
                                    },
                                  ),
                                ),
                                IconButton(
                                  tooltip: _playerExpanded
                                      ? 'Picture in picture'
                                      : 'Expand',
                                  onPressed: () => setState(
                                    () => _playerExpanded = !_playerExpanded,
                                  ),
                                  color: Colors.white,
                                  icon: Icon(
                                    _playerExpanded
                                        ? Icons.picture_in_picture_alt
                                        : Icons.open_in_full,
                                  ),
                                ),
                                IconButton(
                                  tooltip: 'Close',
                                  onPressed: () =>
                                      setState(() => _showPlayer = false),
                                  color: Colors.white,
                                  icon: const Icon(Icons.close),
                                ),
                              ],
                            ),
                            Align(
                              alignment: Alignment.centerLeft,
                              child: Text(
                                playerStatus,
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                                style: const TextStyle(
                                  color: Colors.white70,
                                  fontSize: 11,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
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

  Widget _buildSection() {
    switch (_section) {
      case _DesktopSection.accounts:
        return _buildAccountBrowser();
      case _DesktopSection.settings:
        return DesktopSettingsScreen(
          api: widget.api,
          onRestored: _refreshAfterDatabaseRestore,
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
                        child: SizedBox(
                          height: 50,
                          child: ListView.separated(
                            scrollDirection: Axis.horizontal,
                            itemCount: categoryOptions.length,
                            separatorBuilder: (_, _) =>
                                const SizedBox(width: 6),
                            itemBuilder: (context, index) {
                              final category = categoryOptions[index];
                              return ChoiceChip(
                                label: Text(category.name),
                                selected: _favouriteCategory == category.id,
                                onSelected: (_) => setState(() {
                                  _favouriteCategory = category.id;
                                  _bookmarks = _loadFavouriteItems(category.id);
                                }),
                              );
                            },
                          ),
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
                      return GridView.builder(
                        padding: const EdgeInsets.all(14),
                        gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                          maxCrossAxisExtent:
                              MediaQuery.sizeOf(context).width < 700
                              ? 360
                              : 340,
                          mainAxisExtent: 108,
                          crossAxisSpacing: 12,
                          mainAxisSpacing: 12,
                        ),
                        itemCount: visibleBookmarks.length,
                        itemBuilder: (context, index) {
                          final bookmark = visibleBookmarks[index];
                          return Material(
                            color: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                              side: const BorderSide(color: Color(0xFFE1E6EB)),
                            ),
                            child: InkWell(
                              borderRadius: BorderRadius.circular(16),
                              onTap: () => _playBookmark(bookmark.dbId),
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
                                              color: Color(0xFF52616E),
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
            final entries = snapshot.data?.entries ?? const [];
            if (entries.isEmpty) {
              return const Center(child: Text('Nothing watching now'));
            }
            return NotificationListener<ScrollNotification>(
              onNotification: (notification) {
                final current = snapshot.data;
                if (notification.metrics.extentAfter < 300 &&
                    current != null &&
                    (current.hasMoreSeries || current.hasMoreVod) &&
                    !_loadingMoreWatchingNow) {
                  _loadMoreWatchingNow(current);
                }
                return false;
              },
              child: ListView.builder(
                itemCount: entries.length + (_loadingMoreWatchingNow ? 1 : 0),
                itemBuilder: (context, index) {
                  if (index >= entries.length) {
                    return const Center(
                      child: Padding(
                        padding: EdgeInsets.all(12),
                        child: CircularProgressIndicator(),
                      ),
                    );
                  }
                  final entry = entries[index];
                  final expanded =
                      entry.isSeries &&
                      _expandedWatchingEntryId == entry.playbackId;
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      ListTile(
                        leading: entry.image.isEmpty
                            ? Icon(
                                entry.isSeries
                                    ? Icons.tv
                                    : Icons.movie_outlined,
                              )
                            : Image.network(
                                entry.image,
                                width: 40,
                                height: 56,
                                fit: BoxFit.cover,
                                errorBuilder: (_, _, _) => Icon(
                                  entry.isSeries
                                      ? Icons.tv
                                      : Icons.movie_outlined,
                                ),
                              ),
                        title: Text(entry.title),
                        subtitle: Text(
                          [
                            entry.accountName,
                            entry.details,
                          ].where((value) => value.isNotEmpty).join(' · '),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        onTap: entry.playbackId.isEmpty
                            ? null
                            : () => _playWatchingNow(entry),
                        trailing: entry.isSeries && entry.playbackId.isNotEmpty
                            ? IconButton(
                                icon: Icon(
                                  expanded
                                      ? Icons.expand_less
                                      : Icons.expand_more,
                                ),
                                onPressed: () => _toggleWatchingEpisodes(entry),
                              )
                            : null,
                      ),
                      if (expanded) _buildWatchingEpisodes(entry),
                    ],
                  );
                },
              ),
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
    final selectedAccount = _selectedAccount;
    final selectedCategory = _selectedCategory;
    if (selectedAccount != null && selectedCategory != null) {
      return Column(
        children: [
          ListTile(
            leading: IconButton(
              tooltip: 'Back to categories',
              onPressed: () => setState(() {
                _selectedCategory = null;
                _channels = null;
                _catalogueSearchController.clear();
                _additionalChannels.clear();
                _lastChannelPage = null;
              }),
              icon: const Icon(Icons.arrow_back),
            ),
            title: Text(selectedCategory.title),
            subtitle: Text(selectedAccount.accountName),
          ),
          _accountModePills(selectedAccount),
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
                        decoration: const InputDecoration(
                          prefixIcon: Icon(Icons.search),
                          hintText: 'Search channels',
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
                          gridDelegate:
                              SliverGridDelegateWithMaxCrossAxisExtent(
                                maxCrossAxisExtent:
                                    MediaQuery.sizeOf(context).width < 700
                                    ? 360
                                    : 340,
                                mainAxisExtent: 108,
                                crossAxisSpacing: 12,
                                mainAxisSpacing: 12,
                              ),
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
            leading: IconButton(
              tooltip: 'Back to accounts',
              onPressed: () => setState(() {
                _selectedAccount = null;
                _categories = null;
              }),
              icon: const Icon(Icons.arrow_back),
            ),
            title: Text(selectedAccount.accountName),
            subtitle: Text(selectedAccount.type),
          ),
          _accountModePills(selectedAccount),
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
                if (categories.isEmpty) {
                  return Center(
                    child: Text('No ${_selectedMode.toUpperCase()} categories'),
                  );
                }
                return GridView.builder(
                  padding: const EdgeInsets.all(14),
                  gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
                    maxCrossAxisExtent: 300,
                    mainAxisExtent: 112,
                    crossAxisSpacing: 12,
                    mainAxisSpacing: 12,
                  ),
                  itemCount: categories.length,
                  itemBuilder: (context, index) {
                    final category = categories[index];
                    return _simpleCard(
                      icon: Icons.folder_outlined,
                      title: category.title,
                      subtitle: category.alias,
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

    return FutureBuilder<List<UiptvAccountSummary>>(
      future: _accounts,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return _error(snapshot.error);
        }
        final accounts = snapshot.data ?? const [];
        if (accounts.isEmpty) {
          return const Center(child: Text('No accounts'));
        }
        return GridView.builder(
          padding: const EdgeInsets.all(14),
          gridDelegate: SliverGridDelegateWithMaxCrossAxisExtent(
            maxCrossAxisExtent: 320,
            mainAxisExtent: 120,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
          ),
          itemCount: accounts.length,
          itemBuilder: (context, index) {
            final account = accounts[index];
            return _simpleCard(
              icon: Icons.live_tv_outlined,
              title: account.accountName,
              subtitle: account.type,
              trailing: account.pinToTop
                  ? const Icon(Icons.push_pin, size: 18)
                  : null,
              onTap: () => setState(() {
                _selectedAccount = account;
                _selectedMode = 'itv';
                _selectedCategory = null;
                _categories = widget.api.categories(
                  accountId: account.dbId,
                  mode: _selectedMode,
                );
              }),
            );
          },
        );
      },
    );
  }

  Widget _simpleCard({
    required IconData icon,
    required String title,
    String subtitle = '',
    Widget? trailing,
    required VoidCallback onTap,
  }) {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFFE1E6EB)),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 48,
                height: 48,
                decoration: BoxDecoration(
                  color: const Color(0xFFF2F4F6),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(icon, color: const Color(0xFF66717A)),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    if (subtitle.isNotEmpty) ...[
                      const SizedBox(height: 5),
                      Text(
                        subtitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: Color(0xFF68737D),
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              if (trailing != null)
                trailing
              else
                const Icon(Icons.chevron_right, color: Color(0xFF68737D)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _channelCard(
    UiptvChannelSummary channel,
    UiptvAccountSummary account,
    UiptvCategorySummary category,
  ) {
    return Material(
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFFE1E6EB)),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => _playChannel(
          account.dbId,
          category.dbId,
          channel.playbackId.isEmpty ? channel.dbId : channel.playbackId,
          mode: _selectedMode,
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
                      style: const TextStyle(
                        color: Color(0xFF52616E),
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ),
              IconButton(
                tooltip: 'Add to favourites',
                icon: const Icon(
                  Icons.favorite_border,
                  color: Color(0xFFE12D36),
                ),
                onPressed: channel.dbId.isEmpty
                    ? null
                    : () => _addLiveBookmark(
                        account.dbId,
                        category.dbId,
                        channel.dbId,
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _playChannel(
    String accountDbId,
    String categoryDbId,
    String channelDbId, {
    String mode = 'itv',
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
        setState(() => _showPlayer = true);
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

  Future<void> _playBookmark(String bookmarkDbId) async {
    try {
      final playback = await widget.api.resolveBookmarkPlayback(bookmarkDbId);
      final url = playback['url'] as String?;
      if (url == null || url.isEmpty) {
        throw UiptvApiException(
          422,
          'The selected favourite did not resolve to a stream',
        );
      }
      await _player.playUrl(url);
      if (mounted) {
        setState(() => _showPlayer = true);
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

  Widget _accountModePills(UiptvAccountSummary account) {
    final supportsVodAndSeries =
        account.type.toUpperCase().contains('STALKER') ||
        account.type.toUpperCase().contains('XTREME');
    final modes = supportsVodAndSeries
        ? const [('itv', 'Live'), ('vod', 'VOD'), ('series', 'Series')]
        : const [('itv', 'Live')];
    final selectedLabel = modes
        .firstWhere((mode) => mode.$1 == _selectedMode)
        .$2;
    return _pillBar(modes.map((mode) => mode.$2).toList(), selectedLabel, (
      label,
    ) {
      final mode = modes.firstWhere((option) => option.$2 == label).$1;
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
    });
  }

  Widget _pillBar(
    List<String> labels,
    String selected,
    ValueChanged<String> onSelected,
  ) {
    return SizedBox(
      height: 52,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(
          children: [
            for (final label in labels)
              Padding(
                padding: const EdgeInsets.only(right: 8),
                child: ChoiceChip(
                  label: Text(label),
                  selected: label == selected,
                  onSelected: (_) => onSelected(label),
                ),
              ),
          ],
        ),
      ),
    );
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
        setState(() => _showPlayer = true);
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

  void _toggleWatchingEpisodes(UiptvWatchingNowEntry entry) {
    setState(() {
      if (_expandedWatchingEntryId == entry.playbackId) {
        _expandedWatchingEntryId = null;
        return;
      }
      _expandedWatchingEntryId = entry.playbackId;
      _watchingEpisodes.putIfAbsent(
        entry.playbackId,
        () => widget.api.watchingNowEpisodes(entry.playbackId),
      );
    });
  }

  Widget _buildWatchingEpisodes(UiptvWatchingNowEntry entry) {
    final future = _watchingEpisodes[entry.playbackId];
    if (future == null) {
      return const SizedBox.shrink();
    }
    return FutureBuilder<List<UiptvWatchingNowEpisode>>(
      future: future,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (snapshot.hasError) {
          return _error(snapshot.error);
        }
        final episodes = snapshot.data ?? const [];
        String seasonOf(UiptvWatchingNowEpisode episode) =>
            episode.season.trim().isEmpty ? '1' : episode.season.trim();
        final seasons = episodes.map(seasonOf).toSet().toList()
          ..sort((first, second) {
            final firstNumber = int.tryParse(first);
            final secondNumber = int.tryParse(second);
            if (firstNumber != null && secondNumber != null) {
              return firstNumber.compareTo(secondNumber);
            }
            return first.compareTo(second);
          });
        var selectedSeason =
            _watchingSeasonSelections[entry.playbackId] ??
            (entry.season.trim().isEmpty ? '1' : entry.season.trim());
        if (!seasons.contains(selectedSeason)) {
          selectedSeason = seasons.isEmpty ? '' : seasons.first;
        }
        final visibleEpisodes = selectedSeason.isEmpty
            ? episodes
            : episodes
                  .where((episode) => seasonOf(episode) == selectedSeason)
                  .toList();
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (seasons.length > 1)
              _pillBar(
                seasons.map(_seasonLabel).toList(),
                selectedSeason.isEmpty ? '' : _seasonLabel(selectedSeason),
                (label) {
                  final season = seasons.firstWhere(
                    (season) => _seasonLabel(season) == label,
                  );
                  setState(
                    () => _watchingSeasonSelections[entry.playbackId] = season,
                  );
                },
              ),
            for (final episode in visibleEpisodes)
              ListTile(
                dense: true,
                contentPadding: const EdgeInsets.only(left: 64, right: 16),
                leading: episode.image.isEmpty
                    ? const Icon(Icons.play_circle_outline)
                    : Image.network(
                        episode.image,
                        width: 36,
                        height: 48,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) =>
                            const Icon(Icons.play_circle_outline),
                      ),
                title: Text(episode.title),
                subtitle: Text(
                  [
                    if (episode.season.isNotEmpty) 'S${episode.season}',
                    if (episode.episodeNum.isNotEmpty) 'E${episode.episodeNum}',
                  ].join(' · '),
                ),
                onTap: episode.playbackId.isEmpty
                    ? null
                    : () => _playWatchingEpisode(episode),
              ),
          ],
        );
      },
    );
  }

  String _seasonLabel(String season) =>
      (_bookmarkTranslations['autoSeasonNumber'] ?? 'Season {0}').replaceAll(
        '{0}',
        season,
      );

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
