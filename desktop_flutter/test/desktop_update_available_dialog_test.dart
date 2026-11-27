import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/desktop_update_available_dialog.dart';
// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'update details dialog displays release notes and returns download',
    (tester) async {
      bool? download;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Builder(
              builder: (context) => FilledButton(
                onPressed: () async {
                  download = await showDesktopUpdateAvailableDialog(
                    context: context,
                    update: const UiptvUpdateInfo(
                      version: '1.2.0',
                      url: 'https://github.com/xixogo5105/uiptv/releases/tag/v1.2.0',
                      description: 'New version notes',
                      isAvailable: true,
                    ),
                    labels: const {
                      'autoUpdateAvailablePrompt': 'Update Available: {0}\\n\\nRelease notes: {1}\\n\\nClick OK to download.',
                      'autoUiptvVersion': 'UIPTV version: {0}',
                      'autoOk': 'Download',
                      'commonClose': 'Close',
                    },
                  );
                },
                child: const Text('Open updates'),
              ),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open updates'));
      await tester.pumpAndSettle();

      expect(find.text('UIPTV version: 1.2.0'), findsOneWidget);
      expect(find.textContaining('New version notes'), findsOneWidget);
      expect(find.byType(Image), findsOneWidget);

      await tester.tap(find.text('Download'));
      await tester.pumpAndSettle();
      expect(download, isTrue);
    },
  );
}
