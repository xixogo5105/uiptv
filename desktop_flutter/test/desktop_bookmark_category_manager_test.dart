// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/desktop_bookmark_category_manager.dart';

void main() {
  testWidgets('category manager selects before removing and adds categories', (
    tester,
  ) async {
    var categories = <UiptvBookmarkCategory>[
      const UiptvBookmarkCategory(id: 'sports', name: 'Sports'),
      const UiptvBookmarkCategory(id: 'movies', name: 'Movies'),
    ];
    String? removedId;

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DesktopBookmarkCategoryManagerDialog(
            categories: categories,
            title: 'Manage Categories',
            categoriesLabel: 'Categories',
            categoryLabel: 'Category Name',
            addLabel: 'Add',
            removeLabel: 'Remove',
            closeLabel: 'Close',
            emptyLabel: 'No categories found',
            onAdd: (name) async {
              categories = [
                ...categories,
                UiptvBookmarkCategory(id: name.toLowerCase(), name: name),
              ];
              return categories;
            },
            onRemove: (id) async {
              removedId = id;
              categories = categories.where((item) => item.id != id).toList();
              return categories;
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final removeButton = tester.widget<FilledButton>(
      find.byKey(const ValueKey('bookmark-category-remove')),
    );
    expect(removeButton.onPressed, isNull);
    expect(find.text('Categories'), findsNWidgets(2));

    await tester.tap(find.byKey(const ValueKey('bookmark-category-sports')));
    await tester.pumpAndSettle();
    expect(
      tester
          .widget<FilledButton>(
            find.byKey(const ValueKey('bookmark-category-remove')),
          )
          .onPressed,
      isNotNull,
    );
    await tester.tap(find.byKey(const ValueKey('bookmark-category-remove')));
    await tester.pumpAndSettle();
    expect(removedId, 'sports');
    expect(find.text('Sports'), findsNothing);

    await tester.enterText(
      find.byKey(const ValueKey('bookmark-category-name')),
      'News',
    );
    await tester.tap(find.byKey(const ValueKey('bookmark-category-add')));
    await tester.pumpAndSettle();
    expect(find.text('News'), findsOneWidget);
  });

  testWidgets('category manager adapts to a narrow viewport', (tester) async {
    tester.view.physicalSize = const Size(360, 480);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DesktopBookmarkCategoryManagerDialog(
            categories: const [],
            title: 'Manage Categories',
            categoriesLabel: 'Categories',
            categoryLabel: 'Category Name',
            addLabel: 'Add',
            removeLabel: 'Remove',
            closeLabel: 'Close',
            emptyLabel: 'No categories found',
            onAdd: (_) async => const [],
            onRemove: (_) async => const [],
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('No categories found'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
