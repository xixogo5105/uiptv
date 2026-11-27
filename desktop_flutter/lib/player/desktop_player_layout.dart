import 'dart:math' as math;

// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopPlayerLayout extends StatelessWidget {
  static const narrowLayoutBreakpoint = 900.0;

  static bool movesSeekButtonsToTimeline({required double playerWidth}) =>
      playerWidth < narrowLayoutBreakpoint;

  static bool showsTimeline({required String mode}) =>
      mode == 'vod' || mode == 'series';

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
      return LayoutBuilder(
        builder: (context, constraints) {
          final pipWidth = (constraints.maxWidth - 36)
              .clamp(200.0, 420.0)
              .toDouble();
          final pipHeight = pipWidth * 9 / 16 + 50;
          return Stack(
            children: [
              Positioned.fill(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 4, 10, 10),
                  child: content,
                ),
              ),
              Positioned(
                right: 18,
                bottom: 18,
                width: pipWidth,
                height: pipHeight,
                child: player,
              ),
            ],
          );
        },
      );
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
