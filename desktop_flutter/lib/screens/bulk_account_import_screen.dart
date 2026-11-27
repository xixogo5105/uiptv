// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

class BulkAccountImportScreen extends StatefulWidget {
  const BulkAccountImportScreen({
    super.key,
    required this.api,
    this.onImported,
  });

  final UiptvApiClient api;
  final ValueChanged<List<String>>? onImported;

  @override
  State<BulkAccountImportScreen> createState() =>
      _BulkAccountImportScreenState();
}

class _BulkAccountImportScreenState extends State<BulkAccountImportScreen> {
  static const _translationKeys = [
    'autoImportBulkAccounts',
    'bulkImportSourceType',
    'bulkImportSourceDescription',
    'bulkImportDataTitle',
    'bulkImportDataDescription',
    'autoGroupAccountsByMACAddress',
    'autoGroupAccountsByUsernamePassword',
    'autoGroupConvertedM3UAccountsByUrl',
    'autoWherePossibleConvertM3UToXtreme',
    'autoStartVerificationAfterParsing',
    'parseAndSave',
    'parseMultipleAccountsInputHint',
    'autoInputCannotBeEmpty',
    'autoErrorParsingOrSavingAccounts',
    'autoAccountsParsedAndSaved',
    'autoClear',
    'commonSearch',
  ];

  final TextEditingController _dataController = TextEditingController();
  late Future<Map<String, String>> _translations;
  String _sourceType = 'stalker';
  bool _groupAccounts = true;
  bool _convertM3uToXtreme = true;
  bool _startVerificationAfterParsing = true;
  bool _importing = false;

  @override
  void initState() {
    super.initState();
    _translations = widget.api.translations(_translationKeys);
  }

  @override
  void dispose() {
    _dataController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, String>>(
      future: _translations,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(child: Text(snapshot.error.toString()));
        }
        final labels = snapshot.data ?? const {};
        return _buildForm(context, labels);
      },
    );
  }

  Widget _buildForm(BuildContext context, Map<String, String> labels) {
    final groupLabel = switch (_sourceType) {
      'xtreme' => 'autoGroupAccountsByUsernamePassword',
      'm3u' => 'autoGroupConvertedM3UAccountsByUrl',
      _ => 'autoGroupAccountsByMACAddress',
    };
    final showGroup = _sourceType != 'm3u' || _convertM3uToXtreme;

    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        padding: const EdgeInsets.all(12),
        child: ConstrainedBox(
          constraints: BoxConstraints(
            minHeight: (constraints.maxHeight - 24).clamp(0, double.infinity),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(
                labels['bulkImportSourceType'] ?? 'Source type',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 4),
              Text(labels['bulkImportSourceDescription'] ?? ''),
              const SizedBox(height: 8),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(
                    value: 'stalker',
                    label: Text('Stalker Portal'),
                  ),
                  ButtonSegment(value: 'xtreme', label: Text('Xtreme')),
                  ButtonSegment(value: 'm3u', label: Text('M3U Playlists')),
                ],
                selected: {_sourceType},
                onSelectionChanged: (selection) {
                  setState(() => _sourceType = selection.first);
                },
              ),
              const SizedBox(height: 12),
              if (showGroup)
                SwitchListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12),
                  title: Text(labels[groupLabel] ?? ''),
                  value: _groupAccounts,
                  onChanged: (value) => setState(() => _groupAccounts = value),
                ),
              if (_sourceType == 'm3u')
                SwitchListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 12),
                  title: Text(
                    labels['autoWherePossibleConvertM3UToXtreme'] ?? '',
                  ),
                  value: _convertM3uToXtreme,
                  onChanged: (value) => setState(() {
                    _convertM3uToXtreme = value;
                  }),
                ),
              SwitchListTile(
                contentPadding: const EdgeInsets.symmetric(horizontal: 12),
                title: Text(
                  labels['autoStartVerificationAfterParsing'] ??
                      'Start verification after parsing',
                ),
                value: _startVerificationAfterParsing,
                onChanged: (value) => setState(() {
                  _startVerificationAfterParsing = value;
                }),
              ),
              const SizedBox(height: 10),
              Text(
                labels['bulkImportDataTitle'] ?? 'Bulk account data',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 4),
              Text(labels['bulkImportDataDescription'] ?? ''),
              const SizedBox(height: 8),
              SizedBox(
                height: constraints.maxHeight.clamp(420, 760).toDouble(),
                child: TextField(
                  controller: _dataController,
                  expands: true,
                  minLines: null,
                  maxLines: null,
                  textAlignVertical: TextAlignVertical.top,
                  decoration: InputDecoration(
                    hintText: labels['parseMultipleAccountsInputHint'],
                    alignLabelWithHint: true,
                  ),
                ),
              ),
              const SizedBox(height: 10),
              Wrap(
                spacing: 10,
                runSpacing: 8,
                children: [
                  FilledButton(
                    onPressed: _importing ? null : () => _import(labels),
                    child: _importing
                        ? const SizedBox.square(
                            dimension: 18,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : Text(labels['parseAndSave'] ?? 'Import Accounts'),
                  ),
                  OutlinedButton(
                    onPressed: _importing
                        ? null
                        : () {
                            _dataController.clear();
                            setState(() {
                              _sourceType = 'stalker';
                              _groupAccounts = true;
                              _convertM3uToXtreme = true;
                              _startVerificationAfterParsing = true;
                            });
                          },
                    child: Text(labels['autoClear'] ?? 'Clear'),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _import(Map<String, String> labels) async {
    if (_dataController.text.trim().isEmpty) {
      _showMessage(labels['autoInputCannotBeEmpty'] ?? 'Input cannot be empty');
      return;
    }
    setState(() => _importing = true);
    try {
      final result = await widget.api.importAccounts(
        sourceType: _sourceType,
        data: _dataController.text,
        groupAccounts: _groupAccounts,
        convertM3uToXtreme: _convertM3uToXtreme,
        startVerificationAfterParsing: _startVerificationAfterParsing,
      );
      if (!mounted) return;
      _dataController.clear();
      widget.onImported?.call(result.verificationAccountIds);
      _showMessage(labels['autoAccountsParsedAndSaved'] ?? 'Accounts imported');
    } on UiptvApiException {
      if (mounted) {
        _showMessage(
          labels['autoErrorParsingOrSavingAccounts'] ??
              'Unable to parse or save the supplied accounts',
        );
      }
    } catch (_) {
      if (mounted) {
        _showMessage(
          labels['autoErrorParsingOrSavingAccounts'] ??
              'Unable to parse or save the supplied accounts',
        );
      }
    } finally {
      if (mounted) setState(() => _importing = false);
    }
  }

  void _showMessage(String message) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(message)));
  }
}
