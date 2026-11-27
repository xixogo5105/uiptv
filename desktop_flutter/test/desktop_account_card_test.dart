import 'package:flutter/gestures.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/desktop_account_card.dart';

void main() {
  testWidgets('account card matches JavaFX summary and hover affordance', (
    tester,
  ) async {
    final account = UiptvAccountSummary(
      dbId: 'account-1',
      accountName: 'wtc05.mi20.cc (uttapulta 22-02-2202)',
      type: 'STALKER_PORTAL',
      typeDisplay: 'Stalker Portal',
      pinToTop: true,
      cacheEnabled: true,
      webPlaybackProxy: false,
      categoryCount: 58,
      categoryCountDisplay: '58',
      channelCount: 1729,
      channelCountDisplay: '1,729',
      expiryDate: '14 Apr 27',
      expiryState: 'ok',
      pinSvgStemPath: 'm 289.99122,309.99418 c -0.66028,0.58344 -50.08221,-43.19021 -52.50936,-45.29992 -2.42734,-2.10956 -51.06934,-43.57426 -52.83626,-46.26739 -1.76673,-2.69328 13.04928,-12.78624 13.70956,-13.36969 0.66024,-0.58341 12.52054,-14.06148 14.94736,-11.95215 2.42733,2.10957 37.03325,55.97684 38.80018,58.66996 1.76673,2.69328 38.54876,57.6358 37.88852,58.21919 z',
      pinSvgHeadPath: 'm 56.34936,106.22036 c 20.30938,0.88278 45.68909,32.12704 73.173,75.95489 18.76942,29.93108 45.31357,11.58173 54.19751,2.7927 8.31501,-8.2259 25.42173,-32.179 -3.72915,-51.99008 -42.68539,-29.00919 -72.93354,-55.50764 -73.173,-75.954905 L 81.58356,81.621661 Z',
      pinSvgStemFill: '#cad2d2',
      pinSvgHeadFill: '#e30000',
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: Center(
            child: SizedBox(
              width: 340,
              height: 140,
              child: DesktopAccountCard(
                account: account,
                selected: false,
                onTap: () {},
                onMenuSelected: (_) {},
                menuItems: const <PopupMenuEntry<String>>[],
                categoriesLabel: 'Categories',
                channelsLabel: 'Channels',
                manageLabel: 'Manage',
              ),
            ),
          ),
        ),
      ),
    );

    expect(find.text(account.accountName), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is RichText &&
            widget.text.toPlainText() == 'Stalker Portal (14 Apr 27)',
      ),
      findsOneWidget,
    );
    expect(find.text('58 Categories / 1,729 Channels'), findsOneWidget);
    expect(find.byKey(const Key('account-pin')), findsOneWidget);
    expect(find.byIcon(Icons.live_tv_outlined), findsNothing);
    expect(find.byIcon(Icons.push_pin), findsNothing);
    expect(
      tester
          .widget<AnimatedOpacity>(
            find.byKey(const Key('account-menu-opacity')),
          )
          .opacity,
      0,
    );

    final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
    final card = find.byKey(const Key('account-card-account-1'));
    await mouse.addPointer(location: tester.getCenter(card));
    await mouse.moveTo(tester.getCenter(card));
    await tester.pump(const Duration(milliseconds: 120));

    expect(
      tester
          .widget<AnimatedOpacity>(
            find.byKey(const Key('account-menu-opacity')),
          )
          .opacity,
      1,
    );
  });
}
