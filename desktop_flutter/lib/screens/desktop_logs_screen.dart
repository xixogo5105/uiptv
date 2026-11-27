// ignore: deprecated_member_use
import 'dart:async';

// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/uiptv_api_client.dart';

class DesktopLogsScreen extends StatefulWidget {
  const DesktopLogsScreen({super.key, required this.api});

  final UiptvApiClient api;

  @override
  State<DesktopLogsScreen> createState() => _DesktopLogsScreenState();
}

class _DesktopLogsScreenState extends State<DesktopLogsScreen> {
  static const _translationKeys = [
    'autoUiptvLogs',
    'autoCopy',
    'autoClear',
    'autoWebLogs',
    'autoDetach',
    'autoAttach',
    'autoLogsDetachedInASeparateWindow',
    'commonClose',
  ];

  late Future<Map<String, String>> _translations;
  Timer? _refreshTimer;
  List<String> _lines = const [];
  String? _error;
  bool _webLogs = false;
  bool _loading = true;
  bool _requestInFlight = false;

  @override
  void initState() {
    super.initState();
    _translations = widget.api.translations(_translationKeys);
    _reload();
    _refreshTimer = Timer.periodic(
      const Duration(seconds: 1),
      (_) => _reload(),
    );
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    super.dispose();
  }

  Future<void> _reload() async {
    if (_requestInFlight) return;
    _requestInFlight = true;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final lines = await widget.api.logs(web: _webLogs);
      if (!mounted) return;
      setState(() {
        _lines = lines;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    } finally {
      _requestInFlight = false;
    }
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, String>>(
      future: _translations,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting &&
            snapshot.data == null) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(child: Text(snapshot.error.toString()));
        }
        final labels = snapshot.data ?? const {};
        return Padding(
          padding: const EdgeInsets.all(10),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                labels['autoUiptvLogs'] ?? 'UIPTV Logs',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  OutlinedButton(
                    onPressed: () => Clipboard.setData(
                      ClipboardData(text: _lines.join('\n')),
                    ),
                    child: Text(labels['autoCopy'] ?? 'Copy'),
                  ),
                  OutlinedButton(
                    onPressed: _clear,
                    child: Text(labels['autoClear'] ?? 'Clear'),
                  ),
                  FilledButton.tonal(
                    onPressed: () {
                      setState(() => _webLogs = !_webLogs);
                      _reload();
                    },
                    child: Text(
                      _webLogs
                          ? labels['autoUiptvLogs'] ?? 'UIPTV Logs'
                          : labels['autoWebLogs'] ?? 'Web Logs',
                    ),
                  ),
                  FilledButton(
                    onPressed: () => _detach(labels),
                    child: Text(labels['autoDetach'] ?? 'Detach'),
                  ),
                ],
              ),
              const SizedBox(height: 10),
              Expanded(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.surface,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Theme.of(context).dividerColor),
                  ),
                  child: _logContent(),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _logContent() {
    if (_error != null) {
      return Center(child: Text(_error!));
    }
    if (_lines.isEmpty) {
      return Center(
        child: _loading
            ? const CircularProgressIndicator()
            : const SizedBox.shrink(),
      );
    }
    return Scrollbar(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(10),
        child: SelectionArea(
          child: SelectableText(
            _lines.join('\n'),
            style: const TextStyle(fontFamily: 'monospace'),
          ),
        ),
      ),
    );
  }

  Future<void> _clear() async {
    try {
      await widget.api.clearLogs(web: _webLogs);
      await _reload();
    } catch (error) {
      if (!mounted) return;
      setState(() => _error = error.toString());
    }
  }

  Future<void> _detach(Map<String, String> labels) async {
    await showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: Text(
          labels['autoLogsDetachedInASeparateWindow'] ??
              'Logs detached in a separate window.',
        ),
        content: SizedBox(
          width: 720,
          height: 480,
          child: SingleChildScrollView(
            child: SelectionArea(
              child: SelectableText(
                _lines.join('\n'),
                style: const TextStyle(fontFamily: 'monospace'),
              ),
            ),
          ),
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(labels['autoAttach'] ?? 'Attach'),
          ),
        ],
      ),
    );
  }
}
