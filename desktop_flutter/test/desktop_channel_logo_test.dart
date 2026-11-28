// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:desktop_flutter/widgets/desktop_channel_logo.dart';

void main() {
  testWidgets('missing channel logos use the JavaFX television fallback', (
    tester,
  ) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(body: DesktopChannelLogo(logo: '', selected: false)),
      ),
    );

    expect(find.byType(CustomPaint), findsWidgets);
    expect(find.byIcon(Icons.live_tv_outlined), findsNothing);
  });
}
