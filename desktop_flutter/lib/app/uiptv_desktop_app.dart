// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';
import '../screens/uiptv_desktop_shell.dart';

class UiptvDesktopApp extends StatelessWidget {
  const UiptvDesktopApp({super.key});

  @override
  Widget build(BuildContext context) {
    const baseUrl = String.fromEnvironment('UIPTV_API_BASE_URL');
    const token = String.fromEnvironment('UIPTV_API_TOKEN');
    const initialStreamUrl = String.fromEnvironment('UIPTV_TEST_STREAM_URL');
    final api = UiptvApiClient(baseUrl: baseUrl, bearerToken: token);

    return MaterialApp(
      title: 'UIPTV',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.light,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF17456A),
          brightness: Brightness.light,
          surface: const Color(0xFFFFFFFF),
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Color(0xFF111820),
          elevation: 0,
          scrolledUnderElevation: 0,
        ),
        scaffoldBackgroundColor: const Color(0xFFF7FAFD),
        dividerColor: const Color(0xFFE1E6EB),
        cardTheme: const CardThemeData(
          color: Colors.white,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          margin: EdgeInsets.zero,
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: Colors.white,
          contentPadding: const EdgeInsets.symmetric(
            horizontal: 12,
            vertical: 10,
          ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xFFD8DEE4)),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xFFD8DEE4)),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(10),
            borderSide: const BorderSide(color: Color(0xFF17456A)),
          ),
        ),
      ),
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF4FD8EB),
          brightness: Brightness.dark,
        ),
      ),
      home: UiptvDesktopShell(api: api, initialStreamUrl: initialStreamUrl),
    );
  }
}
