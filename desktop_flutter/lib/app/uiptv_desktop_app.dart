// ignore: deprecated_member_use
import 'package:flutter/foundation.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';
import '../screens/uiptv_desktop_shell.dart';

ThemeData buildUiptvTheme(Brightness brightness) {
  final isDark = brightness == Brightness.dark;
  final colorScheme = ColorScheme.fromSeed(
    seedColor: isDark ? const Color(0xFF4FD8EB) : const Color(0xFF17456A),
    brightness: brightness,
    surface: isDark ? const Color(0xFF1B1F23) : Colors.white,
  );
  final borderColor = isDark
      ? const Color(0xFF3B4248)
      : const Color(0xFFD8DEE4);
  final glassSurface = colorScheme.surface.withValues(
    alpha: isDark ? 0.88 : 0.84,
  );
  final glassBorder = borderColor.withValues(alpha: isDark ? 0.72 : 0.68);
  return ThemeData(
    useMaterial3: true,
    fontFamily: defaultTargetPlatform == TargetPlatform.macOS
        ? 'CupertinoSystemText'
        : 'Roboto',
    brightness: brightness,
    colorScheme: colorScheme,
    appBarTheme: AppBarTheme(
      backgroundColor:
          (isDark ? const Color(0xFF171D23) : const Color(0xFFF9FCFF))
              .withValues(alpha: 0.90),
      foregroundColor: isDark
          ? const Color(0xFFE7EAED)
          : const Color(0xFF111820),
      elevation: 0,
      scrolledUnderElevation: 0,
      surfaceTintColor: Colors.transparent,
    ),
    scaffoldBackgroundColor: isDark
        ? const Color(0xFF11161B)
        : const Color(0xFFF3F7FC),
    dividerColor: isDark ? const Color(0xFF363B40) : const Color(0xFFE1E6EB),
    cardTheme: CardThemeData(
      color: glassSurface,
      surfaceTintColor: Colors.transparent,
      elevation: 0,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
      shape: RoundedRectangleBorder(
        side: BorderSide(color: glassBorder),
        borderRadius: BorderRadius.circular(14),
      ),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: glassSurface,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        side: BorderSide(color: glassBorder),
        borderRadius: BorderRadius.circular(18),
      ),
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: (isDark ? const Color(0xFF252C33) : Colors.white).withValues(
        alpha: 0.90,
      ),
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: borderColor),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: borderColor),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(10),
        borderSide: BorderSide(color: colorScheme.primary),
      ),
    ),
  );
}

class UiptvDesktopApp extends StatefulWidget {
  const UiptvDesktopApp({super.key});

  @override
  State<UiptvDesktopApp> createState() => _UiptvDesktopAppState();
}

class _UiptvDesktopAppState extends State<UiptvDesktopApp> {
  late final UiptvApiClient _api;
  ThemeMode _themeMode = ThemeMode.system;

  @override
  void initState() {
    super.initState();
    const baseUrl = String.fromEnvironment('UIPTV_API_BASE_URL');
    const token = String.fromEnvironment('UIPTV_API_TOKEN');
    _api = UiptvApiClient(baseUrl: baseUrl, bearerToken: token);
    _loadThemeMode();
  }

  Future<void> _loadThemeMode() async {
    try {
      final settings = await _api.settings();
      if (mounted) {
        _applyThemeMode(settings['themeMode'] as String? ?? 'system');
      }
    } catch (_) {
      return;
    }
  }

  void _applyThemeMode(String value) {
    setState(() {
      _themeMode = switch (value) {
        'light' => ThemeMode.light,
        'dark' => ThemeMode.dark,
        _ => ThemeMode.system,
      };
    });
  }

  @override
  Widget build(BuildContext context) {
    const initialStreamUrl = String.fromEnvironment('UIPTV_TEST_STREAM_URL');
    return MaterialApp(
      title: 'UIPTV',
      debugShowCheckedModeBanner: false,
      themeMode: _themeMode,
      theme: buildUiptvTheme(Brightness.light),
      darkTheme: buildUiptvTheme(Brightness.dark),
      home: UiptvDesktopShell(
        api: _api,
        initialStreamUrl: initialStreamUrl,
        onThemeModeChanged: _applyThemeMode,
      ),
    );
  }
}
