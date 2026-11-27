import 'dart:convert';

// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/screens/desktop_account_editor.dart';

void main() {
  testWidgets('account editor creates a provider account at narrow width', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(380, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    var savedAccount = <String, dynamic>{};
    final api = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.method, 'POST');
        expect(request.url.path, '/api/v1/accounts');
        final body = jsonDecode(request.body) as Map<String, dynamic>;
        expect(body['accountName'], 'Adaptive playlist');
        expect(body['type'], 'M3U8_URL');
        expect(body['m3u8Path'], 'https://playlist.example/list.m3u');
        return http.Response(
          jsonEncode({
            'dbId': 'created-row',
            'accountName': 'Adaptive playlist',
            'type': 'M3U8_URL',
          }),
          201,
        );
      }),
    );

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: DesktopAccountEditor(
            api: api,
            translations: const {},
            onSaved: (account) => savedAccount = account,
            onCancel: () {},
          ),
        ),
      ),
    );

    expect(find.text('Account name'), findsOneWidget);
    expect(tester.takeException(), isNull);

    await tester.tap(find.byKey(const ValueKey('account-type')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('M3U8 URL').last);
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const ValueKey('account-accountName')),
      'Adaptive playlist',
    );
    await tester.enterText(
      find.byKey(const ValueKey('account-m3u8Path')),
      'https://playlist.example/list.m3u',
    );
    await tester.tap(find.text('Save'));
    await tester.pumpAndSettle();

    expect(savedAccount['dbId'], 'created-row');
    expect(tester.takeException(), isNull);
  });
}
