// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopAccountTypeFilter extends StatelessWidget {
  const DesktopAccountTypeFilter({
    super.key,
    required this.selectedType,
    required this.allLabel,
    required this.onSelected,
  });

  final String selectedType;
  final String allLabel;
  final ValueChanged<String> onSelected;

  static const _options = [
    ('ALL', ''),
    ('STALKER_PORTAL', 'Stalker Portal'),
    ('M3U8', 'M3U8'),
    ('XTREME_API', 'Xtreme'),
  ];

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 42,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: [
            for (final option in _options)
              Padding(
                padding: const EdgeInsets.only(right: 6),
                child: _filterPill(
                  context,
                  value: option.$1,
                  label: option.$1 == 'ALL' ? allLabel : option.$2,
                  selected: selectedType == option.$1,
                  theme: theme,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _filterPill(
    BuildContext context, {
    required String value,
    required String label,
    required bool selected,
    required ThemeData theme,
  }) {
    final colorScheme = theme.colorScheme;
    return InkWell(
      key: Key('account-filter-$value'),
      onTap: () => onSelected(value),
      borderRadius: BorderRadius.circular(999),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 120),
        padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 8),
        decoration: BoxDecoration(
          color: selected
              ? colorScheme.secondaryContainer.withValues(alpha: 0.92)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(
            color: selected
                ? colorScheme.outline.withValues(alpha: 0.52)
                : Colors.transparent,
          ),
        ),
        child: Text(
          label,
          style: TextStyle(
            fontSize: 14,
            color: selected
                ? colorScheme.onSecondaryContainer
                : colorScheme.onSurfaceVariant,
            fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
          ),
        ),
      ),
    );
  }
}
