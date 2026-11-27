import 'dart:math' as math;

// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopAccountModePillBar extends StatelessWidget {
  const DesktopAccountModePillBar({
    super.key,
    required this.selectedMode,
    required this.supportsVodAndSeries,
    required this.fillAvailable,
    required this.labels,
    required this.onSelected,
  });

  final String selectedMode;
  final bool supportsVodAndSeries;
  final bool fillAvailable;
  final Map<String, String> labels;
  final ValueChanged<String> onSelected;

  List<(String, String)> get _options => [
    ('accounts', labels['autoAccount'] ?? 'Accounts'),
    ('itv', labels['autoTvChannels'] ?? 'TV Channels'),
    if (supportsVodAndSeries) ('vod', labels['autoVod'] ?? 'VOD'),
    if (supportsVodAndSeries) ('series', labels['autoSeries'] ?? 'Series'),
  ];

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final options = _options;
        final textStyle = DefaultTextStyle.of(context).style
            .copyWith(fontSize: 13);
        final textDirection = Directionality.of(context);
        final requiredWidth = options.fold<double>(12, (width, option) {
          final painter = TextPainter(
            text: TextSpan(text: option.$2, style: textStyle),
            textDirection: textDirection,
          )..layout();
          return width + painter.width + 24;
        });
        final availablePillWidth = fillAvailable
            ? constraints.maxWidth
            : math.min(400.0, constraints.maxWidth);
        final fitsOneRow = availablePillWidth >= requiredWidth;
        if (!fitsOneRow) {
          return _buildCompactDropdown(context, options);
        }
        final theme = Theme.of(context);
        final colorScheme = theme.colorScheme;
        return Container(
          key: const Key('account-mode-pills'),
          width: fillAvailable ? double.infinity : 400,
          height: 42,
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
          decoration: BoxDecoration(
            color: colorScheme.surfaceContainerHighest.withValues(alpha: 0.28),
            borderRadius: BorderRadius.circular(14),
            border: Border.all(
              color: colorScheme.outlineVariant.withValues(alpha: 0.38),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final option in options)
                _buildPill(context, option.$1, option.$2),
            ],
          ),
        );
      },
    );
  }

  Widget _buildPill(BuildContext context, String mode, String label) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final selected = selectedMode == mode;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 2),
      child: Material(
        color: selected
            ? colorScheme.surface.withValues(alpha: 0.9)
            : Colors.transparent,
        borderRadius: BorderRadius.circular(999),
        child: InkWell(
          key: Key('account-mode-$mode'),
          borderRadius: BorderRadius.circular(999),
          onTap: () => onSelected(mode),
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13,
                color: colorScheme.onSurface,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildCompactDropdown(
    BuildContext context,
    List<(String, String)> options,
  ) {
    final selected = options.any((option) => option.$1 == selectedMode)
        ? selectedMode
        : options.first.$1;
    return SizedBox(
      key: const Key('account-mode-dropdown'),
      width: double.infinity,
      height: 44,
      child: DropdownButtonFormField<String>(
        initialValue: selected,
        isExpanded: true,
        decoration: InputDecoration(
          isDense: true,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 12,
            vertical: 10,
          ),
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
        ),
        items: [
          for (final option in options)
            DropdownMenuItem<String>(
              value: option.$1,
              child: Text(
                option.$2,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
        ],
        onChanged: (value) {
          if (value != null) onSelected(value);
        },
      ),
    );
  }
}
