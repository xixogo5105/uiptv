// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopNavigationButton extends StatelessWidget {
  static const compactWidthBreakpoint = 960.0;

  static bool usesIconOnlyNavigation(double availableWidth) =>
      availableWidth < compactWidthBreakpoint;

  const DesktopNavigationButton({
    super.key,
    required this.icon,
    required this.label,
    required this.selected,
    required this.iconOnly,
    required this.onPressed,
  });

  final IconData icon;
  final String label;
  final bool selected;
  final bool iconOnly;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final content = iconOnly
        ? SizedBox(
            width: 42,
            height: 42,
            child: Center(child: Icon(icon, size: 20)),
          )
        : Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(icon, size: 18),
                const SizedBox(width: 7),
                Text(
                  label,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 13),
                ),
              ],
            ),
          );

    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: Tooltip(
        message: label,
        child: Semantics(
          button: true,
          selected: selected,
          label: label,
          child: Material(
            color: selected
                ? colorScheme.secondaryContainer
                : Colors.transparent,
            borderRadius: BorderRadius.circular(iconOnly ? 12 : 20),
            child: InkWell(
              borderRadius: BorderRadius.circular(iconOnly ? 12 : 20),
              onTap: onPressed,
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(iconOnly ? 12 : 20),
                  border: Border.all(
                    color: selected
                        ? colorScheme.outlineVariant
                        : Colors.transparent,
                  ),
                ),
                child: content,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
