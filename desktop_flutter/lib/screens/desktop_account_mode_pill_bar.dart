import 'dart:math' as math;

// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import 'desktop_pill_bar.dart';

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
        final width = fillAvailable
            ? constraints.maxWidth
            : math.min(400.0, constraints.maxWidth);
        return SizedBox(
          width: width,
          child: DesktopPillBar(
            barKey: 'account-mode-pills',
            dropdownKey: 'account-mode-dropdown',
            optionKeyPrefix: 'account-mode',
            options: options,
            compactAsDropdown: width < 400,
            selectedValue: selectedMode,
            onSelected: onSelected,
          ),
        );
      },
    );
  }
}
