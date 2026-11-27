// ignore: deprecated_member_use
import 'package:flutter/material.dart';

SliverGridDelegate desktopResponsiveCardGridDelegate({
  required double availableWidth,
  required double minCardWidth,
  required double maxCardWidth,
  required double cardHeight,
  required double horizontalPadding,
  double horizontalGap = 12,
  double verticalGap = 12,
}) {
  final contentWidth = (availableWidth - horizontalPadding)
      .clamp(0.0, double.infinity)
      .toDouble();
  var columnCount =
      ((contentWidth + horizontalGap) / (minCardWidth + horizontalGap))
          .floor()
          .clamp(1, 1000)
          .toInt();
  var cardWidth =
      (contentWidth - (columnCount - 1) * horizontalGap) / columnCount;
  while (cardWidth > maxCardWidth) {
    final nextColumnCount = columnCount + 1;
    final nextCardWidth =
        (contentWidth - (nextColumnCount - 1) * horizontalGap) /
        nextColumnCount;
    if (nextCardWidth < minCardWidth) break;
    columnCount = nextColumnCount;
    cardWidth = nextCardWidth;
  }

  return SliverGridDelegateWithFixedCrossAxisCount(
    crossAxisCount: columnCount,
    mainAxisExtent: cardHeight,
    crossAxisSpacing: horizontalGap,
    mainAxisSpacing: verticalGap,
  );
}

SliverGridDelegate desktopChannelGridDelegate({
  double cardHeight = 108,
  double horizontalGap = 12,
  double verticalGap = 12,
}) {
  return SliverGridDelegateWithFixedCrossAxisCount(
    crossAxisCount: 1,
    mainAxisExtent: cardHeight,
    crossAxisSpacing: horizontalGap,
    mainAxisSpacing: verticalGap,
  );
}
