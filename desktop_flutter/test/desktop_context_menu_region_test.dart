// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:desktop_flutter/widgets/desktop_context_menu_region.dart';

void main() {
  testWidgets('secondary click opens the same selectable context menu', (
    tester,
  ) async {
    String? selectedAction;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: DesktopContextMenuRegion(
              items: const [
                PopupMenuItem(value: 'manage', child: Text('Manage account')),
              ],
              onSelected: (action) => selectedAction = action,
              child: const SizedBox(
                width: 240,
                height: 72,
                child: Center(child: Text('Account row')),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.tapAt(
      tester.getCenter(find.text('Account row')),
      buttons: kSecondaryButton,
    );
    await tester.pumpAndSettle();
    expect(find.text('Manage account'), findsOneWidget);

    await tester.tap(find.text('Manage account'));
    await tester.pumpAndSettle();

    expect(selectedAction, 'manage');
  });
}
