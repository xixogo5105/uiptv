// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';
import 'desktop_responsive_card_grid.dart';

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
    return LayoutBuilder(
      builder: (context, constraints) {
        final useDropdown = _needsDropdown(context, constraints.maxWidth);
        if (useDropdown) {
          final selected = categories.firstWhere(
            (category) => category.id == selectedCategoryId,
            orElse: () => categories.first,
          );
          return Container(
            height: 50,
            decoration: BoxDecoration(
              border: Border.all(
                color: Theme.of(context).colorScheme.outlineVariant,
              ),
              borderRadius: BorderRadius.circular(12),
            ),
            padding: const EdgeInsets.symmetric(horizontal: 10),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<String>(
                value: selected.id,
                isExpanded: true,
                items: [
                  for (final category in categories)
                    DropdownMenuItem(
                      value: category.id,
                      child: Text(
                        category.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                ],
                onChanged: (id) {
                  if (id == null) return;
                  onSelected(
                    categories.firstWhere((category) => category.id == id),
                  );
                },
              ),
            ),
          );
        }
        return SizedBox(
          height: 50,
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (var index = 0; index < categories.length; index++) ...[
                  if (index > 0) const SizedBox(width: 6),
                  ChoiceChip(
                    key: ValueKey('bookmark-category-${categories[index].id}'),
                    label: Text(categories[index].name),
                    selected: selectedCategoryId == categories[index].id,
                    onSelected: (_) => onSelected(categories[index]),
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  bool _needsDropdown(BuildContext context, double availableWidth) {
    if (categories.length < 2) return false;
    final style = Theme.of(context).textTheme.labelLarge ?? const TextStyle();
    final textPainter = TextPainter(textDirection: Directionality.of(context));
    var requiredWidth = (categories.length - 1) * 6.0;
    for (final category in categories) {
      textPainter.text = TextSpan(text: category.name, style: style);
      textPainter.layout();
      requiredWidth += textPainter.width + 44;
    }
    return requiredWidth > availableWidth;
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
