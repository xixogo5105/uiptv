import 'dart:io';

// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

class DesktopAboutDialog extends StatefulWidget {
  const DesktopAboutDialog({super.key, required this.api});

  final UiptvApiClient api;

  @override
  State<DesktopAboutDialog> createState() => _DesktopAboutDialogState();
}

class _DesktopAboutDialogState extends State<DesktopAboutDialog> {
  static const _translationKeys = [
    'autoAboutUIPTV',
    'autoAboutUiptvTitle',
    'autoAboutUiptvTagline',
    'autoAboutUiptvOverview',
    'autoAboutUiptvWebSync',
    'autoAboutUiptvReleaseNotes',
    'autoAuthorXixogo5105',
    'autoAboutUiptvRuntime',
    'autoAboutUiptvPlatform',
    'autoAboutUiptvPoweredBy',
    'autoCopyright2024Xixogo5105',
    'autoHttpsGithubComXixogo5105Uiptv',
    'autoHelp',
    'autoClose',
    'autoCheckForUpdates',
  ];

  late Future<Map<String, dynamic>> _about;
  late Future<Map<String, String>> _translations;

  @override
  void initState() {
    super.initState();
    _about = widget.api.about();
    _translations = widget.api.translations(_translationKeys);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, dynamic>>(
      future: _about,
      builder: (context, aboutSnapshot) {
        if (aboutSnapshot.connectionState == ConnectionState.waiting) {
          return const Dialog(
            child: SizedBox(
              width: 360,
              height: 180,
              child: Center(child: CircularProgressIndicator()),
            ),
          );
        }
        if (aboutSnapshot.hasError) {
          return AlertDialog(
            title: const Text('UIPTV'),
            content: Text(aboutSnapshot.error.toString()),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Close'),
              ),
            ],
          );
        }
        return FutureBuilder<Map<String, String>>(
          future: _translations,
          builder: (context, translationSnapshot) {
            if (translationSnapshot.connectionState ==
                ConnectionState.waiting) {
              return const Dialog(
                child: SizedBox(
                  width: 360,
                  height: 180,
                  child: Center(child: CircularProgressIndicator()),
                ),
              );
            }
            if (translationSnapshot.hasError) {
              return AlertDialog(
                title: const Text('UIPTV'),
                content: Text(translationSnapshot.error.toString()),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('Close'),
                  ),
                ],
              );
            }
            return _aboutCard(
              aboutSnapshot.data ?? const {},
              translationSnapshot.data ?? const {},
            );
          },
        );
      },
    );
  }

  Widget _aboutCard(Map<String, dynamic> about, Map<String, String> labels) {
    final screenSize = MediaQuery.sizeOf(context);
    final dialogWidth = (screenSize.width * 0.9).clamp(280.0, 720.0).toDouble();
    final dialogHeight = (screenSize.height * 0.86)
        .clamp(360.0, 760.0)
        .toDouble();
    final version = about['version'] as String? ?? 'N/A';
    final projectUrl = about['projectUrl'] as String? ?? '';
    final helpUrl = about['helpUrl'] as String? ?? '';
    final releaseUrl = about['releaseUrl'] as String? ?? '';
    return Dialog(
      child: SizedBox(
        width: dialogWidth,
        height: dialogHeight,
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Column(
            mainAxisSize: MainAxisSize.max,
            children: [
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.primaryContainer,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(
                    color: Theme.of(context).colorScheme.primary
                        .withValues(alpha: 0.25),
                  ),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.live_tv, size: 62),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text('DESKTOP APP'),
                          Text(
                            _format(
                              labels['autoAboutUiptvTitle'] ?? 'UIPTV {0}',
                              [version],
                            ),
                            style: Theme.of(context).textTheme.headlineSmall,
                          ),
                          Text(labels['autoAboutUiptvTagline'] ?? ''),
                        ],
                      ),
                    ),
                    Chip(label: Text('v$version')),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Expanded(
                child: SingleChildScrollView(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      _descriptionCard(
                        labels['autoAboutUiptvOverview'] ?? '',
                        labels['autoAboutUiptvWebSync'] ?? '',
                        _format(
                          labels['autoAboutUiptvReleaseNotes'] ??
                              'Current release: {0}',
                          [version],
                        ),
                      ),
                      const SizedBox(height: 10),
                      Text(labels['autoAuthorXixogo5105'] ?? ''),
                      Text(
                        _format(
                          labels['autoAboutUiptvPlatform'] ?? '{0} ({1})',
                          [
                            about['osName'] as String? ?? 'N/A',
                            about['osArch'] as String? ?? 'N/A',
                          ],
                        ),
                      ),
                      Text(
                        _format(
                          labels['autoAboutUiptvRuntime'] ??
                              'Runtime: {0} ({1})',
                          [
                            about['javaVersion'] as String? ?? 'N/A',
                            about['vmName'] as String? ?? 'N/A',
                          ],
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(labels['autoAboutUiptvPoweredBy'] ?? ''),
                      _linkButton(
                        labels['autoHttpsGithubComXixogo5105Uiptv'] ??
                            projectUrl,
                        projectUrl,
                      ),
                      _linkButton(labels['autoHelp'] ?? 'Help', helpUrl),
                      const SizedBox(height: 8),
                      Text(labels['autoCopyright2024Xixogo5105'] ?? ''),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  OutlinedButton(
                    onPressed: () => Navigator.pop(context),
                    child: Text(labels['autoClose'] ?? 'Close'),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: () => _openUrl(releaseUrl),
                    child: Text(
                      labels['autoCheckForUpdates'] ?? 'Check for Updates',
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _descriptionCard(String overview, String webSync, String release) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(overview),
            const SizedBox(height: 8),
            Text(webSync),
            const SizedBox(height: 8),
            Text(release),
          ],
        ),
      ),
    );
  }

  Widget _linkButton(String label, String url) {
    return Align(
      alignment: Alignment.centerLeft,
      child: TextButton(onPressed: () => _openUrl(url), child: Text(label)),
    );
  }

  String _format(String template, List<String> values) {
    var result = template;
    for (var index = 0; index < values.length; index++) {
      result = result.replaceAll('{$index}', values[index]);
    }
    return result;
  }

  Future<void> _openUrl(String value) async {
    final uri = Uri.tryParse(value);
    if (uri == null || uri.scheme != 'https' || uri.host != 'github.com') {
      return;
    }
    try {
      if (Platform.isMacOS) {
        await Process.start('open', [uri.toString()]);
      } else if (Platform.isWindows) {
        await Process.start('cmd', ['/c', 'start', '', uri.toString()]);
      } else if (Platform.isLinux) {
        await Process.start('xdg-open', [uri.toString()]);
      }
    } catch (_) {
      return;
    }
  }
}
