import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

class UiptvApiException implements Exception {
  UiptvApiException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  @override
  String toString() => 'UIPTV API $statusCode: $message';
}

class UiptvAccountSummary {
  UiptvAccountSummary({
    required this.dbId,
    required this.accountName,
    required this.type,
    required this.pinToTop,
    required this.cacheEnabled,
    required this.webPlaybackProxy,
    this.typeDisplay = '',
    this.categoryCount = 0,
    this.channelCount = 0,
    this.categoryCountDisplay = '',
    this.channelCountDisplay = '',
    this.expiryDate = '',
    this.expiryState = 'unknown',
    this.pinSvgStemPath = '',
    this.pinSvgHeadPath = '',
    this.pinSvgStemFill = '',
    this.pinSvgHeadFill = '',
    this.pinSvgScale = 0.075,
  });

  factory UiptvAccountSummary.fromJson(Map<String, dynamic> json) {
    return UiptvAccountSummary(
      dbId: json['dbId'] as String? ?? '',
      accountName: json['accountName'] as String? ?? '',
      type: json['type'] as String? ?? '',
      pinToTop: json['pinToTop'] as bool? ?? false,
      cacheEnabled: json['cacheEnabled'] as bool? ?? true,
      webPlaybackProxy: json['webPlaybackProxy'] as bool? ?? false,
      typeDisplay: json['typeDisplay'] as String? ?? '',
      categoryCount: json['categoryCount'] as int? ?? 0,
      channelCount: json['channelCount'] as int? ?? 0,
      categoryCountDisplay: json['categoryCountDisplay'] as String? ?? '',
      channelCountDisplay: json['channelCountDisplay'] as String? ?? '',
      expiryDate: json['expiryDate'] as String? ?? '',
      expiryState: json['expiryState'] as String? ?? 'unknown',
      pinSvgStemPath: json['pinSvgStemPath'] as String? ?? '',
      pinSvgHeadPath: json['pinSvgHeadPath'] as String? ?? '',
      pinSvgStemFill: json['pinSvgStemFill'] as String? ?? '',
      pinSvgHeadFill: json['pinSvgHeadFill'] as String? ?? '',
      pinSvgScale: (json['pinSvgScale'] as num?)?.toDouble() ?? 0.075,
    );
  }

  final String dbId;
  final String accountName;
  final String type;
  final bool pinToTop;
  final bool cacheEnabled;
  final bool webPlaybackProxy;
  final String typeDisplay;
  final int categoryCount;
  final int channelCount;
  final String categoryCountDisplay;
  final String channelCountDisplay;
  final String expiryDate;
  final String expiryState;
  final String pinSvgStemPath;
  final String pinSvgHeadPath;
  final String pinSvgStemFill;
  final String pinSvgHeadFill;
  final double pinSvgScale;
}

class UiptvAccountImportResult {
  const UiptvAccountImportResult({
    required this.accounts,
    required this.verificationAccountIds,
  });

  final List<UiptvAccountSummary> accounts;
  final List<String> verificationAccountIds;
}

class UiptvCategorySummary {
  const UiptvCategorySummary({
    required this.dbId,
    required this.title,
    required this.alias,
    required this.activeSub,
  });

  factory UiptvCategorySummary.fromJson(Map<String, dynamic> json) {
    return UiptvCategorySummary(
      dbId: json['dbId'] as String? ?? '',
      title: json['title'] as String? ?? '',
      alias: json['alias'] as String? ?? '',
      activeSub: json['activeSub'] as bool? ?? false,
    );
  }

  final String dbId;
  final String title;
  final String alias;
  final bool activeSub;
}

class UiptvChannelSummary {
  const UiptvChannelSummary({
    required this.dbId,
    required this.playbackId,
    required this.name,
    required this.number,
    required this.logo,
    required this.description,
  });

  factory UiptvChannelSummary.fromJson(Map<String, dynamic> json) {
    return UiptvChannelSummary(
      dbId: json['dbId'] as String? ?? '',
      playbackId: json['playbackId'] as String? ?? '',
      name: json['name'] as String? ?? '',
      number: json['number'] as String? ?? '',
      logo: json['logo'] as String? ?? '',
      description: json['description'] as String? ?? '',
    );
  }

  final String dbId;
  final String playbackId;
  final String name;
  final String number;
  final String logo;
  final String description;
}

class UiptvChannelPage {
  const UiptvChannelPage({
    required this.items,
    required this.nextPage,
    required this.hasMore,
    required this.apiOffset,
  });

  final List<UiptvChannelSummary> items;
  final int nextPage;
  final bool hasMore;
  final int apiOffset;
}

class UiptvBookmarkSummary {
  const UiptvBookmarkSummary({
    required this.dbId,
    required this.categoryId,
    required this.channelName,
    required this.categoryTitle,
    required this.accountName,
    required this.logo,
  });

  factory UiptvBookmarkSummary.fromJson(Map<String, dynamic> json) {
    return UiptvBookmarkSummary(
      dbId: json['dbId'] as String? ?? '',
      categoryId: json['categoryId'] as String? ?? '',
      channelName: json['channelName'] as String? ?? '',
      categoryTitle: json['categoryTitle'] as String? ?? '',
      accountName: json['accountName'] as String? ?? '',
      logo: json['logo'] as String? ?? '',
    );
  }

  final String dbId;
  final String categoryId;
  final String channelName;
  final String categoryTitle;
  final String accountName;
  final String logo;
}

class UiptvBookmarkCategory {
  const UiptvBookmarkCategory({required this.id, required this.name});

  factory UiptvBookmarkCategory.fromJson(Map<String, dynamic> json) {
    return UiptvBookmarkCategory(
      id: json['id'] as String? ?? '',
      name: json['name'] as String? ?? '',
    );
  }

  final String id;
  final String name;
}

class UiptvWatchingNowEntry {
  const UiptvWatchingNowEntry({
    required this.isSeries,
    required this.playbackId,
    required this.accountName,
    required this.title,
    required this.image,
    required this.details,
    required this.season,
    required this.updatedAt,
  });

  final bool isSeries;
  final String playbackId;
  final String accountName;
  final String title;
  final String image;
  final String details;
  final String season;
  final int updatedAt;
}

class UiptvUpdateInfo {
  const UiptvUpdateInfo({
    required this.version,
    required this.url,
    required this.description,
    required this.isAvailable,
  });

  final String version;
  final String url;
  final String description;
  final bool isAvailable;
}

class UiptvWatchingNowEpisode {
  const UiptvWatchingNowEpisode({
    required this.playbackId,
    required this.title,
    required this.season,
    required this.episodeNum,
    required this.image,
    this.description = '',
    this.rating = '',
    this.releaseDate = '',
  });

  factory UiptvWatchingNowEpisode.fromJson(Map<String, dynamic> json) {
    return UiptvWatchingNowEpisode(
      playbackId: json['playbackId'] as String? ?? '',
      title: json['title'] as String? ?? '',
      season: json['season'] as String? ?? '',
      episodeNum: json['episodeNum'] as String? ?? '',
      image: json['image'] as String? ?? '',
      description: json['description'] as String? ?? '',
      rating: json['rating'] as String? ?? '',
      releaseDate: json['releaseDate'] as String? ?? '',
    );
  }

  final String playbackId;
  final String title;
  final String season;
  final String episodeNum;
  final String image;
  final String description;
  final String rating;
  final String releaseDate;
}

class UiptvWatchingNowSnapshot {
  const UiptvWatchingNowSnapshot({
    required this.entries,
    required this.offset,
    required this.hasMoreSeries,
    required this.hasMoreVod,
  });

  final List<UiptvWatchingNowEntry> entries;
  final int offset;
  final bool hasMoreSeries;
  final bool hasMoreVod;
}

class UiptvApiClient {
  UiptvApiClient({
    required String baseUrl,
    required this.bearerToken,
    http.Client? httpClient,
  }) : _baseUrl = baseUrl.replaceFirst(RegExp(r'/$'), ''),
       _httpClient = httpClient ?? http.Client();

  final String _baseUrl;
  final String bearerToken;
  final http.Client _httpClient;

  Future<Map<String, dynamic>> health() async {
    final json = await _get('/api/v1/health');
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> settings() async {
    final json = await _get('/api/v1/settings');
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> updateSettings(
    Map<String, dynamic> updates,
  ) async {
    final json = await _request('PUT', '/api/v1/settings', body: updates);
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> internalServerStatus() async {
    final json = await _get('/api/v1/settings/server');
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> setInternalServerRunning(bool running) async {
    final json = await _request(
      'POST',
      '/api/v1/settings/server',
      body: {'action': running ? 'start' : 'stop'},
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> filterLockStatus() async {
    final json = await _get('/api/v1/filter-lock');
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> filterLockAction({
    required String action,
    String? currentPassword,
    String? newPassword,
  }) async {
    final json = await _request(
      'POST',
      '/api/v1/filter-lock',
      body: _filterLockActionBody(action, currentPassword, newPassword),
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> filterLockFilters() async {
    final json = await _get('/api/v1/filter-lock/filters');
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> updateFilterLockFilters({
    required String categories,
    required String channels,
    required bool restrictionsActive,
  }) async {
    final json = await _request(
      'PUT',
      '/api/v1/filter-lock/filters',
      body: {
        'filterCategoriesList': categories,
        'filterChannelsList': channels,
        'restrictionsActive': restrictionsActive,
      },
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> startCacheReload({
    required List<String> accountIds,
    String failurePolicy = 'ask',
  }) async {
    final json = await _request(
      'POST',
      '/api/v1/cache-reloads',
      body: {'accountIds': accountIds, 'failurePolicy': failurePolicy},
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<List<UiptvAccountSummary>> cacheReloadAccounts() async {
    final list = await _get('/api/v1/cache-reloads/accounts') as List<dynamic>;
    return list
        .whereType<Map>()
        .map(
          (item) =>
              UiptvAccountSummary.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
  }

  Future<Map<String, dynamic>> cacheReloadJob(String jobId) async {
    final json = await _get(
      '/api/v1/cache-reloads/${Uri.encodeComponent(jobId)}',
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> stopCacheReload(String jobId) async {
    final json = await _request(
      'POST',
      '/api/v1/cache-reloads/${Uri.encodeComponent(jobId)}/stop',
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> decideCacheReloadFailure({
    required String jobId,
    required String decision,
  }) async {
    final json = await _request(
      'POST',
      '/api/v1/cache-reloads/${Uri.encodeComponent(jobId)}/decision',
      body: {'decision': decision},
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Map<String, dynamic> _filterLockActionBody(
    String action,
    String? currentPassword,
    String? newPassword,
  ) {
    final body = <String, dynamic>{'action': action};
    if (currentPassword != null) body['currentPassword'] = currentPassword;
    if (newPassword != null) body['newPassword'] = newPassword;
    return body;
  }

  Future<Map<String, dynamic>> about() async {
    final json = await _get('/api/v1/about');
    return Map<String, dynamic>.from(json as Map);
  }

  Future<UiptvUpdateInfo> checkForUpdates({
    required String currentVersion,
  }) async {
    final request = Uri.https(
      'api.github.com',
      '/repos/xixogo5105/uiptv/releases/latest',
    );
    final response = await _httpClient
        .get(
          request,
          headers: {
            'Accept': 'application/vnd.github+json',
            'X-GitHub-Api-Version': '2022-11-28',
            'User-Agent': 'UIPTV/$currentVersion',
          },
        )
        .timeout(const Duration(seconds: 20));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw UiptvApiException(
        response.statusCode,
        'Unable to retrieve the latest UIPTV release',
      );
    }
    final release = Map<String, dynamic>.from(jsonDecode(response.body) as Map);
    final rawVersion =
        (release['tag_name'] as String? ?? release['name'] as String? ?? '')
            .trim();
    final latestVersion = _normalizeVersion(
      rawVersion.isEmpty ? '0' : rawVersion,
    );
    final rawUrl = release['html_url'] as String? ?? '';
    final parsedUrl = Uri.tryParse(rawUrl);
    final url =
        parsedUrl != null &&
            parsedUrl.scheme == 'https' &&
            parsedUrl.host == 'github.com' &&
            parsedUrl.path.startsWith('/xixogo5105/uiptv/releases')
        ? parsedUrl.toString()
        : 'https://github.com/xixogo5105/uiptv/releases/latest';
    final name = release['name'] as String? ?? '';
    final description = (release['body'] as String? ?? '').trim();
    return UiptvUpdateInfo(
      version: latestVersion,
      url: url,
      description: description.isNotEmpty
          ? description
          : name.isNotEmpty
          ? name
          : latestVersion,
      isAvailable: isUpdateAvailable(currentVersion, latestVersion),
    );
  }

  static bool isUpdateAvailable(String currentVersion, String latestVersion) {
    try {
      final current = _versionParts(currentVersion);
      final latest = _versionParts(latestVersion);
      final length = current.length > latest.length
          ? current.length
          : latest.length;
      for (var index = 0; index < length; index++) {
        final currentPart = index < current.length ? current[index] : 0;
        final latestPart = index < latest.length ? latest[index] : 0;
        if (latestPart != currentPart) return latestPart > currentPart;
      }
    } on FormatException {
      return false;
    }
    return false;
  }

  static String _normalizeVersion(String value) => value
      .trim()
      .replaceFirst(RegExp(r'^[vV]'), '')
      .replaceFirst('-SNAPSHOT', '');

  static List<int> _versionParts(String value) =>
      _normalizeVersion(value)
          .split('.')
          .map((part) => int.parse(part))
          .toList(growable: false);

  Future<Uint8List> createBackupArchive() async {
    try {
      final response = await _httpClient
          .get(_requestUri('/api/v1/backups/current'), headers: _headers)
          .timeout(const Duration(minutes: 2));
      if (response.statusCode < 200 || response.statusCode >= 300) {
        _decodeResponse(response);
      }
      return response.bodyBytes;
    } on TimeoutException {
      throw UiptvApiException(0, 'The local UIPTV API request timed out');
    } on SocketException {
      throw UiptvApiException(0, 'Unable to connect to the local UIPTV API');
    } on http.ClientException {
      throw UiptvApiException(0, 'Unable to connect to the local UIPTV API');
    }
  }

  Future<Map<String, dynamic>> restoreBackupArchive(Uint8List archive) async {
    try {
      final request =
          http.Request('POST', _requestUri('/api/v1/backups/current'))
            ..headers.addAll({
              ..._headers,
              'Content-Type': 'application/zip',
              'Accept': 'application/json',
            })
            ..bodyBytes = archive;
      final streamed = await _httpClient
          .send(request)
          .timeout(const Duration(minutes: 2));
      final response = await http.Response.fromStream(streamed)
          .timeout(const Duration(minutes: 2));
      return Map<String, dynamic>.from(_decodeResponse(response) as Map);
    } on TimeoutException {
      throw UiptvApiException(0, 'The local UIPTV API request timed out');
    } on SocketException {
      throw UiptvApiException(0, 'Unable to connect to the local UIPTV API');
    } on http.ClientException {
      throw UiptvApiException(0, 'Unable to connect to the local UIPTV API');
    }
  }

  Future<List<String>> logs({bool web = false}) async {
    final response = await _get(
      '/api/v1/logs',
      queryParameters: {if (web) 'source': 'web'},
    ) as Map;
    return (response['lines'] as List<dynamic>? ?? const [])
        .whereType<String>()
        .toList(growable: false);
  }

  Future<void> clearLogs({bool web = false}) async {
    await _request(
      'DELETE',
      '/api/v1/logs',
      queryParameters: {if (web) 'source': 'web'},
    );
  }

  Future<List<UiptvAccountSummary>> accounts() async {
    final list = await _get('/api/v1/accounts') as List<dynamic>;
    return list
        .whereType<Map>()
        .map(
          (item) =>
              UiptvAccountSummary.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
  }

  Future<Map<String, dynamic>> accountDetails(String accountId) async {
    final json = await _get(
      '/api/v1/accounts/${Uri.encodeComponent(accountId)}',
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> updateAccount(
    String accountId,
    Map<String, dynamic> account,
  ) async {
    final json = await _request(
      'PUT',
      '/api/v1/accounts/${Uri.encodeComponent(accountId)}',
      body: account,
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<Map<String, dynamic>> createAccount(
    Map<String, dynamic> account,
  ) async {
    final json = await _request('POST', '/api/v1/accounts', body: account);
    return Map<String, dynamic>.from(json as Map);
  }

  Future<void> deleteAccount(String accountId) async {
    await _request(
      'DELETE',
      '/api/v1/accounts/${Uri.encodeComponent(accountId)}',
    );
  }

  Future<Map<String, dynamic>> removeCachedCategories({
    required String accountId,
    required String mode,
    required List<String> categoryIds,
  }) async {
    final json = await _request(
      'POST',
      '/api/v1/categories/remove',
      body: {'accountId': accountId, 'mode': mode, 'categoryIds': categoryIds},
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<List<UiptvCategorySummary>> categories({
    required String accountId,
    String mode = 'itv',
  }) async {
    final list = await _get(
      '/api/v1/categories',
      queryParameters: {'accountId': accountId, 'mode': mode},
    ) as List<dynamic>;
    return list
        .whereType<Map>()
        .map(
          (item) =>
              UiptvCategorySummary.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
  }

  Future<UiptvChannelPage> channels({
    required String accountId,
    required String categoryDbId,
    String mode = 'itv',
    int page = 1,
    int apiOffset = 0,
  }) async {
    final response = await _get(
      '/api/v1/channels',
      queryParameters: {
        'accountId': accountId,
        'categoryId': categoryDbId,
        'mode': mode,
        'page': '$page',
        'pageSize': '100',
        'apiOffset': '$apiOffset',
      },
    ) as Map;
    final list = response['items'] as List<dynamic>;
    return UiptvChannelPage(
      items: list
          .whereType<Map>()
          .map(
            (item) =>
                UiptvChannelSummary.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList(growable: false),
      nextPage: response['nextPage'] as int? ?? page + 1,
      hasMore: response['hasMore'] as bool? ?? false,
      apiOffset: response['apiOffset'] as int? ?? apiOffset,
    );
  }

  Future<Map<String, dynamic>> resolveLivePlayback({
    required String accountId,
    required String categoryDbId,
    required String channelDbId,
    String mode = 'itv',
  }) async {
    final result = await _get(
      '/api/v1/player',
      queryParameters: {
        'mode': mode,
        'accountId': accountId,
        'categoryId': categoryDbId,
        'channelId': channelDbId,
      },
    );
    return Map<String, dynamic>.from(result as Map);
  }

  Future<Map<String, dynamic>> playerGuide({
    String? accountId,
    String? categoryId,
    String? channelId,
    String? bookmarkId,
  }) async {
    final queryParameters = <String, String>{};
    void addIfPresent(String key, String? value) {
      if (value != null) queryParameters[key] = value;
    }

    addIfPresent('accountId', accountId);
    addIfPresent('categoryId', categoryId);
    addIfPresent('channelId', channelId);
    addIfPresent('bookmarkId', bookmarkId);
    final result = await _get(
      '/api/v1/epg/player',
      queryParameters: queryParameters,
    );
    return Map<String, dynamic>.from(result as Map);
  }

  Future<List<UiptvBookmarkCategory>> bookmarkCategories() async {
    final list = await _get('/api/v1/bookmark-categories') as List<dynamic>;
    return list
        .whereType<Map>()
        .map(
          (item) =>
              UiptvBookmarkCategory.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
  }

  Future<Map<String, String>> translations(Iterable<String> keys) async {
    final response = await _get(
      '/api/v1/translations',
      queryParameters: {'keys': keys.join(',')},
    ) as Map;
    return response.map(
      (key, value) => MapEntry(key.toString(), value?.toString() ?? ''),
    );
  }

  Future<void> addBookmarkCategory(String name) async {
    await _request('POST', '/api/v1/bookmark-categories', body: {'name': name});
  }

  Future<void> deleteBookmarkCategory(String categoryId) async {
    await _request(
      'DELETE',
      '/api/v1/bookmark-categories',
      queryParameters: {'categoryId': categoryId},
    );
  }

  Future<UiptvAccountImportResult> importAccounts({
    required String sourceType,
    required String data,
    bool groupAccounts = true,
    bool convertM3uToXtreme = true,
    bool startVerificationAfterParsing = true,
  }) async {
    final response = await _request(
      'POST',
      '/api/v1/accounts/import',
      body: {
        'sourceType': sourceType,
        'data': data,
        'groupAccounts': groupAccounts,
        'convertM3uToXtreme': convertM3uToXtreme,
        'startVerificationAfterParsing': startVerificationAfterParsing,
      },
    ) as Map;
    final items = response['items'] as List<dynamic>? ?? const [];
    final accounts = items
        .whereType<Map>()
        .map(
          (item) =>
              UiptvAccountSummary.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
    final verificationAccountIds =
        (response['verificationAccountIds'] as List<dynamic>? ?? const [])
            .whereType<String>()
            .toList(growable: false);
    return UiptvAccountImportResult(
      accounts: accounts,
      verificationAccountIds: verificationAccountIds,
    );
  }

  Future<List<UiptvBookmarkSummary>> bookmarks({
    int offset = 0,
    int limit = 100,
    String? categoryId,
  }) async {
    final response = await _get(
      '/api/v1/bookmarks',
      queryParameters: {
        'offset': '$offset',
        'limit': '$limit',
        if (categoryId != null && categoryId.isNotEmpty)
          'categoryId': categoryId,
      },
    ) as Map;
    final items = response['items'] as List<dynamic>;
    return items
        .whereType<Map>()
        .map(
          (item) =>
              UiptvBookmarkSummary.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
  }

  Future<List<UiptvBookmarkSummary>> allBookmarks({
    String? categoryId,
    int pageSize = 200,
  }) async {
    if (pageSize < 1 || pageSize > 200) {
      throw ArgumentError.value(
        pageSize,
        'pageSize',
        'must be between 1 and 200',
      );
    }
    final result = <UiptvBookmarkSummary>[];
    var offset = 0;
    var hasMore = true;
    while (hasMore) {
      final response = await _get(
        '/api/v1/bookmarks',
        queryParameters: {
          'offset': '$offset',
          'limit': '$pageSize',
          if (categoryId != null && categoryId.isNotEmpty)
            'categoryId': categoryId,
        },
      ) as Map;
      final items = (response['items'] as List<dynamic>? ?? const [])
          .whereType<Map>()
          .map(
            (item) =>
                UiptvBookmarkSummary.fromJson(Map<String, dynamic>.from(item)),
          )
          .toList(growable: false);
      result.addAll(items);
      hasMore = response['hasMore'] as bool? ?? false;
      final nextOffset = offset + items.length;
      if (hasMore && (items.isEmpty || nextOffset <= offset)) {
        throw UiptvApiException(
          502,
          'The favourites response did not advance pagination',
        );
      }
      offset = nextOffset;
    }
    return result;
  }

  Future<List<UiptvBookmarkSummary>> recentBookmarks() async {
    final response = await _get('/api/v1/bookmarks/recent') as Map;
    final items = response['items'] as List<dynamic>? ?? const [];
    return items
        .whereType<Map>()
        .map(
          (item) =>
              UiptvBookmarkSummary.fromJson(Map<String, dynamic>.from(item)),
        )
        .toList(growable: false);
  }

  Future<void> clearRecentBookmarks() async {
    await _request('DELETE', '/api/v1/bookmarks/recent');
  }

  Future<Map<String, dynamic>> resolveBookmarkPlayback(
    String bookmarkDbId,
  ) async {
    final result = await _get(
      '/api/v1/player',
      queryParameters: {'bookmarkId': bookmarkDbId},
    );
    return Map<String, dynamic>.from(result as Map);
  }

  Future<void> deleteBookmark(String bookmarkDbId) async {
    await _request(
      'DELETE',
      '/api/v1/bookmarks',
      queryParameters: {'bookmarkId': bookmarkDbId},
    );
  }

  Future<String> addLiveBookmark({
    required String accountDbId,
    required String categoryDbId,
    required String channelDbId,
  }) async {
    final response = await _request(
      'POST',
      '/api/v1/bookmarks',
      body: {
        'accountId': accountDbId,
        'categoryId': categoryDbId,
        'channelId': channelDbId,
      },
    ) as Map;
    return response['bookmarkId'] as String? ?? '';
  }

  Future<UiptvWatchingNowSnapshot> watchingNow({int offset = 0}) async {
    final response = await _get(
      '/api/v1/watching-now',
      queryParameters: {'offset': '$offset', 'limit': '100'},
    ) as Map;
    final entries = <UiptvWatchingNowEntry>[];
    for (final item in (response['series'] as List<dynamic>? ?? const [])) {
      if (item is Map) {
        final row = Map<String, dynamic>.from(item);
        final season = (row['season'] as String? ?? '').trim();
        final episodeNum = row['episodeNum'] as int? ?? 0;
        entries.add(
          UiptvWatchingNowEntry(
            isSeries: true,
            playbackId: row['playbackId'] as String? ?? '',
            accountName: row['accountName'] as String? ?? '',
            title: row['seriesTitle'] as String? ?? '',
            image: row['seriesPoster'] as String? ?? '',
            season: season,
            details: [
              row['episodeName'] as String? ?? '',
              if (season.isNotEmpty) 'S$season',
              if (episodeNum > 0) 'E$episodeNum',
            ].where((value) => value.isNotEmpty).join(' · '),
            updatedAt: row['updatedAt'] as int? ?? 0,
          ),
        );
      }
    }
    for (final item in (response['vod'] as List<dynamic>? ?? const [])) {
      if (item is Map) {
        final row = Map<String, dynamic>.from(item);
        final details = [
          row['releaseDate'] as String? ?? '',
          row['duration'] as String? ?? '',
          row['rating'] as String? ?? '',
        ].where((value) => value.isNotEmpty).join(' · ');
        entries.add(
          UiptvWatchingNowEntry(
            isSeries: false,
            playbackId: row['playbackId'] as String? ?? '',
            accountName: row['accountName'] as String? ?? '',
            title: row['vodName'] as String? ?? '',
            image: row['vodLogo'] as String? ?? '',
            season: '',
            details: details.isNotEmpty
                ? details
                : row['plot'] as String? ?? '',
            updatedAt: row['updatedAt'] as int? ?? 0,
          ),
        );
      }
    }
    entries.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return UiptvWatchingNowSnapshot(
      entries: entries,
      offset: response['offset'] as int? ?? offset,
      hasMoreSeries: response['hasMoreSeries'] as bool? ?? false,
      hasMoreVod: response['hasMoreVod'] as bool? ?? false,
    );
  }

  Future<List<UiptvWatchingNowEpisode>> watchingNowEpisodes(
    String playbackId,
  ) async {
    final episodes = <UiptvWatchingNowEpisode>[];
    var offset = 0;
    var hasMore = true;
    while (hasMore) {
      final response = await _get(
        '/api/v1/watching-now/episodes',
        queryParameters: {
          'playbackId': playbackId,
          'offset': '$offset',
          'limit': '200',
        },
      ) as Map;
      final items = (response['items'] as List<dynamic>? ?? const [])
          .whereType<Map>()
          .map(
            (item) => UiptvWatchingNowEpisode.fromJson(
              Map<String, dynamic>.from(item),
            ),
          )
          .toList(growable: false);
      episodes.addAll(items);
      hasMore = response['hasMore'] as bool? ?? false;
      final nextOffset =
          response['nextOffset'] as int? ?? offset + items.length;
      if (hasMore && nextOffset <= offset) {
        throw UiptvApiException(
          502,
          'The Watching Now episode response did not advance pagination',
        );
      }
      offset = nextOffset;
    }
    return episodes;
  }

  Future<Map<String, dynamic>> watchingNowDetails(String playbackId) async {
    final json = await _get(
      '/api/v1/watching-now/details',
      queryParameters: {'playbackId': playbackId},
    );
    return Map<String, dynamic>.from(json as Map);
  }

  Future<void> removeWatchingNow(String playbackId) async {
    await _request(
      'DELETE',
      '/api/v1/watching-now',
      queryParameters: {'playbackId': playbackId},
    );
  }

  Future<Map<String, dynamic>> resolveWatchingNowPlayback(
    String playbackId, {
    required String mode,
  }) async {
    final result = await _get(
      '/api/v1/player',
      queryParameters: {'watchingNowId': playbackId, 'mode': mode},
    );
    return Map<String, dynamic>.from(result as Map);
  }

  Future<dynamic> _get(String path, {Map<String, String>? queryParameters}) =>
      _request('GET', path, queryParameters: queryParameters);

  Future<dynamic> _request(
    String method,
    String path, {
    Map<String, String>? queryParameters,
    Object? body,
  }) async {
    final requestUri = _requestUri(path, queryParameters: queryParameters);
    try {
      final headers = {
        ..._headers,
        if (body != null) 'Content-Type': 'application/json',
      };
      final request = switch (method) {
        'DELETE' => _httpClient.delete(requestUri, headers: headers),
        'PUT' => _httpClient.put(
          requestUri,
          headers: headers,
          body: jsonEncode(body),
        ),
        'POST' => _httpClient.post(
          requestUri,
          headers: headers,
          body: jsonEncode(body),
        ),
        _ => _httpClient.get(requestUri, headers: headers),
      };
      final response = await request.timeout(const Duration(seconds: 45));
      if (response.statusCode == 204) {
        return null;
      }
      return _decodeResponse(response);
    } on TimeoutException {
      throw UiptvApiException(0, 'The local UIPTV API request timed out');
    } on SocketException {
      throw UiptvApiException(0, 'Unable to connect to the local UIPTV API');
    } on http.ClientException {
      throw UiptvApiException(0, 'Unable to connect to the local UIPTV API');
    }
  }

  Uri _requestUri(String path, {Map<String, String>? queryParameters}) {
    final uri = Uri.tryParse(_baseUrl);
    if (uri == null || !uri.hasScheme || uri.host.isEmpty) {
      throw UiptvApiException(
        0,
        'Local API address is not configured. Start UIPTV with the Flutter desktop launcher.',
      );
    }
    final address = InternetAddress.tryParse(uri.host);
    if (!(address?.isLoopback ?? false)) {
      throw UiptvApiException(
        0,
        'The UIPTV API address must use a loopback host',
      );
    }
    if (bearerToken.isEmpty) {
      throw UiptvApiException(
        0,
        'Local API authentication is missing. Start UIPTV with the Flutter desktop launcher.',
      );
    }
    return Uri.parse('$_baseUrl$path')
        .replace(queryParameters: queryParameters);
  }

  Map<String, String> get _headers => {
    'Accept': 'application/json',
    if (bearerToken.isNotEmpty) 'Authorization': 'Bearer $bearerToken',
  };

  dynamic _decodeResponse(http.Response response) {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      String message = 'Request failed';
      try {
        final decoded = jsonDecode(response.body);
        if (decoded is Map && decoded['error'] is String) {
          message = decoded['error'] as String;
        }
      } on FormatException {
        message = response.body.isEmpty ? message : response.body;
      }
      throw UiptvApiException(response.statusCode, message);
    }
    return jsonDecode(response.body);
  }
}
