// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/screens/desktop_account_type_filter.dart';

void main() {
  testWidgets('account type filters are always visible horizontal pills', (
    tester,
  ) async {
    var selectedType = 'ALL';
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 260,
            child: DesktopAccountTypeFilter(
              selectedType: selectedType,
              allLabel: 'All',
              onSelected: (value) => selectedType = value,
            ),
          ),
        ),
      ),
    );

    expect(find.byType(SingleChildScrollView), findsOneWidget);
    expect(find.text('All'), findsOneWidget);
    expect(find.text('Stalker Portal'), findsOneWidget);
    expect(find.text('M3U8'), findsOneWidget);
    expect(find.text('Xtreme'), findsOneWidget);

    await tester.tap(find.byKey(const Key('account-filter-STALKER_PORTAL')));
    expect(selectedType, 'STALKER_PORTAL');
  });
}
