import 'dart:async';

// ignore: deprecated_member_use
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/uiptv_api_client.dart';

class DesktopCacheReloadScreen extends StatefulWidget {
  const DesktopCacheReloadScreen({
    super.key,
    required this.api,
    this.initialSelectedAccountIds = const [],
  });

  final UiptvApiClient api;
  final List<String> initialSelectedAccountIds;

  @override
  State<DesktopCacheReloadScreen> createState() =>
      _DesktopCacheReloadScreenState();
}

class _DesktopCacheReloadScreenState extends State<DesktopCacheReloadScreen> {
  static const _translationKeys = [
    'autoReloadAccountsCache',
    'reloadBulkFailureHandlingTitle',
    'reloadBulkFailureHandlingMessage',
    'reloadBulkFailureAskEachAccount',
    'reloadCarryOn',
    'reloadMarkBadIgnoreDomain',
    'reloadMarkBadAndNext',
    'reloadStopAll',
    'reloadStalkerPortalAccounts',
    'reloadXtremeAccount',
    'autoStartCacheReload',
    'autoStop',
    'autoQueued',
    'autoRunningProgress',
    'autoUiptvLogs',
    'commonAll',
    'commonSearch',
    'commonClose',
    'autoCopy',
    'reloadDone',
    'reloadDoneWithChannels',
    'reloadPartial',
    'reloadPartialWithChannels',
    'reloadFailed',
    'reloadNoChannelsFound',
    'reloadNoChannelsLoaded',
    'reloadCacheDisabledSkipping',
    'reloadSkippedIgnoredDomain',
    'reloadGlobalCallFailure',
    'reloadGlobalFailurePrompt',
    'reloadGlobalFailurePromptWithIgnoreDomain',
  ];

  static const _failurePolicies = [
    ('ask', 'reloadBulkFailureAskEachAccount'),
    ('carryOn', 'reloadCarryOn'),
    ('markBadIgnoreDomain', 'reloadMarkBadIgnoreDomain'),
    ('markBad', 'reloadMarkBadAndNext'),
    ('stopAll', 'reloadStopAll'),
  ];

  late Future<_CacheReloadData> _initialData;
  Timer? _pollTimer;
  List<UiptvAccountSummary> _accounts = const [];
  Map<String, String> _labels = const {};
  Map<String, dynamic>? _job;
  String? _jobId;
  String _accountType = 'all';
  String _failurePolicy = 'ask';
  String _search = '';
  final Set<String> _selectedAccountIds = {};
  bool _starting = false;
  bool _pollInFlight = false;
  bool _decisionDialogOpen = false;
  bool _initialSelectionApplied = false;
  String? _lastDecisionFailureKey;
  String? _error;

  @override
  void initState() {
    super.initState();
    _initialData = _loadInitialData();
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  Future<_CacheReloadData> _loadInitialData() async {
    final accounts = await widget.api.cacheReloadAccounts();
    final labels = await widget.api.translations(_translationKeys);
    return _CacheReloadData(accounts: accounts, labels: labels);
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<_CacheReloadData>(
      future: _initialData,
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting &&
            snapshot.data == null) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return Center(child: Text(snapshot.error.toString()));
        }
        final data = snapshot.data;
        if (data == null) return const SizedBox.shrink();
        if (!_initialSelectionApplied) {
          _accounts = data.accounts;
          _labels = data.labels;
          _selectedAccountIds.addAll(
            widget.initialSelectedAccountIds.where(
              (id) => data.accounts.any((account) => account.dbId == id),
            ),
          );
          _initialSelectionApplied = true;
        }
        return _buildScreen(context);
      },
    );
  }

  Widget _buildScreen(BuildContext context) {
    final visibleAccounts = _accounts.where(_matchesFilter).toList();
    final width = MediaQuery.sizeOf(context).width;
    final accountPanel = _accountPanel(visibleAccounts);
    final logPanel = _logPanel();
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            _text('autoReloadAccountsCache', 'Reload Accounts Cache'),
            style: Theme.of(context).textTheme.headlineSmall,
          ),
          const SizedBox(height: 10),
          _failurePolicyPicker(),
          const SizedBox(height: 10),
          Expanded(
            child: width < 800
                ? Column(
                    children: [
                      Expanded(child: accountPanel),
                      const SizedBox(height: 10),
                      Expanded(child: logPanel),
                    ],
                  )
                : Row(
                    children: [
                      Expanded(flex: 5, child: accountPanel),
                      const SizedBox(width: 12),
                      Expanded(flex: 7, child: logPanel),
                    ],
                  ),
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              FilledButton(
                onPressed:
                    _starting || _isRunning || _selectedAccountIds.isEmpty
                    ? null
                    : _startReload,
                child: _starting
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Text(_text('autoStartCacheReload', 'Start Cache Reload')),
              ),
              const SizedBox(width: 8),
              if (_isRunning)
                OutlinedButton(
                  onPressed: _stopReload,
                  child: Text(_text('autoStop', 'Stop')),
                ),
              const Spacer(),
              OutlinedButton.icon(
                onPressed: () => Clipboard.setData(
                  ClipboardData(text: _logLines.join('\n')),
                ),
                icon: const Icon(Icons.copy),
                label: Text(_text('autoCopy', 'Copy Log')),
              ),
            ],
          ),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
          ],
        ],
      ),
    );
  }

  Widget _failurePolicyPicker() {
    final items = [
      for (final (value, key) in _failurePolicies)
        DropdownMenuItem(
          value: value,
          child: Text(_text(key, value), overflow: TextOverflow.ellipsis),
        ),
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final information = Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _text(
                    'reloadBulkFailureHandlingTitle',
                    'Automatic Failure Handling',
                  ),
                  style: Theme.of(context).textTheme.titleSmall,
                ),
                const SizedBox(height: 4),
                Text(
                  _text(
                    'reloadBulkFailureHandlingMessage',
                    'Choose how global data fetch failures are handled.',
                  ),
                ),
              ],
            );
            final picker = DropdownButtonFormField<String>(
              initialValue: _failurePolicy,
              isExpanded: true,
              items: items,
              onChanged: _isRunning
                  ? null
                  : (value) {
                      if (value != null) {
                        setState(() => _failurePolicy = value);
                      }
                    },
            );
            if (constraints.maxWidth < 720) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [information, const SizedBox(height: 8), picker],
              );
            }
            return Row(
              children: [
                Expanded(child: information),
                const SizedBox(width: 12),
                SizedBox(width: 300, child: picker),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _accountPanel(List<UiptvAccountSummary> accounts) {
    return Card(
      child: Column(
        children: [
          SizedBox(
            height: 48,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 8),
              children: [
                _typeChip('all', _text('commonAll', 'All')),
                _typeChip(
                  'stalker',
                  _text(
                    'reloadStalkerPortalAccounts',
                    'Stalker Portal Accounts',
                  ),
                ),
                _typeChip(
                  'xtreme',
                  _text('reloadXtremeAccount', 'Xtreme Account'),
                ),
                _typeChip('m3u', 'M3U'),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: TextField(
              onChanged: (value) =>
                  setState(() => _search = value.trim().toLowerCase()),
              decoration: InputDecoration(
                hintText: _text('commonSearch', 'Search'),
                prefixIcon: const Icon(Icons.search),
              ),
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: ListView.builder(
              itemCount: accounts.length,
              itemBuilder: (context, index) {
                final account = accounts[index];
                final accountJob = _accountJobState(account.dbId);
                final subtitleParts = <String>[account.type];
                if (!account.cacheEnabled) {
                  subtitleParts.add(
                    _text('reloadCacheDisabledSkipping', 'Caching disabled'),
                  );
                }
                if (accountJob != null) {
                  subtitleParts.add(_accountStatusLabel(accountJob));
                }
                return CheckboxListTile(
                  value: _selectedAccountIds.contains(account.dbId),
                  title: Text(account.accountName),
                  subtitle: Text(subtitleParts.join(' · ')),
                  controlAffinity: ListTileControlAffinity.leading,
                  onChanged: _isRunning
                      ? null
                      : (selected) => setState(() {
                          if (selected == true) {
                            _selectedAccountIds.add(account.dbId);
                          } else {
                            _selectedAccountIds.remove(account.dbId);
                          }
                        }),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _typeChip(String type, String label) {
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: ChoiceChip(
        label: Text(label),
        selected: _accountType == type,
        onSelected: (_) => setState(() => _accountType = type),
      ),
    );
  }

  Widget _logPanel() {
    final logs = _logLines;
    final total = _job?['total'] as int? ?? 0;
    final completed = _job?['completed'] as int? ?? 0;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                Text(
                  _text('autoUiptvLogs', 'Logs'),
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const Spacer(),
                if (_job != null)
                  Text(
                    total == 0
                        ? _statusLabel(_job!['status'] as String? ?? '')
                        : _text('autoRunningProgress', 'Running ({0}/{1})')
                              .replaceAll('{0}', '$completed')
                              .replaceAll('{1}', '$total'),
                  ),
              ],
            ),
            if (_isRunning) ...[
              const SizedBox(height: 6),
              LinearProgressIndicator(
                value: total <= 0 ? null : completed / total,
              ),
            ],
            const SizedBox(height: 8),
            Expanded(
              child: DecoratedBox(
                decoration: BoxDecoration(
                  border: Border.all(color: Theme.of(context).dividerColor),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: logs.isEmpty
                    ? const SizedBox.shrink()
                    : Scrollbar(
                        child: SingleChildScrollView(
                          padding: const EdgeInsets.all(10),
                          child: SelectionArea(
                            child: SelectableText(
                              logs.join('\n'),
                              style: const TextStyle(fontFamily: 'monospace'),
                            ),
                          ),
                        ),
                      ),
              ),
            ),
            if (_pendingFailure != null) ...[
              const SizedBox(height: 8),
              Text(
                _text('reloadGlobalCallFailure', 'Global Call Failure'),
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
              Text(_pendingFailure!['reason'] as String? ?? ''),
            ],
          ],
        ),
      ),
    );
  }

  bool _matchesFilter(UiptvAccountSummary account) {
    final matchesType = switch (_accountType) {
      'stalker' => account.type == 'STALKER_PORTAL',
      'xtreme' => account.type == 'XTREME_API',
      'm3u' => account.type.startsWith('M3U8_'),
      _ => true,
    };
    return matchesType &&
        (_search.isEmpty ||
            account.accountName.toLowerCase().contains(_search));
  }

  List<String> get _logLines {
    final result = <String>[];
    final jobLogs = _job?['logs'] as List<dynamic>? ?? const [];
    result.addAll(jobLogs.whereType<String>());
    final accountStates = _job?['accounts'] as List<dynamic>? ?? const [];
    for (final state in accountStates.whereType<Map>()) {
      final name = state['accountName'] as String? ?? '';
      final lines = state['logs'] as List<dynamic>? ?? const [];
      result.addAll(lines.whereType<String>().map((line) => '$name · $line'));
    }
    return result;
  }

  Map<String, dynamic>? _accountJobState(String accountDbId) {
    final states = _job?['accounts'] as List<dynamic>? ?? const [];
    for (final state in states.whereType<Map>()) {
      if (state['dbId'] == accountDbId) return Map<String, dynamic>.from(state);
    }
    return null;
  }

  String _accountStatusLabel(Map<String, dynamic> state) {
    final status = state['status'] as String? ?? '';
    final channelCount = state['channelCount'] as int? ?? 0;
    return switch (status) {
      'done' => _text(
        'reloadDoneWithChannels',
        'Done ({0} channels)',
      ).replaceAll('{0}', '$channelCount'),
      'yellow' => _text(
        'reloadPartialWithChannels',
        'Partial ({0} channels)',
      ).replaceAll('{0}', '$channelCount'),
      'bad' || 'failed' => _text('reloadFailed', 'Failed'),
      'skipped' => _text('reloadNoChannelsLoaded', 'Skipped'),
      'stopped' => _text('reloadStopAll', 'Stopped'),
      _ => _statusLabel(status),
    };
  }

  Map<String, dynamic>? get _pendingFailure {
    final failure = _job?['pendingFailure'];
    return failure is Map ? Map<String, dynamic>.from(failure) : null;
  }

  bool get _isRunning {
    final status = _job?['status'];
    return status == 'queued' ||
        status == 'running' ||
        status == 'waiting_for_decision';
  }

  Future<void> _startReload() async {
    setState(() {
      _starting = true;
      _error = null;
    });
    try {
      final job = await widget.api.startCacheReload(
        accountIds: _selectedAccountIds.toList(growable: false),
        failurePolicy: _failurePolicy,
      );
      if (!mounted) return;
      setState(() {
        _job = job;
        _jobId = job['jobId'] as String?;
        _lastDecisionFailureKey = null;
      });
      _startPolling();
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      if (mounted) setState(() => _starting = false);
    }
  }

  void _startPolling() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(seconds: 1), (_) => _pollJob());
    _pollJob();
  }

  Future<void> _pollJob() async {
    final jobId = _jobId;
    if (jobId == null || _pollInFlight) return;
    _pollInFlight = true;
    try {
      final job = await widget.api.cacheReloadJob(jobId);
      if (!mounted) return;
      setState(() => _job = job);
      if (!_isRunning) _pollTimer?.cancel();
      await _showPendingFailureDecision(job);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    } finally {
      _pollInFlight = false;
    }
  }

  Future<void> _showPendingFailureDecision(Map<String, dynamic> job) async {
    final failureValue = job['pendingFailure'];
    if (failureValue is! Map || _decisionDialogOpen) return;
    _decisionDialogOpen = true;
    final failure = Map<String, dynamic>.from(failureValue);
    final canIgnore = failure['canIgnoreDomain'] as bool? ?? false;
    final accountName = failure['accountName'] as String? ?? '';
    final reason = failure['reason'] as String? ?? '';
    final failureKey = '${failure['accountDbId']}:$reason';
    if (_lastDecisionFailureKey == failureKey) {
      _decisionDialogOpen = false;
      return;
    }
    final promptKey = canIgnore
        ? 'reloadGlobalFailurePromptWithIgnoreDomain'
        : 'reloadGlobalFailurePrompt';
    final prompt = _text(
      promptKey,
      'Account: {0}\n{1}\n\nChoose how to continue.',
    ).replaceAll('{0}', accountName).replaceAll('{1}', reason);
    final decision = await showDialog<String>(
      context: context,
      barrierDismissible: false,
      builder: (context) => AlertDialog(
        title: Text(_text('reloadGlobalCallFailure', 'Global Call Failure')),
        content: SizedBox(
          width: 520,
          child: SingleChildScrollView(child: Text(prompt)),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, 'carryOn'),
            child: Text(_text('reloadCarryOn', 'Carry On')),
          ),
          if (canIgnore)
            TextButton(
              onPressed: () => Navigator.pop(context, 'markBadIgnoreDomain'),
              child: Text(
                _text(
                  'reloadMarkBadIgnoreDomain',
                  'Mark Bad, Ignore Similar URLs & Next',
                ),
              ),
            ),
          TextButton(
            onPressed: () => Navigator.pop(context, 'markBad'),
            child: Text(_text('reloadMarkBadAndNext', 'Mark Bad & Next')),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, 'stopAll'),
            child: Text(_text('reloadStopAll', 'Stop All')),
          ),
        ],
      ),
    );
    _decisionDialogOpen = false;
    if (decision == null || !mounted) return;
    _lastDecisionFailureKey = failureKey;
    try {
      final updated = await widget.api.decideCacheReloadFailure(
        jobId: job['jobId'] as String,
        decision: decision,
      );
      if (mounted) setState(() => _job = updated);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  Future<void> _stopReload() async {
    final jobId = _jobId;
    if (jobId == null) return;
    try {
      final job = await widget.api.stopCacheReload(jobId);
      if (mounted) setState(() => _job = job);
    } catch (error) {
      if (mounted) setState(() => _error = error.toString());
    }
  }

  String _statusLabel(String status) {
    return switch (status) {
      'queued' => _text('autoQueued', 'Queued'),
      'completed' => _text('reloadDone', 'Done'),
      'stopped' => _text('reloadStopAll', 'Stopped'),
      'waiting_for_decision' => _text(
        'reloadGlobalCallFailure',
        'Failure decision required',
      ),
      'failed' => _text('reloadFailed', 'Failed'),
      _ => status,
    };
  }

  String _text(String key, String fallback) => _labels[key] ?? fallback;
}

class _CacheReloadData {
  const _CacheReloadData({required this.accounts, required this.labels});

  final List<UiptvAccountSummary> accounts;
  final Map<String, String> labels;
}
