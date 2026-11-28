import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/desktop_watching_now_screen.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('Watching shows JavaFX-style series cards and loaded details', (
    tester,
  ) async {
    await _setSize(tester, const Size(1700, 960));
    final played = <String>[];
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DesktopWatchingNowScreen(
            entries: [_seriesEntry, _vodEntry],
            translations: _translations,
            onPlay: (entry) => played.add(entry.playbackId),
            onPlayEpisode: (episode) => played.add(episode.playbackId),
            onRemove: (_) async {},
            loadEpisodes: (_) async => _episodes,
            loadDetails: (_) async => _seriesDetails,
            onLoadMore: () {},
            loadingMore: false,
            hasMore: false,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('watching-entry-card-series-1')),
      findsOneWidget,
    );
    expect(find.byKey(const Key('watching-entry-card-vod-1')), findsNothing);
    expect(find.text('View Episodes...'), findsOneWidget);
    expect(find.byKey(const Key('watching-mode-pill-bar')), findsOneWidget);
    expect(find.byType(SegmentedButton<String>), findsNothing);

    final seriesTitle = find.byKey(
      const Key('watching-entry-select-series-1-title'),
    );
    await tester.tap(seriesTitle);
    await tester.pump(const Duration(milliseconds: 400));
    expect(find.text('Watching Now'), findsNothing);
    expect(played, isEmpty);

    await _doubleTap(tester, seriesTitle);
    await tester.pumpAndSettle();

    expect(find.text('Watching Now'), findsOneWidget);
    expect(find.text('Example series plot.'), findsOneWidget);
    expect(find.text('Rating: 8.5'), findsOneWidget);
    expect(find.text('Season 1 - Episode 1'), findsOneWidget);
    expect(tester.takeException(), isNull);
    expect(played, isEmpty);

    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('View Episodes...'));
    await tester.pumpAndSettle();
    expect(find.text('Example series plot.'), findsOneWidget);
    await tester.tap(find.byTooltip('Back'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('VOD').first);
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('watching-entry-card-vod-1')), findsOneWidget);
    final vodTitle = find.byKey(const Key('watching-entry-select-vod-1-title'));
    await tester.tap(vodTitle);
    await tester.pump(const Duration(milliseconds: 400));
    expect(played, isEmpty);
    await _doubleTap(tester, vodTitle);
    expect(played, ['vod-1']);
    await tester.tap(find.text('Play'));
    expect(played, ['vod-1', 'vod-1']);
  });

  testWidgets('Watching series details remain usable in a narrow window', (
    tester,
  ) async {
    await _setSize(tester, const Size(620, 850));
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DesktopWatchingNowScreen(
            entries: [_seriesEntry],
            translations: _translations,
            onPlay: (_) {},
            onPlayEpisode: (_) {},
            onRemove: (_) async {},
            loadEpisodes: (_) async => _episodes,
            loadDetails: (_) async => _seriesDetails,
            onLoadMore: () {},
            loadingMore: false,
            hasMore: false,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('View Episodes...'));
    await tester.pumpAndSettle();

    expect(find.text('Reload from server'), findsOneWidget);
    expect(find.text('Example series plot.'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

Future<void> _doubleTap(WidgetTester tester, Finder finder) async {
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 100));
  await tester.tap(finder);
  await tester.pump(const Duration(milliseconds: 100));
}

Future<void> _setSize(WidgetTester tester, Size size) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
}

const _seriesEntry = UiptvWatchingNowEntry(
  isSeries: true,
  playbackId: 'series-1',
  accountName: 'Test provider',
  title: 'Example Series',
  image: '',
  details: 'Season 1 · Episode 1',
  season: '1',
  updatedAt: 1,
);

const _vodEntry = UiptvWatchingNowEntry(
  isSeries: false,
  playbackId: 'vod-1',
  accountName: 'Test provider',
  title: 'Example Film',
  image: '',
  details: '2025 · 90 · 8.0',
  season: '',
  updatedAt: 1,
);

const _episodes = [
  UiptvWatchingNowEpisode(
    playbackId: 'episode-1',
    title: 'The Beginning',
    season: '1',
    episodeNum: '1',
    image: '',
    description: 'Episode synopsis.',
    rating: '8.1',
    releaseDate: '2024-01-01',
  ),
];

const _seriesDetails = {
  'name': 'Example Series',
  'poster': '',
  'plot': 'Example series plot.',
  'rating': '8.5',
  'genre': 'Drama',
  'releaseDate': '2024',
};

const _translations = {
  'autoSeries': 'Series',
  'autoVod': 'VOD',
  'commonSearch': 'Search',
  'autoViewEpisodes': 'View Episodes...',
  'autoEpisodes': 'Episodes',
  'autoWatchingNow': 'Watching Now',
  'autoBack': 'Back',
  'autoReloadFromServer': 'Reload from server',
  'autoSeasonNumber': 'Season {0}',
  'autoEpisodeNumber': 'Episode {0}',
  'autoRatingPrefix': 'Rating: {0}',
  'autoGenrePrefix': 'Genre: {0}',
  'autoReleasePrefix': 'Release: {0}',
};
