// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

Future<bool?> showDesktopUpdateAvailableDialog({
  required BuildContext context,
  required UiptvUpdateInfo update,
  required Map<String, String> labels,
}) {
  return showDialog<bool>(
    context: context,
    builder: (context) =>
        DesktopUpdateAvailableDialog(update: update, labels: labels),
  );
}

class DesktopUpdateAvailableDialog extends StatelessWidget {
  const DesktopUpdateAvailableDialog({
    super.key,
    required this.update,
    required this.labels,
  });

  final UiptvUpdateInfo update;
  final Map<String, String> labels;

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.sizeOf(context);
    final width = (size.width * 0.88).clamp(480.0, 820.0).toDouble();
    final height = (size.height * 0.82).clamp(360.0, 648.0).toDouble();
    final colorScheme = Theme.of(context).colorScheme;
    final updatePrompt =
        (labels['autoUpdateAvailablePrompt'] ??
                'Update Available: {0}\\n\\nRelease notes: {1}\\n\\nClick OK to download.')
            .replaceAll('{0}', update.version)
            .replaceAll('{1}', update.description)
            .replaceAll(r'\n', '\n');
    return Dialog(
      child: SizedBox(
        width: width,
        height: height,
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 92,
                    height: 92,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(
                        color: colorScheme.outlineVariant.withValues(
                          alpha: 0.7,
                        ),
                      ),
                    ),
                    child: Image.asset(
                      'assets/icons/uiptv-app.png',
                      width: 72,
                      height: 72,
                      fit: BoxFit.contain,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Text('UIPTV'),
                            const Spacer(),
                            Chip(label: Text('v${update.version}')),
                          ],
                        ),
                        Text(
                          labels['autoUiptvVersion']?.replaceAll(
                                '{0}',
                                update.version,
                              ) ??
                              'UIPTV version: ${update.version}',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),
              Expanded(
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: SingleChildScrollView(
                            child: SelectableText(updatePrompt),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  OutlinedButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: Text(
                      labels['commonClose'] ?? labels['autoClose'] ?? 'Close',
                    ),
                  ),
                  const SizedBox(width: 8),
                  FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: Text(labels['autoOk'] ?? 'OK'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
