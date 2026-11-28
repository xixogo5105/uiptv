// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopPillBar extends StatelessWidget {
  const DesktopPillBar({
    super.key,
    required this.barKey,
    required this.options,
    required this.selectedValue,
    required this.onSelected,
    this.optionKeyPrefix,
    this.dropdownKey,
    this.compactAsDropdown = true,
  });

  final String barKey;
  final List<(String, String)> options;
  final String selectedValue;
  final ValueChanged<String> onSelected;
  final String? optionKeyPrefix;
  final String? dropdownKey;
  final bool compactAsDropdown;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final theme = Theme.of(context);
        final textStyle =
            theme.textTheme.bodyMedium?.copyWith(fontSize: 12.2) ??
            const TextStyle(fontSize: 12.2);
        final painter = TextPainter(textDirection: Directionality.of(context));
        var requiredWidth = 12.0 + (options.length - 1) * 5;
        for (final option in options) {
          painter.text = TextSpan(text: option.$2, style: textStyle);
          painter.layout();
          requiredWidth += painter.width + 30;
        }
        if (compactAsDropdown &&
            options.length > 1 &&
            requiredWidth > constraints.maxWidth) {
          return _buildDropdown(context, theme);
        }

        final barColor = theme.brightness == Brightness.light
            ? const Color(0xFFF5F5F5)
            : Colors.white.withValues(alpha: 0.035);
        final borderColor = theme.brightness == Brightness.light
            ? const Color(0xFFE5E5E5)
            : Colors.white.withValues(alpha: 0.055);
        return Container(
          key: Key(barKey),
          height: 50,
          padding: const EdgeInsets.all(5),
          decoration: BoxDecoration(
            color: barColor,
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: borderColor),
          ),
          child: SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (var index = 0; index < options.length; index++) ...[
                  if (index > 0) const SizedBox(width: 5),
                  _buildPill(context, options[index]),
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildPill(BuildContext context, (String, String) option) {
    final theme = Theme.of(context);
    final selected = selectedValue == option.$1;
    final keyPrefix = optionKeyPrefix ?? '$barKey-option';
    final selectedColor = theme.brightness == Brightness.light
        ? Colors.white
        : Colors.white.withValues(alpha: 0.16);
    final textColor = theme.brightness == Brightness.light
        ? const Color(0xFF111318)
        : const Color(0xFFF2F2F2);
    final unselectedTextColor = theme.brightness == Brightness.light
        ? const Color(0xFF737373)
        : const Color(0xFFB8B8B8);
    return Material(
      color: selected ? selectedColor : Colors.transparent,
      elevation: selected ? 1 : 0,
      shadowColor: Colors.black.withValues(
        alpha: theme.brightness == Brightness.light ? 0.13 : 0.45,
      ),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        key: Key('$keyPrefix-${option.$1}'),
        borderRadius: BorderRadius.circular(12),
        onTap: () => onSelected(option.$1),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 13, vertical: 5),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: selected && theme.brightness == Brightness.dark
                ? Border.all(color: Colors.white.withValues(alpha: 0.28))
                : null,
          ),
          child: Text(
            option.$2,
            style: TextStyle(
              fontSize: 12.2,
              color: selected ? textColor : unselectedTextColor,
              fontWeight: selected ? FontWeight.w700 : FontWeight.w600,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDropdown(BuildContext context, ThemeData theme) {
    final selected = options.any((option) => option.$1 == selectedValue)
        ? selectedValue
        : options.first.$1;
    final popupKey = dropdownKey ?? barKey;
    final isLight = theme.brightness == Brightness.light;
    final foreground = isLight
        ? const Color(0xFF111318)
        : const Color(0xFFF2F2F2);
    final popupColor = isLight ? Colors.white : const Color(0xFF252525);
    return PopupMenuButton<String>(
      key: Key(popupKey),
      tooltip: '',
      position: PopupMenuPosition.under,
      color: popupColor,
      surfaceTintColor: Colors.transparent,
      elevation: 8,
      shape: RoundedRectangleBorder(
        side: BorderSide(
          color: isLight ? const Color(0x1F0F172A) : const Color(0xFF3D3D3D),
        ),
        borderRadius: BorderRadius.circular(14),
      ),
      onSelected: onSelected,
      itemBuilder: (context) => [
        for (final option in options)
          PopupMenuItem<String>(
            value: option.$1,
            height: 40,
            padding: EdgeInsets.zero,
            child: Container(
              key: ValueKey('$popupKey-option-${option.$1}'),
              margin: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
              padding: const EdgeInsets.symmetric(horizontal: 10),
              decoration: BoxDecoration(
                color: option.$1 == selected
                    ? (isLight
                          ? const Color(0xFFF5F5F5)
                          : const Color(0xFF333333))
                    : Colors.transparent,
                borderRadius: BorderRadius.circular(9),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      option.$2,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: foreground,
                        fontSize: 13,
                        fontWeight: option.$1 == selected
                            ? FontWeight.w600
                            : FontWeight.normal,
                      ),
                    ),
                  ),
                  if (option.$1 == selected)
                    Icon(Icons.check, size: 16, color: foreground),
                ],
              ),
            ),
          ),
      ],
      child: Container(
        key: Key('$popupKey-surface'),
        height: 44,
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          color: isLight
              ? Colors.white.withValues(alpha: 0.78)
              : Colors.white.withValues(alpha: 0.035),
          border: Border.all(
            color: isLight
                ? const Color(0x1F0F172A)
                : Colors.white.withValues(alpha: 0.075),
          ),
          borderRadius: BorderRadius.circular(14),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                options.firstWhere((option) => option.$1 == selected).$2,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(
                  color: foreground,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
            Icon(Icons.keyboard_arrow_down, color: foreground, size: 20),
          ],
        ),
      ),
    );
  }
}
