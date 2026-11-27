// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

class DesktopWatchingNowScreen extends StatefulWidget {
  const DesktopWatchingNowScreen({
    super.key,
    required this.entries,
    required this.translations,
    required this.onPlay,
    required this.onPlayEpisode,
    required this.onRemove,
    required this.loadEpisodes,
    required this.loadDetails,
    required this.onLoadMore,
    required this.loadingMore,
    required this.hasMore,
  });

  final List<UiptvWatchingNowEntry> entries;
  final Map<String, String> translations;
  final ValueChanged<UiptvWatchingNowEntry> onPlay;
  final ValueChanged<UiptvWatchingNowEpisode> onPlayEpisode;
  final Future<void> Function(UiptvWatchingNowEntry) onRemove;
  final Future<List<UiptvWatchingNowEpisode>> Function(UiptvWatchingNowEntry)
  loadEpisodes;
  final Future<Map<String, dynamic>> Function(UiptvWatchingNowEntry)
  loadDetails;
  final VoidCallback onLoadMore;
  final bool loadingMore;
  final bool hasMore;

  @override
  State<DesktopWatchingNowScreen> createState() =>
      _DesktopWatchingNowScreenState();
}

class _DesktopWatchingNowScreenState extends State<DesktopWatchingNowScreen> {
  final TextEditingController _searchController = TextEditingController();
  String _mode = 'series';
  UiptvWatchingNowEntry? _selectedEntry;
  Future<List<UiptvWatchingNowEpisode>>? _episodes;
  Future<Map<String, dynamic>>? _details;
  String? _selectedSeason;

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  String _text(String key, String fallback) =>
      widget.translations[key] ?? fallback;

  void _openSeries(UiptvWatchingNowEntry entry) {
    setState(() {
      _selectedEntry = entry;
      _episodes = widget.loadEpisodes(entry);
      _details = widget.loadDetails(entry);
      _selectedSeason = null;
    });
  }

  void _reloadSeries() {
    final entry = _selectedEntry;
    if (entry == null) return;
    setState(() {
      _episodes = widget.loadEpisodes(entry);
      _details = widget.loadDetails(entry);
      _selectedSeason = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final selected = _selectedEntry;
    if (selected != null) return _buildSeriesDetails(selected);
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final tabs = _buildModeTabs();
              final search = _buildSearchField();
              if (constraints.maxWidth < 620) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [tabs, const SizedBox(height: 8), search],
                );
              }
              return Row(
                children: [
                  SizedBox(width: 210, child: tabs),
                  const SizedBox(width: 8),
                  Expanded(child: search),
                ],
              );
            },
          ),
        ),
        Expanded(child: _buildEntryGrid()),
      ],
    );
  }

  Widget _buildModeTabs() {
    return SegmentedButton<String>(
      segments: [
        ButtonSegment(
          value: 'series',
          label: Text(_text('autoSeries', 'Series')),
        ),
        ButtonSegment(value: 'vod', label: Text(_text('autoVod', 'VOD'))),
      ],
      selected: {_mode},
      onSelectionChanged: (selected) => setState(() => _mode = selected.first),
    );
  }

  Widget _buildSearchField() {
    return TextField(
      controller: _searchController,
      onChanged: (_) => setState(() {}),
      decoration: InputDecoration(
        hintText: _text('commonSearch', 'Search'),
        prefixIcon: const Icon(Icons.search),
        suffixIcon: _searchController.text.isEmpty
            ? null
            : IconButton(
                tooltip: _text('autoClear', 'Clear'),
                onPressed: () {
                  _searchController.clear();
                  setState(() {});
                },
                icon: const Icon(Icons.close),
              ),
      ),
    );
  }

  Widget _buildEntryGrid() {
    final query = _searchController.text.trim().toLowerCase();
    final filtered = widget.entries
        .where((entry) {
          if (entry.isSeries != (_mode == 'series')) return false;
          return query.isEmpty ||
              '${entry.title} ${entry.accountName} ${entry.details}'
                  .toLowerCase()
                  .contains(query);
        })
        .toList(growable: false);
    if (filtered.isEmpty) {
      return Center(
        child: Text(
          _text(
            'autoNothingFoundFor',
            'Nothing found for {0}',
          ).replaceAll('{0}', _searchController.text),
        ),
      );
    }
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (widget.hasMore &&
            !widget.loadingMore &&
            notification.metrics.maxScrollExtent - notification.metrics.pixels <
                300) {
          widget.onLoadMore();
        }
        return false;
      },
      child: GridView.builder(
        padding: const EdgeInsets.fromLTRB(14, 8, 14, 16),
        itemCount: filtered.length + (widget.loadingMore ? 1 : 0),
        gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(
          maxCrossAxisExtent: 620,
          mainAxisExtent: 246,
          crossAxisSpacing: 14,
          mainAxisSpacing: 14,
        ),
        itemBuilder: (context, index) {
          if (index >= filtered.length) {
            return const Center(child: CircularProgressIndicator());
          }
          return _entryCard(filtered[index]);
        },
      ),
    );
  }

  Widget _entryCard(UiptvWatchingNowEntry entry) {
    return Card(
      key: Key('watching-entry-card-${entry.playbackId}'),
      clipBehavior: Clip.antiAlias,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _poster(entry.image, width: 136, height: 204),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: Text(
                          entry.title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                      ),
                      IconButton(
                        tooltip: _text('autoRemove', 'Remove'),
                        color: Theme.of(context).colorScheme.error,
                        onPressed: () => widget.onRemove(entry),
                        icon: const Icon(Icons.delete_outline),
                      ),
                    ],
                  ),
                  Text(
                    entry.accountName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 6,
                    runSpacing: 4,
                    children: [
                      _metadataChip(
                        entry.isSeries
                            ? _text('autoSeries', 'Series')
                            : _text('autoVod', 'VOD'),
                      ),
                      if (entry.season.isNotEmpty)
                        _metadataChip(
                          _text(
                            'autoSeasonNumber',
                            'Season {0}',
                          ).replaceAll('{0}', entry.season),
                        ),
                      if (entry.details.isNotEmpty)
                        _metadataChip(entry.details),
                    ],
                  ),
                  const Spacer(),
                  Align(
                    alignment: Alignment.bottomLeft,
                    child: entry.isSeries
                        ? OutlinedButton(
                            onPressed: () => _openSeries(entry),
                            child: Text(
                              _text('autoViewEpisodes', 'View Episodes...'),
                            ),
                          )
                        : FilledButton.tonalIcon(
                            onPressed: () => widget.onPlay(entry),
                            icon: const Icon(Icons.play_arrow),
                            label: Text(_text('autoPlay', 'Play')),
                          ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSeriesDetails(UiptvWatchingNowEntry entry) {
    final title = entry.title;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 10, 16, 10),
          child: Row(
            children: [
              IconButton(
                tooltip: _text('autoBack', 'Back'),
                onPressed: () => setState(() => _selectedEntry = null),
                icon: const Icon(Icons.arrow_back),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _text('autoWatchingNow', 'Watching Now'),
                      style: Theme.of(context).textTheme.titleMedium,
                    ),
                    Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
                  ],
                ),
              ),
              OutlinedButton.icon(
                onPressed: _reloadSeries,
                icon: const Icon(Icons.refresh),
                label: Text(
                  _text('autoReloadFromServer', 'Reload from server'),
                ),
              ),
            ],
          ),
        ),
        Expanded(
          child: LayoutBuilder(
            builder: (context, constraints) {
              final metadata = _seriesMetadata(entry);
              final episodes = _seriesEpisodes();
              if (constraints.maxWidth < 840) {
                final metadataHeight = (constraints.maxHeight * 0.38).clamp(
                  210.0,
                  280.0,
                );
                return Padding(
                  padding: const EdgeInsets.fromLTRB(12, 0, 12, 12),
                  child: Column(
                    children: [
                      SizedBox(
                        height: metadataHeight,
                        child: _seriesMetadata(entry, compact: true),
                      ),
                      const SizedBox(height: 8),
                      Expanded(child: episodes),
                    ],
                  ),
                );
              }
              return Row(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  SizedBox(width: 330, child: metadata),
                  const SizedBox(width: 12),
                  Expanded(child: episodes),
                ],
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _seriesMetadata(UiptvWatchingNowEntry entry, {bool compact = false}) {
    return FutureBuilder<Map<String, dynamic>>(
      future: _details,
      builder: (context, snapshot) {
        final data = snapshot.data ?? const <String, dynamic>{};
        final image = data['poster'] as String? ?? entry.image;
        final title = data['name'] as String? ?? entry.title;
        final plot = data['plot'] as String? ?? '';
        final rating = data['rating'] as String? ?? '';
        final genre = data['genre'] as String? ?? '';
        final releaseDate = data['releaseDate'] as String? ?? '';
        final content = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(
              entry.accountName,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
            if (snapshot.connectionState == ConnectionState.waiting)
              const Padding(
                padding: EdgeInsets.only(top: 10),
                child: LinearProgressIndicator(),
              ),
            if (rating.isNotEmpty)
              _metadataLine(
                _text(
                  'autoRatingPrefix',
                  'Rating: {0}',
                ).replaceAll('{0}', rating),
              ),
            if (genre.isNotEmpty)
              _metadataLine(
                _text('autoGenrePrefix', 'Genre: {0}').replaceAll('{0}', genre),
              ),
            if (releaseDate.isNotEmpty)
              _metadataLine(
                _text(
                  'autoReleasePrefix',
                  'Release: {0}',
                ).replaceAll('{0}', releaseDate),
              ),
            if (plot.isNotEmpty) ...[const SizedBox(height: 6), Text(plot)],
          ],
        );
        return Card(
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: compact
                ? Row(
                    children: [
                      _poster(image, width: 120, height: 180),
                      const SizedBox(width: 12),
                      Expanded(child: SingleChildScrollView(child: content)),
                    ],
                  )
                : Column(
                    children: [
                      Center(child: _poster(image, width: 246, height: 366)),
                      const SizedBox(height: 12),
                      Expanded(child: SingleChildScrollView(child: content)),
                    ],
                  ),
          ),
        );
      },
    );
  }

  Widget _seriesEpisodes() {
    return FutureBuilder<List<UiptvWatchingNowEpisode>>(
      future: _episodes,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(child: Text(snapshot.error.toString()));
        }
        final episodes = snapshot.data ?? const [];
        if (episodes.isEmpty) return const SizedBox.shrink();
        final seasons =
            episodes
                .map((episode) => episode.season.isEmpty ? '1' : episode.season)
                .toSet()
                .toList()
              ..sort(
                (a, b) =>
                    int.tryParse(a)?.compareTo(int.tryParse(b) ?? 0) ??
                    a.compareTo(b),
              );
        final selected = seasons.contains(_selectedSeason)
            ? _selectedSeason!
            : seasons.first;
        final visible = episodes
            .where(
              (episode) =>
                  (episode.season.isEmpty ? '1' : episode.season) == selected,
            )
            .toList(growable: false);
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(14, 12, 14, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      _text('autoEpisodes', 'Episodes'),
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                  ),
                  Text('${episodes.length}'),
                ],
              ),
            ),
            if (seasons.length > 1)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Wrap(
                  spacing: 6,
                  children: [
                    for (final season in seasons)
                      ChoiceChip(
                        label: Text(
                          _text(
                            'autoSeasonNumber',
                            'Season {0}',
                          ).replaceAll('{0}', season),
                        ),
                        selected: selected == season,
                        onSelected: (_) =>
                            setState(() => _selectedSeason = season),
                      ),
                  ],
                ),
              ),
            Expanded(
              child: ListView.separated(
                padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
                itemCount: visible.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final episode = visible.elementAt(index);
                  return _episodeCard(episode);
                },
              ),
            ),
          ],
        );
      },
    );
  }

  Widget _episodeCard(UiptvWatchingNowEpisode episode) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(10),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _poster(episode.image, width: 236, height: 132, landscape: true),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '${_text('autoSeasonNumber', 'Season {0}').replaceAll('{0}', episode.season.isEmpty ? '1' : episode.season)} - ${_text('autoEpisodeNumber', 'Episode {0}').replaceAll('{0}', episode.episodeNum)}',
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  if (episode.title.isNotEmpty) Text(episode.title),
                  if (episode.rating.isNotEmpty)
                    _metadataLine(
                      _text(
                        'autoRatingPrefix',
                        'Rating: {0}',
                      ).replaceAll('{0}', episode.rating),
                    ),
                  if (episode.releaseDate.isNotEmpty)
                    _metadataLine(
                      _text(
                        'autoReleasePrefix',
                        'Release: {0}',
                      ).replaceAll('{0}', episode.releaseDate),
                    ),
                  if (episode.description.isNotEmpty) ...[
                    const SizedBox(height: 4),
                    Text(
                      episode.description,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                  const SizedBox(height: 6),
                  Align(
                    alignment: Alignment.centerRight,
                    child: IconButton.filledTonal(
                      tooltip: _text('autoPlay', 'Play'),
                      onPressed: () => widget.onPlayEpisode(episode),
                      icon: const Icon(Icons.play_arrow),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _metadataLine(String text) => Padding(
    padding: const EdgeInsets.only(top: 5),
    child: Text(
      text,
      style: Theme.of(context).textTheme.bodyMedium
          ?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
    ),
  );

  Widget _metadataChip(String text) => Chip(
    label: Text(text, maxLines: 1, overflow: TextOverflow.ellipsis),
    visualDensity: VisualDensity.compact,
    padding: EdgeInsets.zero,
  );

  Widget _poster(
    String url, {
    required double width,
    required double height,
    bool landscape = false,
  }) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(9),
      child: SizedBox(
        width: width,
        height: height,
        child: url.isEmpty
            ? ColoredBox(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: Icon(
                  landscape ? Icons.movie_outlined : Icons.live_tv,
                  size: 38,
                ),
              )
            : Image.network(
                url,
                fit: BoxFit.cover,
                errorBuilder: (_, _, _) => ColoredBox(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  child: const Icon(Icons.live_tv, size: 38),
                ),
              ),
      ),
    );
  }
}
