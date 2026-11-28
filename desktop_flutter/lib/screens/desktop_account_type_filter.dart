// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import 'desktop_pill_bar.dart';

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
    return DesktopPillBar(
      barKey: 'account-type-pill-bar',
      dropdownKey: 'account-type-pill-dropdown',
      optionKeyPrefix: 'account-filter',
      options: [
        for (final option in _options)
          (option.$1, option.$1 == 'ALL' ? allLabel : option.$2),
      ],
      compactAsDropdown: false,
      selectedValue: selectedType,
      onSelected: onSelected,
    );
  }
}
