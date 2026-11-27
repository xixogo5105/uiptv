// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:desktop_flutter/screens/desktop_navigation_button.dart';

void main() {
  test(
    'navigation labels collapse only when the available width is narrow',
    () {
      expect(DesktopNavigationButton.usesIconOnlyNavigation(900), isTrue);
      expect(DesktopNavigationButton.usesIconOnlyNavigation(1200), isFalse);
    },
  );

  testWidgets('icon-only navigation keeps its accessible label', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          appBar: AppBar(
            title: Row(
              children: [
                DesktopNavigationButton(
                  icon: Icons.favorite,
                  label: 'Favourites',
                  selected: true,
                  iconOnly: true,
                  onPressed: () {},
                ),
              ],
            ),
          ),
        ),
      ),
    );

    expect(find.text('Favourites'), findsNothing);
    expect(find.byTooltip('Favourites'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('normal navigation displays its label', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DesktopNavigationButton(
            icon: Icons.person,
            label: 'Accounts',
            selected: false,
            iconOnly: false,
            onPressed: () {},
          ),
        ),
      ),
    );

    expect(find.text('Accounts'), findsOneWidget);
    expect(find.byTooltip('Accounts'), findsOneWidget);
  });
}
