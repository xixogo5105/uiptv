// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';
import 'desktop_responsive_card_grid.dart';
import 'desktop_pill_bar.dart';

ButtonStyle desktopBookmarkToolbarButtonStyle(BuildContext context) {
  final dark = Theme.of(context).brightness == Brightness.dark;
  return OutlinedButton.styleFrom(
    backgroundColor: dark
        ? const Color(0xFF252525)
        : Colors.white.withValues(alpha: 0.78),
    foregroundColor: dark ? const Color(0xFFF2F2F2) : const Color(0xFF111318),
    side: BorderSide(
      color: dark ? const Color(0xFF3D3D3D) : const Color(0x1F0F172A),
    ),
    minimumSize: const Size(0, 42),
    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 7),
    textStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
  );
}

class DesktopBookmarkCategorySelector extends StatelessWidget {
  const DesktopBookmarkCategorySelector({
    super.key,
    required this.categories,
    required this.selectedCategoryId,
    required this.onSelected,
  });

  final List<UiptvBookmarkCategory> categories;
  final String selectedCategoryId;
  final ValueChanged<UiptvBookmarkCategory> onSelected;

  @override
  Widget build(BuildContext context) {
    if (categories.isEmpty) return const SizedBox.shrink();
    return DesktopPillBar(
      barKey: 'bookmark-category-pill-bar',
      dropdownKey: 'bookmark-category-pill-dropdown',
      optionKeyPrefix: 'bookmark-category',
      options: [
        for (final category in categories) (category.id, category.name),
      ],
      selectedValue: selectedCategoryId,
      onSelected: (id) =>
          onSelected(categories.firstWhere((category) => category.id == id)),
    );
  }
}

SliverGridDelegate desktopBookmarkGridDelegate({
  required double availableWidth,
}) {
  return desktopResponsiveCardGridDelegate(
    availableWidth: availableWidth,
    minCardWidth: 255,
    maxCardWidth: 345,
    cardHeight: 108,
    horizontalPadding: 28,
    horizontalGap: 12,
    verticalGap: 12,
  );
}
