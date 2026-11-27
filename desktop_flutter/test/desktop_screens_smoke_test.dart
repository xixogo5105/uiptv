import 'dart:convert';

import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/bulk_account_import_screen.dart';
import 'package:desktop_flutter/screens/desktop_about_dialog.dart';
import 'package:desktop_flutter/screens/desktop_cache_reload_screen.dart';
import 'package:desktop_flutter/screens/desktop_logs_screen.dart';
import 'package:desktop_flutter/screens/desktop_settings_screen.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

void main() {
  testWidgets('bulk account import screen renders source and data controls', (
    tester,
  ) async {
    await _pumpScreen(tester, BulkAccountImportScreen(api: _api()));

    expect(find.text('Stalker Portal'), findsOneWidget);
    expect(find.text('M3U Playlists'), findsOneWidget);
  });

  testWidgets('cache reload screen renders cache-supported account selection', (
    tester,
  ) async {
    await _pumpScreen(tester, DesktopCacheReloadScreen(api: _api()));

    expect(find.text('Reload Accounts Cache'), findsOneWidget);
    expect(find.text('Stalker Portal Accounts'), findsOneWidget);
  });

  testWidgets('settings screen renders responsive settings cards', (
    tester,
  ) async {
    await _pumpScreen(tester, DesktopSettingsScreen(api: _api()));

    expect(find.text('Video Players'), findsWidgets);
    expect(find.text('Backup & Restore'), findsWidgets);
    expect(find.text('Internal Web Server'), findsWidgets);
  });

  testWidgets('logs screen renders app log actions', (tester) async {
    await _pumpScreen(tester, DesktopLogsScreen(api: _api()));

    expect(find.text('UIPTV Logs'), findsOneWidget);
    expect(find.text('Web Logs'), findsOneWidget);
    expect(find.text('Detach'), findsOneWidget);
  });

  testWidgets('about dialog renders runtime metadata', (tester) async {
    await _pumpScreen(tester, DesktopAboutDialog(api: _api()));

    expect(find.text('DESKTOP APP'), findsOneWidget);
    expect(find.text('UIPTV 1.1.5'), findsOneWidget);
    expect(find.text('25 (OpenJDK)'), findsOneWidget);
  });
}

Future<void> _pumpScreen(WidgetTester tester, Widget screen) async {
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(body: SizedBox.expand(child: screen)),
    ),
  );
  await tester.pumpAndSettle();
}

UiptvApiClient _api() {
  return UiptvApiClient(
    baseUrl: 'http://127.0.0.1:8888',
    bearerToken: 'headless-test-token',
    httpClient: MockClient((request) async {
      final path = request.url.path;
      if (path == '/api/v1/translations') {
        final keys =
            request.url.queryParameters['keys']?.split(',') ?? const [];
        final labels = {for (final key in keys) key: key};
        labels.addAll({
          'autoReloadAccountsCache': 'Reload Accounts Cache',
          'reloadStalkerPortalAccounts': 'Stalker Portal Accounts',
          'autoUiptvLogs': 'UIPTV Logs',
          'autoWebLogs': 'Web Logs',
          'autoDetach': 'Detach',
          'autoAboutUiptvTitle': 'UIPTV {0}',
          'autoAboutUiptvRuntime': '{0} ({1})',
          'configVideoPlayers': 'Video Players',
          'configDatabaseSyncTitle': 'Backup & Restore',
          'configWebServer': 'Internal Web Server',
        });
        return http.Response(jsonEncode(labels), 200);
      }
      if (path == '/api/v1/cache-reloads/accounts') {
        return http.Response(
          jsonEncode([
            {
              'dbId': 'account-1',
              'accountName': 'Main account',
              'type': 'STALKER_PORTAL',
              'pinToTop': false,
              'cacheEnabled': true,
              'webPlaybackProxy': false,
            },
          ]),
          200,
        );
      }
      if (path == '/api/v1/settings') {
        return http.Response(
          jsonEncode({
            'playerPath1': '',
            'playerPath2': '',
            'playerPath3': '',
            'defaultPlayerPath': '__embedded_player__',
            'embeddedPlayerPath': '__embedded_player__',
            'webBrowserPlayerPath': '__web_browser_player__',
            'embeddedPlayer': true,
            'wideView': false,
            'resolveChainAndDeepRedirects': false,
            'enableThumbnails': true,
            'showBookmarkAndAccountCounts': false,
            'epgEnabled': true,
            'themeMode': 'system',
            'languageLocale': 'en-US',
            'languageOptions': [
              {'tag': 'en-US', 'name': 'English (United States)'},
            ],
            'uiZoomPercent': 100,
            'uiZoomOptions': [75, 100, 125],
            'cacheExpiryDays': 30,
            'serverPort': '8080',
            'httpsServerPort': '8443',
            'httpsServerEnabled': false,
            'autoRunServerOnStartup': false,
            'webPlaybackProxyEnabled': false,
            'tmdbTokenConfigured': false,
            'filterLockConfigured': false,
            'restartRequired': false,
          }),
          200,
        );
      }
      if (path == '/api/v1/settings/server') {
        return http.Response(
          jsonEncode({
            'running': false,
            'httpUrl': 'http://127.0.0.1:8080',
            'httpsUrl': '',
          }),
          200,
        );
      }
      if (path == '/api/v1/filter-lock') {
        return http.Response(
          jsonEncode({
            'passwordConfigured': false,
            'unlocked': true,
            'unlockWindowMinutes': 10,
            'minimumPasswordLength': 6,
          }),
          200,
        );
      }
      if (path == '/api/v1/filter-lock/filters') {
        return http.Response(
          jsonEncode({
            'filterCategoriesList': '',
            'filterChannelsList': '',
            'restrictionsActive': true,
          }),
          200,
        );
      }
      if (path == '/api/v1/about') {
        return http.Response(
          jsonEncode({
            'version': '1.1.5',
            'javaVersion': '25',
            'vmName': 'OpenJDK',
            'osName': 'macOS',
            'osArch': 'aarch64',
            'projectUrl': 'https://github.com/xixogo5105/uiptv',
            'helpUrl': 'https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md',
            'releaseUrl': 'https://github.com/xixogo5105/uiptv/releases/latest',
          }),
          200,
        );
      }
      if (path == '/api/v1/logs') {
        return http.Response(jsonEncode({'lines': []}), 200);
      }
      return http.Response(jsonEncode({'error': 'Not found'}), 404);
    }),
  );
}
