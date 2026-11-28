// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopChannelCardStyle {
  static const selectedBackgroundColor = Color(0xFF101827);
  static const selectedBorderColor = Color(0xFF1F2A44);
  static const selectedForegroundColor = Color(0xFFFFFFFF);
  static const selectedMutedForegroundColor = Color(0xB8FFFFFF);

  static Color background(ColorScheme colorScheme, {required bool selected}) =>
      selected ? selectedBackgroundColor : colorScheme.surface;

  static Color border(ColorScheme colorScheme, {required bool selected}) =>
      selected ? selectedBorderColor : colorScheme.outlineVariant;

  static Color foreground(ColorScheme colorScheme, {required bool selected}) =>
      selected ? selectedForegroundColor : colorScheme.onSurface;

  static Color mutedForeground(
    ColorScheme colorScheme, {
    required bool selected,
  }) => selected ? selectedMutedForegroundColor : colorScheme.onSurfaceVariant;
}
