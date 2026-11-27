// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopContextMenuRegion extends StatelessWidget {
  const DesktopContextMenuRegion({
    super.key,
    required this.child,
    required this.items,
    required this.onSelected,
  });

  final Widget child;
  final List<PopupMenuEntry<String>> items;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return child;
    return Builder(
      builder: (context) => GestureDetector(
        onSecondaryTapUp: (details) async {
          final size = MediaQuery.sizeOf(context);
          final selected = await showMenu<String>(
            context: context,
            position: RelativeRect.fromLTRB(
              details.globalPosition.dx,
              details.globalPosition.dy,
              size.width - details.globalPosition.dx,
              size.height - details.globalPosition.dy,
            ),
            items: items,
          );
          if (selected != null) onSelected(selected);
        },
        child: child,
      ),
    );
  }
}
