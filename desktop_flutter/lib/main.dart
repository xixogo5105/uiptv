// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';
import 'package:window_manager/window_manager.dart';

import 'app/uiptv_desktop_app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await windowManager.ensureInitialized();
  windowManager.waitUntilReadyToShow(
    const WindowOptions(
      size: Size(1440, 900),
      minimumSize: Size(640, 640),
      center: true,
      title: 'Favourite - UIPTV',
    ),
    () async {
      await windowManager.maximize();
      await windowManager.show();
      await windowManager.focus();
    },
  );
  MediaKit.ensureInitialized();
  runApp(const UiptvDesktopApp());
}
