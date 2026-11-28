// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/desktop_favourites_layout.dart';

void main() {
  testWidgets('category pills become a dropdown when they cannot fit', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 220,
            child: DesktopBookmarkCategorySelector(
              categories: _categories,
              selectedCategoryId: 'recent',
              onSelected: (_) {},
            ),
          ),
        ),
      ),
    );

    expect(find.byType(PopupMenuButton<String>), findsOneWidget);
    expect(find.byType(ChoiceChip), findsNothing);
  });

  testWidgets('category pills stay visible when there is room', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 900,
            child: DesktopBookmarkCategorySelector(
              categories: _categories,
              selectedCategoryId: 'recent',
              onSelected: (_) {},
            ),
          ),
        ),
      ),
    );

    expect(find.byType(PopupMenuButton<String>), findsNothing);
    expect(find.byType(ChoiceChip), findsNothing);
    expect(find.byKey(const Key('bookmark-category-pill-bar')), findsOneWidget);
  });

  testWidgets('compact category menu has a contained selected row', (
    tester,
  ) async {
    String? selected;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: SizedBox(
            width: 220,
            child: DesktopBookmarkCategorySelector(
              categories: _categories,
              selectedCategoryId: 'recent',
              onSelected: (category) => selected = category.id,
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.byKey(const Key('bookmark-category-pill-dropdown')));
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('bookmark-category-pill-dropdown-option-recent')),
      findsOneWidget,
    );
    expect(find.byIcon(Icons.check), findsOneWidget);
    await tester.tap(find.text('Cricket').last);
    expect(selected, 'cricket');
  });

  test('favourites grid wraps multiple cards in wide view', () {
    final delegate = desktopBookmarkGridDelegate(availableWidth: 1600);

    expect(delegate, isA<SliverGridDelegateWithFixedCrossAxisCount>());
    expect(
      (delegate as SliverGridDelegateWithFixedCrossAxisCount).crossAxisCount,
      5,
    );
  });

  test('favourites grid retains one column when only one card fits', () {
    final delegate = desktopBookmarkGridDelegate(availableWidth: 500);

    expect(
      (delegate as SliverGridDelegateWithFixedCrossAxisCount).crossAxisCount,
      1,
    );
  });
}

const _categories = [
  UiptvBookmarkCategory(id: 'recent', name: 'Recent'),
  UiptvBookmarkCategory(id: 'cricket', name: 'Cricket'),
  UiptvBookmarkCategory(id: 'movies', name: 'Movies'),
  UiptvBookmarkCategory(id: 'drama', name: 'Drama'),
];
