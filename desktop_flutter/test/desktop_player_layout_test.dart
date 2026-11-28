// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/player/desktop_player_layout.dart';

void main() {
  test('control bar width adapts to the player display mode', () {
    expect(
      DesktopPlayerLayout.controlBarMaxWidth(
        playerWidth: 1200,
        wideView: false,
        isFullscreen: false,
        isFloating: false,
      ),
      DesktopPlayerLayout.narrowControlBarWidth,
    );
    expect(
      DesktopPlayerLayout.controlBarMaxWidth(
        playerWidth: 1200,
        wideView: true,
        isFullscreen: false,
        isFloating: false,
      ),
      DesktopPlayerLayout.wideControlBarWidth,
    );
    expect(
      DesktopPlayerLayout.controlBarMaxWidth(
        playerWidth: 1200,
        wideView: false,
        isFullscreen: true,
        isFloating: false,
      ),
      DesktopPlayerLayout.fullscreenControlBarWidth,
    );
    expect(
      DesktopPlayerLayout.controlBarMaxWidth(
        playerWidth: 1200,
        wideView: false,
        isFullscreen: false,
        isFloating: true,
      ),
      DesktopPlayerLayout.floatingControlBarWidth,
    );
    expect(
      DesktopPlayerLayout.controlBarMaxWidth(
        playerWidth: 400,
        wideView: false,
        isFullscreen: false,
        isFloating: true,
      ),
      400,
    );
  });

  test('only VOD and series show the progress and transport row', () {
    expect(DesktopPlayerLayout.showsTimeline(mode: 'itv'), isFalse);
    expect(DesktopPlayerLayout.showsTimeline(mode: 'vod'), isTrue);
    expect(DesktopPlayerLayout.showsTimeline(mode: 'series'), isTrue);
  });

  test('seek controls are reserved for on-demand playback', () {
    expect(
      DesktopPlayerLayout.showsSeekButtonsInToolbar(
        mode: 'itv',
        playerWidth: 1200,
      ),
      isFalse,
    );
    expect(
      DesktopPlayerLayout.showsSeekButtonsBesideTimeline(
        mode: 'itv',
        playerWidth: 700,
      ),
      isFalse,
    );
    expect(
      DesktopPlayerLayout.showsSeekButtonsInToolbar(
        mode: 'vod',
        playerWidth: 1200,
      ),
      isTrue,
    );
    expect(
      DesktopPlayerLayout.showsSeekButtonsBesideTimeline(
        mode: 'vod',
        playerWidth: 700,
      ),
      isTrue,
    );
  });

  test(
    'channel navigation stays visible and disables movement at queue ends',
    () {
      expect(DesktopPlayerLayout.showsChannelNavigation(mode: 'itv'), isTrue);
      expect(DesktopPlayerLayout.showsChannelNavigation(mode: 'vod'), isFalse);
      expect(
        DesktopPlayerLayout.canNavigateChannel(
          currentIndex: 0,
          queueLength: 1,
          next: false,
        ),
        isFalse,
      );
      expect(
        DesktopPlayerLayout.canNavigateChannel(
          currentIndex: 0,
          queueLength: 1,
          next: true,
        ),
        isFalse,
      );
      expect(
        DesktopPlayerLayout.canNavigateChannel(
          currentIndex: 0,
          queueLength: 2,
          next: false,
        ),
        isFalse,
      );
      expect(
        DesktopPlayerLayout.canNavigateChannel(
          currentIndex: 0,
          queueLength: 2,
          next: true,
        ),
        isTrue,
      );
      expect(
        DesktopPlayerLayout.canNavigateChannel(
          currentIndex: 1,
          queueLength: 2,
          next: true,
        ),
        isFalse,
      );
    },
  );

  test('seek buttons move beside the timeline below 900px', () {
    expect(
      DesktopPlayerLayout.movesSeekButtonsToTimeline(playerWidth: 899),
      isTrue,
    );
    expect(
      DesktopPlayerLayout.movesSeekButtonsToTimeline(playerWidth: 900),
      isFalse,
    );
  });

  test(
    'narrow embedded playback shows inline EPG without a control button',
    () {
      expect(
        DesktopPlayerLayout.showsInlineEpg(
          wideView: false,
          isFullscreen: false,
          isFloating: false,
        ),
        isTrue,
      );
      expect(
        DesktopPlayerLayout.showsEpgControl(
          wideView: false,
          isFullscreen: false,
        ),
        isFalse,
      );
    },
  );

  test('wide playback uses the EPG control and no inline details', () {
    expect(
      DesktopPlayerLayout.showsInlineEpg(
        wideView: true,
        isFullscreen: false,
        isFloating: false,
      ),
      isFalse,
    );
    expect(
      DesktopPlayerLayout.showsEpgControl(wideView: true, isFullscreen: false),
      isTrue,
    );
  });

  test('fullscreen uses the EPG control even from narrow view', () {
    expect(
      DesktopPlayerLayout.showsInlineEpg(
        wideView: false,
        isFullscreen: true,
        isFloating: false,
      ),
      isFalse,
    );
    expect(
      DesktopPlayerLayout.showsEpgControl(wideView: false, isFullscreen: true),
      isTrue,
    );
  });

  test('floating playback does not show inline EPG', () {
    expect(
      DesktopPlayerLayout.showsInlineEpg(
        wideView: false,
        isFullscreen: false,
        isFloating: true,
      ),
      isFalse,
    );
  });

  test('floating playback does not expose the EPG control', () {
    expect(
      DesktopPlayerLayout.showsEpgControl(
        wideView: true,
        isFullscreen: false,
        isFloating: true,
      ),
      isFalse,
    );
  });

  testWidgets('wide embedded layout gives catalogue more width', (
    tester,
  ) async {
    await _pumpLayout(tester, wideView: true);

    final content = tester.getRect(find.byKey(const Key('content')));
    final player = tester.getRect(find.byKey(const Key('player')));
    expect(player.left, greaterThan(content.right));
    expect(content.width, greaterThan(player.width));
    expect(player.height / player.width, closeTo(9 / 16, 0.01));
  });

  testWidgets('narrow embedded layout gives player more width', (tester) async {
    await _pumpLayout(tester, wideView: false);

    final content = tester.getRect(find.byKey(const Key('content')));
    final player = tester.getRect(find.byKey(const Key('player')));
    expect(player.left, greaterThan(content.right));
    expect(player.width, greaterThan(content.width));
  });

  testWidgets('narrow window stacks the embedded player above content', (
    tester,
  ) async {
    await _pumpLayout(tester, width: 800, height: 800);

    final content = tester.getRect(find.byKey(const Key('content')));
    final player = tester.getRect(find.byKey(const Key('player')));
    expect(player.width, closeTo(content.width, 1));
    expect(player.top, lessThan(content.top));
    expect(player.bottom, lessThanOrEqualTo(content.top));
  });

  testWidgets('floating mode overlays the catalogue only when selected', (
    tester,
  ) async {
    await _pumpLayout(tester, isFloating: true);

    final content = tester.getRect(find.byKey(const Key('content')));
    final player = tester.getRect(find.byKey(const Key('player')));
    expect(player.right, closeTo(1200 - 18, 1));
    expect(player.bottom, closeTo(800 - 18, 1));
    expect(player.overlaps(content), isTrue);
  });

  testWidgets('floating player can be dragged within the app window', (
    tester,
  ) async {
    await _pumpLayout(tester, isFloating: true);

    final initialRect = tester.getRect(find.byKey(const Key('player')));
    await tester.drag(
      find.byKey(const ValueKey('desktop-pip-drag-handle')),
      const Offset(-180, -120),
    );
    await tester.pumpAndSettle();

    final movedRect = tester.getRect(find.byKey(const Key('player')));
    expect(movedRect.left, lessThan(initialRect.left));
    expect(movedRect.top, lessThan(initialRect.top));

    await tester.drag(
      find.byKey(const ValueKey('desktop-pip-drag-handle')),
      const Offset(-2000, -2000),
    );
    await tester.pumpAndSettle();

    final boundedRect = tester.getRect(find.byKey(const Key('player')));
    expect(boundedRect.left, greaterThanOrEqualTo(0));
    expect(boundedRect.top, greaterThanOrEqualTo(0));
  });

  testWidgets('fullscreen layout contains only the player', (tester) async {
    await _pumpLayout(tester, isFullscreen: true);

    expect(find.byKey(const Key('content')), findsNothing);
    expect(
      tester.getRect(find.byKey(const Key('player'))),
      const Rect.fromLTWH(0, 0, 1200, 800),
    );
  });
}

Future<void> _pumpLayout(
  WidgetTester tester, {
  double width = 1200,
  double height = 800,
  bool wideView = false,
  bool isFullscreen = false,
  bool isFloating = false,
}) async {
  tester.view.physicalSize = Size(width, height);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: DesktopPlayerLayout(
          content: const ColoredBox(key: Key('content'), color: Colors.blue),
          player: const ColoredBox(key: Key('player'), color: Colors.black),
          showPlayer: true,
          isFullscreen: isFullscreen,
          isFloating: isFloating,
          wideView: wideView,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}
