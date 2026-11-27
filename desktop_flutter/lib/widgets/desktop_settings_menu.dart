// ignore: deprecated_member_use
import 'package:flutter/material.dart';

enum DesktopSettingsMenuAction {
  settings,
  import,
  cacheReload,
  logs,
  parentalLock,
  plainTextMode,
  epg,
  theme,
  stayOnTop,
  lightweightMode,
  help,
  updates,
  about,
}

class DesktopSettingsMenu extends StatelessWidget {
  const DesktopSettingsMenu({
    super.key,
    required this.labels,
    required this.onSelected,
    required this.pauseFiltering,
    required this.thumbnailsEnabled,
    required this.epgEnabled,
    required this.themeMode,
    required this.stayOnTop,
    this.enabled = true,
  });

  final Map<String, String> labels;
  final ValueChanged<DesktopSettingsMenuAction> onSelected;
  final bool pauseFiltering;
  final bool thumbnailsEnabled;
  final bool epgEnabled;
  final String themeMode;
  final bool stayOnTop;
  final bool enabled;

  String _text(String key, String fallback) => labels[key] ?? fallback;

  String get _themeLabel {
    final key = switch (themeMode) {
      'light' => 'autoEnableDarkTheme',
      'dark' => 'autoUseSystemTheme',
      _ => 'autoDisableDarkTheme',
    };
    return _text(key, 'Change theme');
  }

  Widget _entry(IconData icon, String label) {
    return Row(
      children: [
        Icon(icon),
        const SizedBox(width: 12),
        Flexible(
          child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final gearColor = pauseFiltering
        ? isDark
              ? const Color(0xFFF87171)
              : const Color(0xFFDC2626)
        : isDark
        ? const Color(0xFF22C55E)
        : const Color(0xFF16A34A);
    return PopupMenuButton<DesktopSettingsMenuAction>(
      enabled: enabled,
      tooltip: _text('autoSettings', 'Settings'),
      icon: Icon(Icons.settings, color: gearColor, size: 22),
      onSelected: onSelected,
      itemBuilder: (context) => [
        PopupMenuItem(
          value: DesktopSettingsMenuAction.settings,
          child: _entry(
            Icons.settings_outlined,
            _text('autoSettings', 'Settings'),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.import,
          child: _entry(
            Icons.file_download_outlined,
            _text('autoImportBulkAccounts', 'Import'),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.cacheReload,
          child: _entry(
            Icons.sync,
            _text('autoReloadAccountsCache', 'Reload Accounts Cache'),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.logs,
          child: _entry(Icons.article_outlined, _text('autoLogs', 'Logs')),
        ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.parentalLock,
          child: _entry(
            Icons.lock_outline,
            _text(
              pauseFiltering
                  ? 'autoEnableParentalLockAccess'
                  : 'autoDisableParentalLockAccess',
              'Toggle parental lock access',
            ),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.plainTextMode,
          child: _entry(
            Icons.image_outlined,
            _text(
              thumbnailsEnabled
                  ? 'autoEnablePlainTextMode'
                  : 'autoDisablePlainTextMode',
              'Toggle plain text mode',
            ),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.epg,
          child: _entry(
            Icons.schedule_outlined,
            _text(
              epgEnabled ? 'epgDisableMode' : 'epgEnableMode',
              'Toggle EPG',
            ),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.theme,
          child: _entry(Icons.brightness_6_outlined, _themeLabel),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.stayOnTop,
          child: Semantics(
            checked: stayOnTop,
            child: Row(
              children: [
                Icon(Icons.push_pin_outlined),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    _text('autoStayOnTop', 'Stay on top'),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(width: 8),
                SizedBox(
                  width: 20,
                  child: stayOnTop ? const Icon(Icons.check, size: 18) : null,
                ),
              ],
            ),
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.lightweightMode,
          child: _entry(
            Icons.window_outlined,
            _text(
              'configLightweightModeSwitchTitle',
              'Switch to Lightweight Mode',
            ),
          ),
        ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.help,
          child: _entry(Icons.help_outline, _text('autoHelp', 'Help')),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.updates,
          child: _entry(
            Icons.system_update_alt,
            _text('autoCheckForUpdates2', 'Check for updates'),
          ),
        ),
        PopupMenuItem(
          value: DesktopSettingsMenuAction.about,
          child: _entry(Icons.info_outline, _text('autoAbout', 'About')),
        ),
      ],
    );
  }
}
