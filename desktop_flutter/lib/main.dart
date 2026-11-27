// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:media_kit/media_kit.dart';

import 'app/uiptv_desktop_app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  MediaKit.ensureInitialized();
  runApp(const UiptvDesktopApp());
}
