import 'dart:math' as math;

// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopPlayerLayout extends StatelessWidget {
  static const narrowLayoutBreakpoint = 900.0;
  static const narrowControlBarWidth = 480.0;
  static const floatingControlBarWidth = 520.0;
  static const wideControlBarWidth = 680.0;
  static const fullscreenControlBarWidth = 800.0;

  static double controlBarMaxWidth({
    required double playerWidth,
    required bool wideView,
    required bool isFullscreen,
    required bool isFloating,
  }) {
    final preferredWidth = isFullscreen
        ? fullscreenControlBarWidth
        : isFloating
        ? floatingControlBarWidth
        : wideView
        ? wideControlBarWidth
        : narrowControlBarWidth;
    return math.min(playerWidth, preferredWidth);
  }

  static bool movesSeekButtonsToTimeline({required double playerWidth}) =>
      playerWidth < narrowLayoutBreakpoint;

  static bool showsTimeline({required String mode}) =>
      mode == 'vod' || mode == 'series';

  static bool showsSeekButtonsInToolbar({
    required String mode,
    required double playerWidth,
  }) =>
      showsTimeline(mode: mode) &&
      !movesSeekButtonsToTimeline(playerWidth: playerWidth);

  static bool showsSeekButtonsBesideTimeline({
    required String mode,
    required double playerWidth,
  }) =>
      showsTimeline(mode: mode) &&
      movesSeekButtonsToTimeline(playerWidth: playerWidth);

  static bool showsChannelNavigation({required String mode}) => mode == 'itv';

  static bool canNavigateChannel({
    required int currentIndex,
    required int queueLength,
    required bool next,
  }) {
    if (queueLength <= 1 || currentIndex < 0 || currentIndex >= queueLength) {
      return false;
    }
    final targetIndex = currentIndex + (next ? 1 : -1);
    return targetIndex >= 0 && targetIndex < queueLength;
  }

  static bool showsInlineEpg({
    required bool wideView,
    required bool isFullscreen,
    required bool isFloating,
  }) => !wideView && !isFullscreen && !isFloating;

  static bool showsEpgControl({
    required bool wideView,
    required bool isFullscreen,
    bool isFloating = false,
  }) => isFullscreen || (wideView && !isFloating);

  const DesktopPlayerLayout({
    super.key,
    required this.content,
    required this.player,
    required this.showPlayer,
    required this.isFullscreen,
    required this.isFloating,
    required this.wideView,
  });

  final Widget content;
  final Widget player;
  final bool showPlayer;
  final bool isFullscreen;
  final bool isFloating;
  final bool wideView;

  @override
  Widget build(BuildContext context) {
    if (isFullscreen) {
      return SizedBox.expand(child: player);
    }

    if (!showPlayer) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
        child: content,
      );
    }

    if (isFloating) {
      return _DesktopFloatingPlayerLayout(content: content, player: player);
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        if (constraints.maxWidth < narrowLayoutBreakpoint) {
          final playerHeight = math.min(
            (constraints.maxWidth - 20) * 9 / 16 + 68,
            constraints.maxHeight * 0.58,
          );
          return Column(
            children: [
              SizedBox(
                height: playerHeight,
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 4, 10, 4),
                  child: player,
                ),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
                  child: content,
                ),
              ),
            ],
          );
        }

        return Row(
          children: [
            Expanded(
              flex: wideView ? 3 : 1,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(10, 4, 4, 10),
                child: content,
              ),
            ),
            Expanded(
              flex: wideView ? 1 : 3,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(4, 4, 10, 10),
                child: wideView
                    ? Align(
                        alignment: Alignment.topCenter,
                        child: AspectRatio(aspectRatio: 16 / 9, child: player),
                      )
                    : player,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _DesktopFloatingPlayerLayout extends StatefulWidget {
  const _DesktopFloatingPlayerLayout({
    required this.content,
    required this.player,
  });

  final Widget content;
  final Widget player;

  @override
  State<_DesktopFloatingPlayerLayout> createState() =>
      _DesktopFloatingPlayerLayoutState();
}

class _DesktopFloatingPlayerLayoutState
    extends State<_DesktopFloatingPlayerLayout> {
  Offset? _position;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final pipWidth = (constraints.maxWidth - 36)
          .clamp(200.0, 420.0)
          .toDouble();
      final pipHeight = pipWidth * 9 / 16 + 50;
      final maxLeft = math.max(0.0, constraints.maxWidth - pipWidth);
      final maxTop = math.max(0.0, constraints.maxHeight - pipHeight);
      final left = (_position?.dx ?? math.max(0.0, maxLeft - 18))
          .clamp(0.0, maxLeft)
          .toDouble();
      final top = (_position?.dy ?? math.max(0.0, maxTop - 18))
          .clamp(0.0, maxTop)
          .toDouble();

      return Stack(
        children: [
          Positioned.fill(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
              child: widget.content,
            ),
          ),
          Positioned(
            left: left,
            top: top,
            width: pipWidth,
            height: pipHeight,
            child: Stack(
              children: [
                Positioned.fill(child: widget.player),
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  height: 24,
                  child: MouseRegion(
                    cursor: SystemMouseCursors.move,
                    child: GestureDetector(
                      key: const ValueKey('desktop-pip-drag-handle'),
                      behavior: HitTestBehavior.opaque,
                      onPanUpdate: (details) {
                        setState(() {
                          _position = Offset(
                            ((_position?.dx ?? left) + details.delta.dx)
                                .clamp(0.0, maxLeft)
                                .toDouble(),
                            ((_position?.dy ?? top) + details.delta.dy)
                                .clamp(0.0, maxTop)
                                .toDouble(),
                          );
                        });
                      },
                      child: Center(
                        child: Container(
                          width: 58,
                          height: 16,
                          decoration: BoxDecoration(
                            color: const Color(0x66000000),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: const Icon(
                            Icons.drag_handle,
                            color: Colors.white70,
                            size: 15,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      );
    },
  );
}
