import 'dart:math' as math;

// ignore: deprecated_member_use
import 'package:flutter/material.dart';

import '../api/uiptv_api_client.dart';

class DesktopAccountEditor extends StatefulWidget {
  const DesktopAccountEditor({
    super.key,
    required this.api,
    required this.translations,
    required this.onSaved,
    required this.onCancel,
    this.accountId,
  });

  final UiptvApiClient api;
  final Map<String, String> translations;
  final ValueChanged<Map<String, dynamic>> onSaved;
  final VoidCallback onCancel;
  final String? accountId;

  @override
  State<DesktopAccountEditor> createState() => _DesktopAccountEditorState();
}

class _DesktopAccountEditorState extends State<DesktopAccountEditor> {
  static const _accountTypes = [
    'STALKER_PORTAL',
    'XTREME_API',
    'M3U8_URL',
    'M3U8_LOCAL',
  ];

  final _formKey = GlobalKey<FormState>();
  final _controllers = <String, TextEditingController>{
    for (final field in [
      'accountName',
      'username',
      'password',
      'url',
      'macAddress',
      'macAddressList',
      'serialNumber',
      'deviceId1',
      'deviceId2',
      'signature',
      'epg',
      'm3u8Path',
      'httpMethod',
      'timezone',
    ])
      field: TextEditingController(),
  };

  bool _loading = false;
  bool _saving = false;
  bool _pinToTop = false;
  bool _cacheEnabled = true;
  bool _resolveRedirects = false;
  bool _webPlaybackProxy = false;
  bool _parentalLock = true;
  String _type = 'STALKER_PORTAL';
  String? _selectedMacAddress;
  String? _loadError;

  bool get _isCreating => widget.accountId == null;

  @override
  void initState() {
    super.initState();
    _controllers['httpMethod']!.text = 'GET';
    _controllers['timezone']!.text = 'Europe/London';
    if (!_isCreating) {
      _loadAccount();
    }
  }

  @override
  void dispose() {
    for (final controller in _controllers.values) {
      controller.dispose();
    }
    super.dispose();
  }

  String _tr(String key, String fallback) =>
      widget.translations[key] ?? fallback;

  Future<void> _loadAccount() async {
    setState(() {
      _loading = true;
      _loadError = null;
    });
    try {
      final account = await widget.api.accountDetails(widget.accountId!);
      if (!mounted) return;
      for (final entry in _controllers.entries) {
        entry.value.text = account[entry.key] as String? ?? '';
      }
      setState(() {
        _type = _accountTypes.contains(account['type'])
            ? account['type'] as String
            : 'STALKER_PORTAL';
        _selectedMacAddress = account['macAddress'] as String?;
        _pinToTop = account['pinToTop'] as bool? ?? false;
        _cacheEnabled = account['cacheEnabled'] as bool? ?? true;
        _resolveRedirects =
            account['resolveChainAndDeepRedirects'] as bool? ?? false;
        _webPlaybackProxy = account['webPlaybackProxy'] as bool? ?? false;
        _parentalLock = account['parentalLock'] as bool? ?? true;
        _loading = false;
      });
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _loadError = error.toString();
        _loading = false;
      });
    }
  }

  Widget _field(
    String key,
    String label, {
    bool required = false,
    bool obscure = false,
    int maxLines = 1,
    ValueChanged<String>? onChanged,
  }) {
    return TextFormField(
      key: ValueKey('account-$key'),
      controller: _controllers[key],
      onChanged: onChanged,
      obscureText: obscure,
      maxLines: maxLines,
      decoration: InputDecoration(
        labelText: label,
        alignLabelWithHint: maxLines > 1,
        border: const OutlineInputBorder(),
        isDense: true,
      ),
      validator: required
          ? (value) => value == null || value.trim().isEmpty
                ? _tr('autoInputCannotBeEmpty', 'This field is required')
                : null
          : null,
    );
  }

  List<Widget> _accountFields() {
    final fields = <Widget>[
      _field(
        'accountName',
        _tr('manageAccountNameMustBeUniquePrompt', 'Account name'),
        required: true,
      ),
    ];
    switch (_type) {
      case 'STALKER_PORTAL':
        fields.addAll([
          _field('url', _tr('manageUrlPrompt', 'Portal URL'), required: true),
          _field('username', _tr('manageUserNamePrompt', 'Username')),
          _field(
            'password',
            _tr('managePasswordPrompt', 'Password'),
            obscure: true,
          ),
          _macAddressSelector(),
          _field(
            'macAddressList',
            _tr('manageMacAddressListPrompt', 'MAC address list'),
            maxLines: 5,
            onChanged: (_) => setState(() {}),
          ),
          _field(
            'serialNumber',
            _tr('manageSerialNumberPrompt', 'Serial Number (SN)'),
          ),
          _field('deviceId1', _tr('manageDeviceId1Prompt', 'Device ID 1')),
          _field('deviceId2', _tr('manageDeviceId2Prompt', 'Device ID 2')),
          _field('signature', _tr('manageSignaturePrompt', 'Signature')),
          _field('epg', _tr('manageEpgPrompt', 'EPG source')),
        ]);
        break;
      case 'XTREME_API':
        fields.addAll([
          _field('url', _tr('manageUrlPrompt', 'Server URL'), required: true),
          _field('username', _tr('manageUserNamePrompt', 'Username')),
          _field(
            'password',
            _tr('managePasswordPrompt', 'Password'),
            obscure: true,
          ),
          _field('epg', _tr('manageEpgPrompt', 'EPG source')),
        ]);
        break;
      case 'M3U8_URL':
        fields.addAll([
          _field(
            'm3u8Path',
            _tr('manageM3u8FilePathUrlPrompt', 'Playlist URL'),
            required: true,
          ),
          _field('epg', _tr('manageEpgPrompt', 'EPG source')),
        ]);
        break;
      case 'M3U8_LOCAL':
        fields.add(
          _field(
            'm3u8Path',
            _tr('manageM3u8FilePathUrlPrompt', 'Playlist file path'),
            required: true,
          ),
        );
        fields.add(_field('epg', _tr('manageEpgPrompt', 'EPG source')));
        break;
    }
    fields.addAll([
      _field('httpMethod', _tr('manageHttpMethodPrompt', 'HTTP method')),
      _field('timezone', _tr('manageTimezonePrompt', 'Timezone')),
    ]);
    return fields;
  }

  Map<String, dynamic> _accountPayload() {
    final addresses = _macAddresses();
    final selectedAddress =
        _selectedMacAddress != null &&
            addresses.any(
              (address) =>
                  address.toLowerCase() == _selectedMacAddress!.toLowerCase(),
            )
        ? _selectedMacAddress
        : addresses.isEmpty
        ? null
        : addresses.first;
    return {
      for (final entry in _controllers.entries) entry.key: entry.value.text,
      'macAddress': _type == 'STALKER_PORTAL'
          ? selectedAddress ?? ''
          : _controllers['macAddress']!.text,
      'type': _type,
      'pinToTop': _pinToTop,
      'cacheEnabled': _cacheEnabled,
      'resolveChainAndDeepRedirects': _resolveRedirects,
      'webPlaybackProxy': _webPlaybackProxy,
      'parentalLock': _parentalLock,
    };
  }

  List<String> _macAddresses() {
    final values = _controllers['macAddressList']!.text
        .split(',')
        .map((value) => value.trim())
        .where((value) => value.isNotEmpty)
        .toList();
    final seen = <String>{};
    final unique = values
        .where((value) => seen.add(value.toLowerCase()))
        .toList();
    final selected = _selectedMacAddress?.trim() ?? '';
    if (selected.isNotEmpty &&
        !unique.any((value) => value.toLowerCase() == selected.toLowerCase())) {
      unique.insert(0, selected);
    }
    return unique;
  }

  Widget _macAddressSelector() {
    final addresses = _macAddresses();
    final selected = addresses.contains(_selectedMacAddress)
        ? _selectedMacAddress
        : addresses.isEmpty
        ? null
        : addresses.first;
    return Row(
      children: [
        Expanded(
          child: DropdownButtonFormField<String>(
            key: ValueKey(
              'mac-address-${selected ?? 'none'}-${addresses.length}',
            ),
            initialValue: selected,
            decoration: InputDecoration(
              labelText: _tr(
                'managePrimaryMacAddressHint',
                'Primary MAC address',
              ),
              border: const OutlineInputBorder(),
              isDense: true,
            ),
            items: addresses
                .map(
                  (address) => DropdownMenuItem(
                    value: address,
                    child: Text(address, overflow: TextOverflow.ellipsis),
                  ),
                )
                .toList(),
            onChanged: _saving
                ? null
                : (address) => setState(() {
                    _selectedMacAddress = address;
                    _controllers['macAddress']!.text = address ?? '';
                  }),
          ),
        ),
        const SizedBox(width: 4),
        TextButton(
          onPressed: _saving ? null : _manageMacAddresses,
          child: Text(_tr('autoManage', 'Manage')),
        ),
      ],
    );
  }

  Future<void> _manageMacAddresses() async {
    final addresses = _macAddresses();
    var selected = _selectedMacAddress;
    final newAddressController = TextEditingController();
    await showDialog<void>(
      context: context,
      builder: (dialogContext) => StatefulBuilder(
        builder: (context, setDialogState) => AlertDialog(
          title: Text(_tr('autoManageMACAddresses', 'Manage MAC addresses')),
          content: SizedBox(
            width: math.min(500.0, MediaQuery.sizeOf(context).width - 48),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                RadioGroup<String>(
                  groupValue: selected,
                  onChanged: (value) => setDialogState(() => selected = value),
                  child: SizedBox(
                    height: 260,
                    child: ListView.builder(
                      itemCount: addresses.length,
                      itemBuilder: (context, index) {
                        final address = addresses[index];
                        return ListTile(
                          dense: true,
                          selected: address == selected,
                          leading: Radio<String>(value: address),
                          title: Text(address),
                          trailing: IconButton(
                            tooltip: _tr('autoRemove', 'Remove'),
                            icon: const Icon(Icons.remove_circle_outline),
                            onPressed: () => setDialogState(() {
                              addresses.removeAt(index);
                              if (selected == address) {
                                selected = addresses.isEmpty
                                    ? null
                                    : addresses.first;
                              }
                            }),
                          ),
                          onTap: () => setDialogState(() => selected = address),
                        );
                      },
                    ),
                  ),
                ),
                Row(
                  children: [
                    Expanded(
                      child: TextField(
                        key: const ValueKey('new-mac-address'),
                        controller: newAddressController,
                        decoration: InputDecoration(
                          labelText: _tr(
                            'autoAddMACAddressEsCommaSeparated',
                            'Add MAC address',
                          ),
                          isDense: true,
                        ),
                      ),
                    ),
                    IconButton(
                      tooltip: _tr('autoAdd', 'Add'),
                      icon: const Icon(Icons.add),
                      onPressed: () {
                        final candidate = newAddressController.text.trim();
                        if (candidate.isEmpty ||
                            addresses.any(
                              (value) =>
                                  value.toLowerCase() ==
                                  candidate.toLowerCase(),
                            )) {
                          return;
                        }
                        setDialogState(() {
                          addresses.add(candidate);
                          selected ??= candidate;
                          newAddressController.clear();
                        });
                      },
                    ),
                  ],
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogContext),
              child: Text(_tr('autoCancel', 'Cancel')),
            ),
            FilledButton(
              onPressed: () {
                setState(() {
                  _controllers['macAddressList']!.text = addresses.join(', ');
                  _selectedMacAddress = selected;
                  _controllers['macAddress']!.text = selected ?? '';
                });
                Navigator.pop(dialogContext);
              },
              child: Text(_tr('commonSave', 'Save')),
            ),
          ],
        ),
      ),
    );
    newAddressController.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final payload = _accountPayload();
      final saved = _isCreating
          ? await widget.api.createAccount(payload)
          : await widget.api.updateAccount(widget.accountId!, payload);
      if (mounted) widget.onSaved(saved);
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(error.toString())));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  Widget _switchRow(String title, bool value, ValueChanged<bool> onChanged) {
    return SwitchListTile.adaptive(
      contentPadding: EdgeInsets.zero,
      title: Text(title),
      value: value,
      onChanged: _saving ? null : onChanged,
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_loadError != null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_loadError!, textAlign: TextAlign.center),
            const SizedBox(height: 8),
            OutlinedButton(onPressed: _loadAccount, child: const Text('Retry')),
          ],
        ),
      );
    }
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = math.min(380.0, constraints.maxWidth).toDouble();
        return Form(
          key: _formKey,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(14, 8, 8, 8),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        _isCreating
                            ? _tr('autoNewAccount', 'New Account')
                            : _tr('autoEditManageAccount', 'Account'),
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    IconButton(
                      tooltip: _tr('commonClose', 'Close'),
                      onPressed: widget.onCancel,
                      icon: const Icon(Icons.close),
                    ),
                    FilledButton.tonalIcon(
                      onPressed: _saving ? null : _save,
                      icon: _saving
                          ? const SizedBox.square(
                              dimension: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.save_outlined, size: 18),
                      label: Text(_tr('commonSave', 'Save')),
                    ),
                  ],
                ),
              ),
              const Divider(height: 1),
              Expanded(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(14),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      DropdownButtonFormField<String>(
                        key: const ValueKey('account-type'),
                        initialValue: _type,
                        decoration: InputDecoration(
                          labelText: _tr('autoAccount', 'Account'),
                          border: const OutlineInputBorder(),
                          isDense: true,
                        ),
                        items: _accountTypes
                            .map(
                              (type) => DropdownMenuItem(
                                value: type,
                                child: Text(type.replaceAll('_', ' ')),
                              ),
                            )
                            .toList(),
                        onChanged: _saving
                            ? null
                            : (type) {
                                if (type != null) setState(() => _type = type);
                              },
                      ),
                      const SizedBox(height: 12),
                      Wrap(
                        spacing: 10,
                        runSpacing: 10,
                        children: _accountFields()
                            .map(
                              (field) => SizedBox(width: width, child: field),
                            )
                            .toList(),
                      ),
                      const SizedBox(height: 8),
                      _switchRow(
                        _tr('autoPinAccountOnTop', 'Pin account on top'),
                        _pinToTop,
                        (value) => setState(() => _pinToTop = value),
                      ),
                      _switchRow(
                        _tr(
                          'manageAccountDisableCaching',
                          'Disable catalogue caching',
                        ),
                        !_cacheEnabled,
                        (value) => setState(() => _cacheEnabled = !value),
                      ),
                      _switchRow(
                        _tr(
                          'manageResolveChainAndDeepRedirects',
                          'Resolve redirect chains',
                        ),
                        _resolveRedirects,
                        (value) => setState(() => _resolveRedirects = value),
                      ),
                      _switchRow(
                        _tr(
                          'manageWebPlaybackProxy',
                          'Use browser stream proxy',
                        ),
                        _webPlaybackProxy,
                        (value) => setState(() => _webPlaybackProxy = value),
                      ),
                      _switchRow(
                        _tr(
                          'autoEnableParentalLockAccess',
                          'Enable parental lock access',
                        ),
                        _parentalLock,
                        (value) => setState(() => _parentalLock = value),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}
