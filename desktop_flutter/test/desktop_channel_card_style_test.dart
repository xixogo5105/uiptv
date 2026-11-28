import 'package:desktop_flutter/screens/desktop_channel_card_style.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('selected channel styling matches JavaFX in both themes', () {
    for (final brightness in Brightness.values) {
      final colorScheme = ColorScheme.fromSeed(
        seedColor: Colors.blue,
        brightness: brightness,
      );

      expect(
        DesktopChannelCardStyle.background(colorScheme, selected: true),
        const Color(0xFF101827),
      );
      expect(
        DesktopChannelCardStyle.border(colorScheme, selected: true),
        const Color(0xFF1F2A44),
      );
      expect(
        DesktopChannelCardStyle.foreground(colorScheme, selected: true),
        Colors.white,
      );
      expect(
        DesktopChannelCardStyle.mutedForeground(colorScheme, selected: true),
        const Color(0xB8FFFFFF),
      );
    }
  });
}
