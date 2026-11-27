// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:desktop_flutter/screens/desktop_responsive_card_grid.dart';

void main() {
  test('channel cards always use a single column', () {
    final delegate = desktopChannelGridDelegate();

    expect(
      (delegate as SliverGridDelegateWithFixedCrossAxisCount).crossAxisCount,
      1,
    );
  });
}
