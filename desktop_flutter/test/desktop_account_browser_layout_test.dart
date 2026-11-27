// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:desktop_flutter/screens/desktop_account_browser_layout.dart';

void main() {
  test('account browser single-column mode matches JavaFX breakpoint', () {
    expect(DesktopAccountBrowserLayout.usesSingleColumn(1079), isTrue);
    expect(DesktopAccountBrowserLayout.usesSingleColumn(1080), isFalse);
  });

  test('account cards use responsive JavaFX-style column sizing', () {
    final wide = desktopAccountGridDelegate(availableWidth: 1600);
    final narrow = desktopAccountGridDelegate(availableWidth: 700);

    expect(
      (wide as SliverGridDelegateWithFixedCrossAxisCount).crossAxisCount,
      5,
    );
    expect(
      (narrow as SliverGridDelegateWithFixedCrossAxisCount).crossAxisCount,
      2,
    );
  });
  Future<void> setWindowSize(
    WidgetTester tester,
    double width,
    double height,
  ) async {
    tester.view.physicalSize = Size(width, height);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
  }

  Widget buildLayout({
    required bool showEditor,
    bool hasSelectedAccount = true,
    bool hasSelectedCategory = false,
  }) {
    return MaterialApp(
      home: DesktopAccountBrowserLayout(
        showEditor: showEditor,
        hasSelectedAccount: hasSelectedAccount,
        hasSelectedCategory: hasSelectedCategory,
        accountPane: const ColoredBox(
          key: Key('account-pane'),
          color: Colors.blue,
        ),
        catalog: const ColoredBox(
          key: Key('catalog-pane'),
          color: Colors.green,
        ),
        editor: const ColoredBox(key: Key('account-editor'), color: Colors.red),
      ),
    );
  }

  testWidgets(
    'wide account browser shows account list and catalogue side by side',
    (tester) async {
      await setWindowSize(tester, 1600, 900);
      await tester.pumpWidget(buildLayout(showEditor: false));

      expect(find.byKey(const Key('account-pane')), findsOneWidget);
      expect(find.byKey(const Key('catalog-pane')), findsOneWidget);
      expect(
        tester.getTopLeft(find.byKey(const Key('account-pane'))).dx,
        lessThan(tester.getTopLeft(find.byKey(const Key('catalog-pane'))).dx),
      );
    },
  );

  testWidgets('account list fills the view before an account is selected', (
    tester,
  ) async {
    await setWindowSize(tester, 1600, 900);
    await tester.pumpWidget(
      buildLayout(showEditor: false, hasSelectedAccount: false),
    );

    expect(find.byKey(const Key('catalog-pane')), findsNothing);
    expect(
      tester.getRect(find.byKey(const Key('account-pane'))),
      const Rect.fromLTWH(0, 0, 1600, 900),
    );
  });

  testWidgets('channel browsing narrows accounts and expands the last pane', (
    tester,
  ) async {
    await setWindowSize(tester, 1600, 900);
    await tester.pumpWidget(
      buildLayout(
        showEditor: false,
        hasSelectedAccount: true,
        hasSelectedCategory: true,
      ),
    );

    final accounts = tester.getRect(find.byKey(const Key('account-pane')));
    final channels = tester.getRect(find.byKey(const Key('catalog-pane')));
    expect(accounts.width, 260);
    expect(channels.width, greaterThan(accounts.width));
    expect(channels.right, 1600);
  });

  testWidgets('narrow account browser keeps the account list full-height', (
    tester,
  ) async {
    await setWindowSize(tester, 900, 800);
    await tester.pumpWidget(
      buildLayout(showEditor: false, hasSelectedAccount: false),
    );

    expect(find.byKey(const Key('account-pane')), findsOneWidget);
    expect(find.byKey(const Key('catalog-pane')), findsNothing);
    expect(
      tester.getRect(find.byKey(const Key('account-pane'))),
      const Rect.fromLTWH(0, 0, 900, 800),
    );
  });

  testWidgets('narrow category view replaces the account list', (tester) async {
    await setWindowSize(tester, 900, 800);
    await tester.pumpWidget(
      buildLayout(showEditor: false, hasSelectedAccount: true),
    );

    expect(find.byKey(const Key('account-pane')), findsNothing);
    expect(find.byKey(const Key('catalog-pane')), findsOneWidget);
    expect(
      tester.getRect(find.byKey(const Key('catalog-pane'))),
      const Rect.fromLTWH(0, 0, 900, 800),
    );
  });

  testWidgets('narrow channel view replaces the category view', (tester) async {
    await setWindowSize(tester, 900, 800);
    await tester.pumpWidget(
      buildLayout(
        showEditor: false,
        hasSelectedAccount: true,
        hasSelectedCategory: true,
      ),
    );

    expect(find.byKey(const Key('account-pane')), findsNothing);
    expect(find.byKey(const Key('catalog-pane')), findsOneWidget);
    expect(
      tester.getRect(find.byKey(const Key('catalog-pane'))),
      const Rect.fromLTWH(0, 0, 900, 800),
    );
  });

  testWidgets(
    'narrow account editor replaces the panes instead of overflowing',
    (tester) async {
      await setWindowSize(tester, 900, 800);
      await tester.pumpWidget(buildLayout(showEditor: true));

      expect(find.byKey(const Key('account-editor')), findsOneWidget);
      expect(find.byKey(const Key('account-pane')), findsNothing);
      expect(find.byKey(const Key('catalog-pane')), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('wide account editor becomes a third pane when space permits', (
    tester,
  ) async {
    await setWindowSize(tester, 1600, 900);
    await tester.pumpWidget(buildLayout(showEditor: true));

    expect(find.byKey(const Key('account-editor')), findsOneWidget);
    expect(find.byKey(const Key('account-pane')), findsOneWidget);
    expect(find.byKey(const Key('catalog-pane')), findsOneWidget);
  });
}
