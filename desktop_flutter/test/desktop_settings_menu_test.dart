import 'package:desktop_flutter/widgets/desktop_settings_menu.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('settings menu exposes the JavaFX gear actions', (tester) async {
    final labels = {
      'autoSettings': 'Settings',
      'autoImportBulkAccounts': 'Import Bulk Accounts',
      'autoReloadAccountsCache': 'Reload Accounts Cache',
      'autoLogs': 'Logs',
      'autoDisableParentalLockAccess': 'Disable parental lock access',
      'autoEnablePlainTextMode': 'Switch to plain text mode',
      'epgDisableMode': 'Disable EPG mode',
      'autoEnableDarkTheme': 'Switch to dark theme',
      'autoStayOnTop': 'Stay on top',
      'configLightweightModeSwitchTitle': 'Switch to Lightweight Mode',
      'autoHelp': 'Help',
      'autoCheckForUpdates2': 'Check for updates...',
      'autoAbout': 'About',
    };

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          appBar: AppBar(
            actions: [
              DesktopSettingsMenu(
                labels: labels,
                onSelected: (_) {},
                pauseFiltering: false,
                thumbnailsEnabled: true,
                epgEnabled: true,
                themeMode: 'light',
                stayOnTop: false,
              ),
            ],
          ),
        ),
      ),
    );

    final gear = tester.widget<Icon>(find.byIcon(Icons.settings));
    expect(gear.color, const Color(0xFF16A34A));

    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();

    for (final label in labels.values) {
      expect(find.text(label), findsOneWidget, reason: label);
    }
  });
}
