// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import 'desktop_responsive_card_grid.dart';

SliverGridDelegate desktopAccountGridDelegate({
  required double availableWidth,
}) {
  final compactPane = availableWidth <= 430;
  return desktopResponsiveCardGridDelegate(
    availableWidth: availableWidth,
    minCardWidth: compactPane ? 230 : 300,
    maxCardWidth: compactPane ? 380 : 430,
    cardHeight: 100,
    horizontalPadding: 16,
  );
}

class DesktopAccountBrowserLayout extends StatelessWidget {
  static const singleColumnBreakpoint = 1080.0;

  static bool usesSingleColumn(double width) => width < singleColumnBreakpoint;
  const DesktopAccountBrowserLayout({
    super.key,
    required this.accountPane,
    required this.catalog,
    required this.editor,
    required this.showEditor,
    required this.hasSelectedAccount,
    required this.hasSelectedCategory,
  });

  final Widget accountPane;
  final Widget catalog;
  final Widget editor;
  final bool showEditor;
  final bool hasSelectedAccount;
  final bool hasSelectedCategory;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final wide = !usesSingleColumn(constraints.maxWidth);
        if (showEditor && !hasSelectedAccount) {
          if (!wide) return editor;
          return Row(
            children: [
              SizedBox(width: 340, child: editor),
              Expanded(child: accountPane),
            ],
          );
        }
        if (showEditor && (!wide || constraints.maxWidth < 1360)) {
          return editor;
        }
        if (!wide) return hasSelectedAccount ? catalog : accountPane;
        if (!hasSelectedAccount) return accountPane;
        return Row(
          children: [
            if (showEditor) SizedBox(width: 340, child: editor),
            SizedBox(
              width: hasSelectedCategory ? 260 : 330,
              child: accountPane,
            ),
            const VerticalDivider(width: 1),
            Expanded(child: catalog),
          ],
        );
      },
    );
  }
}
