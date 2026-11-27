// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/screens/desktop_account_mode_pill_bar.dart';

void main() {
  testWidgets('account mode bar shows every JavaFX mode when space permits', (
    tester,
  ) async {
    var selectedMode = 'itv';

    Widget buildBar(double width) => MaterialApp(
      home: Scaffold(
        body: Center(
          child: SizedBox(
            width: width,
            child: StatefulBuilder(
              builder: (context, setState) => DesktopAccountModePillBar(
                selectedMode: selectedMode,
                supportsVodAndSeries: true,
                fillAvailable: true,
                labels: const {
                  'autoAccount': 'Accounts',
                  'autoTvChannels': 'TV Channels',
                  'autoVod': 'VOD',
                  'autoSeries': 'Series',
                },
                onSelected: (mode) => setState(() => selectedMode = mode),
              ),
            ),
          ),
        ),
      ),
    );

    await tester.pumpWidget(buildBar(500));
    expect(find.byKey(const Key('account-mode-pills')), findsOneWidget);
    expect(find.text('Accounts'), findsOneWidget);
    expect(find.text('TV Channels'), findsOneWidget);
    expect(find.text('VOD'), findsOneWidget);
    expect(find.text('Series'), findsOneWidget);
    await tester.tap(find.byKey(const Key('account-mode-vod')));
    expect(selectedMode, 'vod');

    await tester.pumpWidget(buildBar(190));
    expect(find.byKey(const Key('account-mode-dropdown')), findsOneWidget);
    expect(find.byKey(const Key('account-mode-pills')), findsNothing);
  });
}
