// ignore: deprecated_member_use
import 'package:flutter/foundation.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/app/uiptv_desktop_app.dart';

void main() {
  test('dark theme uses translucent glass surfaces and readable controls', () {
    debugDefaultTargetPlatformOverride = TargetPlatform.macOS;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final theme = buildUiptvTheme(Brightness.dark);

    expect(theme.brightness, Brightness.dark);
    expect(theme.textTheme.bodyMedium?.fontFamily, 'CupertinoSystemText');
    expect(theme.scaffoldBackgroundColor, const Color(0xFF11161B));
    expect(theme.appBarTheme.backgroundColor!.a, lessThan(1));
    expect(theme.cardTheme.color!.a, lessThan(1));
    expect(theme.inputDecorationTheme.fillColor!.a, lessThan(1));
    expect(theme.dialogTheme.shape, isA<RoundedRectangleBorder>());
    expect(theme.colorScheme.onSurface, isNot(Colors.black));
  });

  test('light theme uses translucent glass surfaces and readable controls', () {
    debugDefaultTargetPlatformOverride = TargetPlatform.macOS;
    addTearDown(() => debugDefaultTargetPlatformOverride = null);
    final theme = buildUiptvTheme(Brightness.light);

    expect(theme.brightness, Brightness.light);
    expect(theme.textTheme.bodyMedium?.fontFamily, 'CupertinoSystemText');
    expect(theme.appBarTheme.backgroundColor!.a, lessThan(1));
    expect(theme.cardTheme.color!.a, lessThan(1));
    expect(theme.inputDecorationTheme.fillColor!.a, lessThan(1));
    expect(theme.colorScheme.onSurface, isNot(Colors.white));
  });
}
