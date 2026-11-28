// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:desktop_flutter/screens/desktop_pill_bar.dart';

void main() {
  testWidgets('light pill bar and selected item use JavaFX colors', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.light(),
        home: Scaffold(
          body: SizedBox(
            width: 500,
            child: DesktopPillBar(
              barKey: 'pill-bar',
              optionKeyPrefix: 'pill',
              options: const [('recent', 'Recent'), ('all', 'All')],
              selectedValue: 'recent',
              onSelected: (_) {},
              compactAsDropdown: false,
            ),
          ),
        ),
      ),
    );

    final bar = tester.widget<Container>(find.byKey(const Key('pill-bar')));
    expect((bar.decoration! as BoxDecoration).color, const Color(0xFFF5F5F5));
    final selectedMaterial = tester.widget<Material>(
      find
          .ancestor(
            of: find.byKey(const Key('pill-recent')),
            matching: find.byType(Material),
          )
          .first,
    );
    expect(selectedMaterial.color, Colors.white);
  });

  testWidgets('dark selected pill has the subtle JavaFX outline', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData.dark(),
        home: Scaffold(
          body: SizedBox(
            width: 500,
            child: DesktopPillBar(
              barKey: 'pill-bar',
              optionKeyPrefix: 'pill',
              options: const [('recent', 'Recent'), ('all', 'All')],
              selectedValue: 'recent',
              onSelected: (_) {},
              compactAsDropdown: false,
            ),
          ),
        ),
      ),
    );

    final selectedPill = tester.widget<Container>(
      find.descendant(
        of: find.byKey(const Key('pill-recent')),
        matching: find.byType(Container),
      ),
    );
    final decoration = selectedPill.decoration! as BoxDecoration;
    expect(decoration.border, isA<Border>());
    expect(decoration.border!.top.color.a, closeTo(0.28, 0.01));
  });
}
