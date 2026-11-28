// ignore: deprecated_member_use
import 'package:flutter/material.dart';

class DesktopChannelLogo extends StatelessWidget {
  const DesktopChannelLogo({
    super.key,
    required this.logo,
    required this.selected,
    this.size = 48,
  });

  final String logo;
  final bool selected;
  final double size;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final colorScheme = theme.colorScheme;
    final missingImage = logo.trim().isEmpty;
    final background = selected
        ? Colors.white.withValues(alpha: isDark ? 0.08 : 0.14)
        : (isDark ? const Color(0xFF2B2B2B) : const Color(0xFFF2F2F2));
    final border = selected
        ? Colors.white.withValues(alpha: isDark ? 0.16 : 0.25)
        : (isDark ? const Color(0xFF3D3D3D) : const Color(0xFFE0E0E0));
    final iconColor = selected
        ? (isDark ? Colors.black.withValues(alpha: 0.42) : Colors.white70)
        : colorScheme.onSurfaceVariant.withValues(alpha: 0.78);

    Widget fallback() => SizedBox(
      width: size - 12,
      height: size - 12,
      child: CustomPaint(painter: _DefaultChannelIconPainter(iconColor)),
    );

    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: missingImage ? background : Colors.transparent,
        border: missingImage ? Border.all(color: border) : null,
        borderRadius: BorderRadius.circular(12),
      ),
      child: missingImage
          ? fallback()
          : Image.network(
              logo,
              width: size - 4,
              height: size - 4,
              fit: BoxFit.contain,
              errorBuilder: (_, _, _) => Container(
                width: size,
                height: size,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  color: background,
                  border: Border.all(color: border),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: fallback(),
              ),
            ),
    );
  }
}

class _DefaultChannelIconPainter extends CustomPainter {
  const _DefaultChannelIconPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.width / 36;
    canvas.save();
    canvas.scale(scale, size.height / 36);
    final stroke = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.8
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    final fill = Paint()
      ..color = color
      ..style = PaintingStyle.fill;
    final antenna = Path()
      ..moveTo(11, 3.5)
      ..lineTo(18, 10)
      ..lineTo(25, 3.5);
    canvas.drawPath(antenna, stroke);
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(3.5, 9.5, 29, 23),
        const Radius.circular(3.5),
      ),
      stroke,
    );
    canvas.drawRRect(
      RRect.fromRectAndRadius(
        const Rect.fromLTWH(7, 13, 22, 16),
        const Radius.circular(1.5),
      ),
      stroke,
    );
    final play = Path()
      ..moveTo(15, 16)
      ..lineTo(23, 21)
      ..lineTo(15, 26)
      ..close();
    canvas.drawPath(play, fill);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_DefaultChannelIconPainter oldDelegate) =>
      oldDelegate.color != color;
}
