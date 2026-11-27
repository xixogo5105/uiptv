// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

class DesktopAccountCard extends StatefulWidget {
  const DesktopAccountCard({
    super.key,
    required this.account,
    required this.selected,
    required this.onTap,
    required this.onMenuSelected,
    required this.menuItems,
    required this.categoriesLabel,
    required this.channelsLabel,
    required this.manageLabel,
  });

  final UiptvAccountSummary account;
  final bool selected;
  final VoidCallback onTap;
  final ValueChanged<String> onMenuSelected;
  final List<PopupMenuEntry<String>> menuItems;
  final String categoriesLabel;
  final String channelsLabel;
  final String manageLabel;

  @override
  State<DesktopAccountCard> createState() => _DesktopAccountCardState();
}

class _DesktopAccountCardState extends State<DesktopAccountCard> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final account = widget.account;
    final metrics = <String>[];
    if (account.categoryCount > 1) {
      final count = account.categoryCountDisplay.isEmpty
          ? account.categoryCount.toString()
          : account.categoryCountDisplay;
      metrics.add('$count ${widget.categoriesLabel}');
    }
    if (account.channelCount > 0) {
      final count = account.channelCountDisplay.isEmpty
          ? account.channelCount.toString()
          : account.channelCountDisplay;
      metrics.add('$count ${widget.channelsLabel}');
    }
    final borderColor = widget.selected
        ? colorScheme.primary.withValues(alpha: 0.62)
        : colorScheme.outlineVariant.withValues(alpha: 0.55);

    return MouseRegion(
      key: Key('account-card-${account.dbId}'),
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: Card(
        color: widget.selected
            ? colorScheme.secondaryContainer.withValues(alpha: 0.88)
            : null,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(14),
          side: BorderSide(color: borderColor),
        ),
        child: InkWell(
          onTap: widget.onTap,
          borderRadius: BorderRadius.circular(14),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (account.pinToTop &&
                        account.pinSvgStemPath.isNotEmpty &&
                        account.pinSvgHeadPath.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(right: 8, top: 1),
                        child: Semantics(
                          label: 'Pinned account',
                          child: CustomPaint(
                            key: const Key('account-pin'),
                            size: const Size(22, 22),
                            painter: _AccountPinPainter(account),
                          ),
                        ),
                      ),
                    Expanded(
                      child: Text(
                        account.accountName,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: 14,
                          height: 1.18,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ),
                    const SizedBox(width: 4),
                    AnimatedOpacity(
                      key: const Key('account-menu-opacity'),
                      opacity: _hovered ? 1 : 0,
                      duration: const Duration(milliseconds: 100),
                      child: IgnorePointer(
                        ignoring: !_hovered,
                        child: SizedBox(
                          width: 28,
                          height: 28,
                          child: PopupMenuButton<String>(
                            tooltip: widget.manageLabel,
                            padding: EdgeInsets.zero,
                            iconSize: 20,
                            icon: const Icon(Icons.more_horiz),
                            onSelected: widget.onMenuSelected,
                            itemBuilder: (_) => widget.menuItems,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 7),
                _buildTypeAndExpiry(account, colorScheme),
                if (metrics.isNotEmpty) ...[
                  const SizedBox(height: 7),
                  Text(
                    metrics.join(' / '),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontSize: 12,
                      height: 1.2,
                      color: widget.selected
                          ? colorScheme.onSecondaryContainer.withValues(
                              alpha: 0.78,
                            )
                          : colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTypeAndExpiry(
    UiptvAccountSummary account,
    ColorScheme colorScheme,
  ) {
    final type = account.typeDisplay.isNotEmpty
        ? account.typeDisplay
        : switch (account.type) {
            'STALKER_PORTAL' => 'Stalker Portal',
            'M3U8_LOCAL' => 'M3U8 Local',
            'M3U8_URL' => 'M3U8 URL',
            'XTREME_API' => 'Xtreme API',
            _ => account.type.replaceAll('_', ' '),
          };
    final expiryColor = switch (account.expiryState) {
      'ok' => const Color(0xFF2E7D32),
      'warning' => const Color(0xFFF9A825),
      'expired' => const Color(0xFFC62828),
      _ => colorScheme.onSurfaceVariant,
    };
    final foreground = widget.selected
        ? colorScheme.onSecondaryContainer.withValues(alpha: 0.78)
        : colorScheme.onSurfaceVariant;
    return RichText(
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      text: TextSpan(
        style: DefaultTextStyle.of(context).style
            .copyWith(fontSize: 13, height: 1.2, color: foreground),
        children: [
          TextSpan(text: type),
          if (account.expiryDate.isNotEmpty)
            TextSpan(
              text: ' (${account.expiryDate})',
              style: TextStyle(color: expiryColor, fontWeight: FontWeight.w700),
            ),
        ],
      ),
    );
  }
}

class _AccountPinPainter extends CustomPainter {
  _AccountPinPainter(this.account);

  final UiptvAccountSummary account;

  @override
  void paint(Canvas canvas, Size size) {
    final stem = _parsePath(account.pinSvgStemPath);
    final head = _parsePath(account.pinSvgHeadPath);
    canvas.save();
    canvas.scale(account.pinSvgScale);
    canvas.drawPath(stem, Paint()..color = _parseColor(account.pinSvgStemFill));
    canvas.drawPath(head, Paint()..color = _parseColor(account.pinSvgHeadFill));
    canvas.restore();
  }

  @override
  bool shouldRepaint(_AccountPinPainter oldDelegate) =>
      account.pinSvgStemPath != oldDelegate.account.pinSvgStemPath ||
      account.pinSvgHeadPath != oldDelegate.account.pinSvgHeadPath ||
      account.pinSvgStemFill != oldDelegate.account.pinSvgStemFill ||
      account.pinSvgHeadFill != oldDelegate.account.pinSvgHeadFill ||
      account.pinSvgScale != oldDelegate.account.pinSvgScale;

  Path _parsePath(String data) {
    final tokens = RegExp(
      r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?',
    ).allMatches(data).map((match) => match.group(0)!).toList();
    final path = Path();
    var index = 0;
    var command = '';
    var x = 0.0;
    var y = 0.0;
    var startX = 0.0;
    var startY = 0.0;
    double readNumber() => double.parse(tokens[index++]);

    while (index < tokens.length) {
      if (RegExp(r'^[A-Za-z]$').hasMatch(tokens[index])) {
        command = tokens[index++];
      }
      switch (command) {
        case 'm':
        case 'M':
          final nextX = readNumber();
          final nextY = readNumber();
          x = command == 'm' ? x + nextX : nextX;
          y = command == 'm' ? y + nextY : nextY;
          startX = x;
          startY = y;
          path.moveTo(x, y);
          command = command == 'm' ? 'l' : 'L';
          break;
        case 'l':
        case 'L':
          final nextX = readNumber();
          final nextY = readNumber();
          x = command == 'l' ? x + nextX : nextX;
          y = command == 'l' ? y + nextY : nextY;
          path.lineTo(x, y);
          break;
        case 'c':
        case 'C':
          if (index + 5 >= tokens.length ||
              RegExp(r'^[A-Za-z]$').hasMatch(tokens[index])) {
            command = '';
            break;
          }
          final values = List<double>.generate(6, (_) => readNumber());
          final relative = command == 'c';
          final control1X = relative ? x + values[0] : values[0];
          final control1Y = relative ? y + values[1] : values[1];
          final control2X = relative ? x + values[2] : values[2];
          final control2Y = relative ? y + values[3] : values[3];
          final endX = relative ? x + values[4] : values[4];
          final endY = relative ? y + values[5] : values[5];
          path.cubicTo(control1X, control1Y, control2X, control2Y, endX, endY);
          x = endX;
          y = endY;
          break;
        case 'z':
        case 'Z':
          path.close();
          x = startX;
          y = startY;
          command = '';
          break;
        default:
          command = '';
          break;
      }
    }
    return path;
  }

  Color _parseColor(String value) {
    final hex = value.replaceFirst('#', '');
    if (hex.length == 6) {
      return Color(0xFF000000 | int.parse(hex, radix: 16));
    }
    if (hex.length == 8) {
      return Color(int.parse(hex, radix: 16));
    }
    return const Color(0xFFCAD2D2);
  }
}
