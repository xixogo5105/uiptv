import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/api/uiptv_api_client.dart';

void main() {
  test('health sends bearer token and parses response', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/health');
        expect(request.headers['authorization'], 'Bearer test-token');
        return http.Response(
          jsonEncode({'status': 'ok', 'apiVersion': 'v1'}),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final result = await client.health();

    expect(result['status'], 'ok');
    expect(result['apiVersion'], 'v1');
  });

  test('player guide uses local channel identifiers and bearer auth', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/epg/player');
        expect(request.url.queryParameters, {
          'accountId': 'local-account',
          'categoryId': 'local-category',
          'channelId': 'local-channel',
        });
        expect(request.headers['authorization'], 'Bearer test-token');
        return http.Response(
          jsonEncode({'channelName': 'Station', 'now': null, 'next': null}),
          200,
        );
      }),
    );

    final guide = await client.playerGuide(
      accountId: 'local-account',
      categoryId: 'local-category',
      channelId: 'local-channel',
    );

    expect(guide['channelName'], 'Station');
    expect(guide['now'], isNull);
  });

  test(
    'desktop settings read and update use the dedicated desktop route',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          if (request.method == 'GET') {
            expect(request.url.path, '/api/v1/settings');
            return http.Response(
              jsonEncode({
                'themeMode': 'system',
                'languageLocale': 'en-US',
                'defaultPlayerPath': '__embedded_player__',
                'embeddedPlayerPath': '__embedded_player__',
                'webBrowserPlayerPath': '__web_browser_player__',
                'tmdbTokenConfigured': true,
                'filterLockConfigured': true,
              }),
              200,
            );
          }
          expect(request.method, 'PUT');
          expect(request.url.path, '/api/v1/settings');
          expect(jsonDecode(request.body), {
            'defaultPlayerPath': '__web_browser_player__',
            'embeddedPlayer': false,
            'themeMode': 'dark',
            'wideView': true,
          });
          return http.Response(
            jsonEncode({'themeMode': 'dark', 'wideView': true}),
            200,
          );
        }),
      );

      final current = await client.settings();
      final saved = await client.updateSettings({
        'defaultPlayerPath': '__web_browser_player__',
        'embeddedPlayer': false,
        'themeMode': 'dark',
        'wideView': true,
      });

      expect(current['tmdbTokenConfigured'], isTrue);
      expect(current['embeddedPlayerPath'], '__embedded_player__');
      expect(saved['themeMode'], 'dark');
    },
  );

  test(
    'filter lock protects and updates filter settings through explicit routes',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          if (request.method == 'GET' &&
              request.url.path == '/api/v1/filter-lock') {
            return http.Response(
              jsonEncode({
                'passwordConfigured': true,
                'unlocked': false,
                'unlockWindowMinutes': 10,
                'minimumPasswordLength': 6,
              }),
              200,
            );
          }
          if (request.method == 'POST') {
            expect(request.url.path, '/api/v1/filter-lock');
            expect(jsonDecode(request.body), {
              'action': 'unlock',
              'currentPassword': 'passphrase',
            });
            return http.Response(jsonEncode({'unlocked': true}), 200);
          }
          if (request.method == 'GET') {
            expect(request.url.path, '/api/v1/filter-lock/filters');
            return http.Response(
              jsonEncode({
                'filterCategoriesList': 'adult',
                'filterChannelsList': 'restricted',
                'restrictionsActive': true,
              }),
              200,
            );
          }
          expect(request.method, 'PUT');
          expect(request.url.path, '/api/v1/filter-lock/filters');
          expect(jsonDecode(request.body), {
            'filterCategoriesList': 'adult,xxx',
            'filterChannelsList': 'restricted',
            'restrictionsActive': false,
          });
          return http.Response(jsonEncode({'restrictionsActive': false}), 200);
        }),
      );

      final status = await client.filterLockStatus();
      final unlocked = await client.filterLockAction(
        action: 'unlock',
        currentPassword: 'passphrase',
      );
      final filters = await client.filterLockFilters();
      final saved = await client.updateFilterLockFilters(
        categories: 'adult,xxx',
        channels: 'restricted',
        restrictionsActive: false,
      );

      expect(status['passwordConfigured'], isTrue);
      expect(unlocked['unlocked'], isTrue);
      expect(filters['filterCategoriesList'], 'adult');
      expect(saved['restrictionsActive'], isFalse);
    },
  );

  test(
    'logs and about read desktop metadata and clear the selected log source',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          if (request.url.path == '/api/v1/about') {
            return http.Response(
              jsonEncode({
                'version': '1.1.5',
                'javaVersion': '25',
                'vmName': 'OpenJDK',
                'osName': 'macOS',
                'osArch': 'aarch64',
                'projectUrl': 'https://github.com/xixogo5105/uiptv',
                'helpUrl':
                    'https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md',
                'releaseUrl':
                    'https://github.com/xixogo5105/uiptv/releases/latest',
              }),
              200,
            );
          }
          if (request.method == 'GET') {
            expect(request.url.path, '/api/v1/logs');
            expect(request.url.queryParameters['source'], 'web');
            return http.Response(
              jsonEncode({
                'lines': ['Web request'],
              }),
              200,
            );
          }
          expect(request.method, 'DELETE');
          expect(request.url.path, '/api/v1/logs');
          expect(request.url.queryParameters['source'], 'web');
          return http.Response(jsonEncode({'status': 'ok'}), 200);
        }),
      );

      final about = await client.about();
      final logs = await client.logs(web: true);
      await client.clearLogs(web: true);

      expect(about['version'], '1.1.5');
      expect(about['javaVersion'], '25');
      expect(logs, ['Web request']);
    },
  );

  test(
    'backup client downloads ZIP bytes and restores them as binary',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          expect(request.headers['authorization'], 'Bearer test-token');
          if (request.method == 'GET') {
            expect(request.url.path, '/api/v1/backups/current');
            return http.Response.bytes([80, 75, 3, 4, 9], 200);
          }
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/backups/current');
          expect(request.headers['content-type'], 'application/zip');
          expect(request.bodyBytes, [80, 75, 3, 4, 9]);
          return http.Response(
            jsonEncode({'databaseBytes': 100, 'archiveBytes': 80}),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final archive = await client.createBackupArchive();
      final restored = await client.restoreBackupArchive(archive);

      expect(archive, [80, 75, 3, 4, 9]);
      expect(restored['databaseBytes'], 100);
    },
  );

  test(
    'cache reload jobs use local account IDs and explicit failure decisions',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          if (request.method == 'GET' &&
              request.url.path == '/api/v1/cache-reloads/accounts') {
            return http.Response(
              jsonEncode([
                {
                  'dbId': 'local-account',
                  'accountName': 'Disabled cache',
                  'type': 'M3U8_URL',
                  'pinToTop': false,
                  'cacheEnabled': false,
                  'webPlaybackProxy': false,
                },
              ]),
              200,
            );
          }
          if (request.method == 'POST' &&
              request.url.path == '/api/v1/cache-reloads') {
            expect(jsonDecode(request.body), {
              'accountIds': ['local-account'],
              'failurePolicy': 'ask',
            });
            return http.Response(
              jsonEncode({'jobId': 'job-1', 'status': 'queued'}),
              202,
            );
          }
          if (request.method == 'GET') {
            expect(request.url.path, '/api/v1/cache-reloads/job-1');
            return http.Response(
              jsonEncode({'jobId': 'job-1', 'status': 'waiting_for_decision'}),
              200,
            );
          }
          if (request.url.path.endsWith('/decision')) {
            expect(request.method, 'POST');
            expect(jsonDecode(request.body), {
              'decision': 'markBadIgnoreDomain',
            });
            return http.Response(
              jsonEncode({'jobId': 'job-1', 'status': 'running'}),
              200,
            );
          }
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/cache-reloads/job-1/stop');
          return http.Response(
            jsonEncode({'jobId': 'job-1', 'status': 'stopped'}),
            200,
          );
        }),
      );

      final accounts = await client.cacheReloadAccounts();
      final started = await client.startCacheReload(
        accountIds: [accounts.single.dbId],
      );
      final status = await client.cacheReloadJob(started['jobId'] as String);
      final decision = await client.decideCacheReloadFailure(
        jobId: started['jobId'] as String,
        decision: 'markBadIgnoreDomain',
      );
      final stopped = await client.stopCacheReload(started['jobId'] as String);

      expect(status['status'], 'waiting_for_decision');
      expect(accounts.single.cacheEnabled, isFalse);
      expect(decision['status'], 'running');
      expect(stopped['status'], 'stopped');
    },
  );

  test('accounts parse safe account summaries', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/accounts');
        return http.Response(
          jsonEncode([
            {
              'dbId': '1',
              'accountName': 'Main',
              'type': 'M3U8_URL',
              'typeDisplay': 'M3U8 URL',
              'pinToTop': true,
              'webPlaybackProxy': false,
              'categoryCount': 12,
              'channelCount': 394,
              'categoryCountDisplay': '12',
              'channelCountDisplay': '394',
              'expiryDate': '14 Apr 27',
              'expiryState': 'ok',
              'pinSvgStemPath': 'm 289.99122,309.99418 c 1,2 3,4 5,6 z',
              'pinSvgHeadPath': 'm 56.34936,106.22036 c 1,2 3,4 5,6 z',
              'pinSvgStemFill': '#cad2d2',
              'pinSvgHeadFill': '#e30000',
              'pinSvgScale': 0.075,
            },
          ]),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final accounts = await client.accounts();

    expect(accounts, hasLength(1));
    expect(accounts.first.dbId, '1');
    expect(accounts.first.accountName, 'Main');
    expect(accounts.first.typeDisplay, 'M3U8 URL');
    expect(accounts.first.categoryCount, 12);
    expect(accounts.first.channelCount, 394);
    expect(accounts.first.channelCountDisplay, '394');
    expect(accounts.first.expiryDate, '14 Apr 27');
    expect(accounts.first.pinSvgHeadFill, '#e30000');
  });

  test('account detail, update, and delete use account row IDs', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/accounts/local%2Faccount');
        if (request.method == 'GET') {
          return http.Response(
            jsonEncode({'dbId': 'local/account', 'accountName': 'Main'}),
            200,
          );
        }
        if (request.method == 'PUT') {
          expect(jsonDecode(request.body), {'accountName': 'Updated'});
          return http.Response(
            jsonEncode({'dbId': 'local/account', 'accountName': 'Updated'}),
            200,
          );
        }
        expect(request.method, 'DELETE');
        return http.Response(jsonEncode({'status': 'deleted'}), 200);
      }),
    );

    final detail = await client.accountDetails('local/account');
    final updated = await client.updateAccount('local/account', {
      'accountName': 'Updated',
    });
    await client.deleteAccount('local/account');

    expect(detail['accountName'], 'Main');
    expect(updated['accountName'], 'Updated');
  });

  test(
    'account creation posts editable fields to the account collection',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/accounts');
          expect(jsonDecode(request.body), {
            'accountName': 'New account',
            'type': 'M3U8_URL',
          });
          return http.Response(
            jsonEncode({'dbId': 'created-row', 'accountName': 'New account'}),
            201,
          );
        }),
      );

      final created = await client.createAccount({
        'accountName': 'New account',
        'type': 'M3U8_URL',
      });

      expect(created['dbId'], 'created-row');
    },
  );

  test(
    'category cache removal sends account, mode, and local category IDs',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/categories/remove');
          expect(jsonDecode(request.body), {
            'accountId': 'local-account',
            'mode': 'itv',
            'categoryIds': ['local-category'],
          });
          return http.Response(jsonEncode({'removedCategoryCount': 1}), 200);
        }),
      );

      final result = await client.removeCachedCategories(
        accountId: 'local-account',
        mode: 'itv',
        categoryIds: ['local-category'],
      );

      expect(result['removedCategoryCount'], 1);
    },
  );

  test('categories and channels use local row identifiers', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/categories') {
          expect(request.url.queryParameters['accountId'], 'local-account');
          expect(request.url.queryParameters['mode'], 'itv');
          return http.Response(
            jsonEncode([
              {
                'dbId': 'local-category',
                'title': 'News',
                'alias': 'news',
                'activeSub': false,
              },
            ]),
            200,
          );
        }
        expect(request.url.path, '/api/v1/channels');
        expect(request.url.queryParameters['categoryId'], 'local-category');
        expect(request.url.queryParameters['pageSize'], '100');
        final page = request.url.queryParameters['page'];
        expect(page, anyOf('1', '2'));
        if (page == '2') {
          expect(request.url.queryParameters['apiOffset'], '100');
        }
        return http.Response(
          jsonEncode({
            'items': [
              {
                'dbId': page == '1' ? 'local-channel' : 'local-channel-2',
                if (page == '1') 'playbackId': 'ephemeral-playback-reference',
                'name': page == '1' ? 'Public News' : 'Public News 2',
                'number': page == '1' ? '1' : '2',
                'logo': '',
                'description': '',
                'watched': false,
                'hd': 1,
                'status': 1,
              },
            ],
            'nextPage': page == '1' ? 2 : 3,
            'hasMore': page == '1',
            'apiOffset': page == '1' ? 100 : 200,
          }),
          200,
        );
      }),
    );

    final categories = await client.categories(accountId: 'local-account');
    final channels = await client.channels(
      accountId: 'local-account',
      categoryDbId: categories.first.dbId,
    );

    expect(categories.single.title, 'News');
    expect(channels.items.single.dbId, 'local-channel');
    expect(channels.items.single.playbackId, 'ephemeral-playback-reference');
    expect(channels.items.single.name, 'Public News');
    expect(channels.hasMore, isTrue);
    expect(channels.nextPage, 2);
    expect(channels.apiOffset, 100);
    final nextPage = await client.channels(
      accountId: 'local-account',
      categoryDbId: categories.first.dbId,
      page: channels.nextPage,
      apiOffset: channels.apiOffset,
    );
    expect(nextPage.items.single.dbId, 'local-channel-2');
    expect(nextPage.hasMore, isFalse);
  });

  test('unconfigured API bootstrap fails with actionable message', () async {
    final client = UiptvApiClient(baseUrl: '', bearerToken: '');

    await expectLater(
      client.health(),
      throwsA(
        isA<UiptvApiException>().having(
          (error) => error.message,
          'message',
          contains('Start UIPTV with the Flutter desktop launcher'),
        ),
      ),
    );
  });

  test(
    'API client refuses to send its bearer token to a non-loopback host',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'https://example.com',
        bearerToken: 'test-token',
        httpClient: MockClient((_) async {
          fail('The request must not be sent to a non-loopback host');
        }),
      );

      await expectLater(
        client.health(),
        throwsA(
          isA<UiptvApiException>().having(
            (error) => error.message,
            'message',
            contains('loopback host'),
          ),
        ),
      );
    },
  );

  test('favourites list, play, and removal use opaque bookmark IDs', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.headers['authorization'], 'Bearer test-token');
        if (request.method == 'GET' &&
            request.url.path == '/api/v1/bookmarks') {
          expect(request.url.queryParameters['limit'], '100');
          return http.Response(
            jsonEncode({
              'items': [
                {
                  'dbId': 'local-bookmark',
                  'categoryId': 'saved-category',
                  'channelName': 'Station',
                  'categoryTitle': 'News',
                  'accountName': 'Main',
                  'logo': '',
                },
              ],
              'offset': 0,
              'limit': 100,
              'hasMore': false,
            }),
            200,
          );
        }
        if (request.method == 'GET' && request.url.path == '/api/v1/player') {
          expect(request.url.queryParameters['bookmarkId'], 'local-bookmark');
          return http.Response(
            jsonEncode({'url': 'https://stream.test/live.m3u8'}),
            200,
          );
        }
        if (request.method == 'POST' &&
            request.url.path == '/api/v1/bookmarks') {
          expect(request.headers['authorization'], 'Bearer test-token');
          expect(jsonDecode(request.body), {
            'accountId': 'local-account',
            'categoryId': 'local-category',
            'channelId': 'local-channel',
          });
          return http.Response(
            jsonEncode({'status': 'saved', 'bookmarkId': 'local-bookmark'}),
            200,
          );
        }
        expect(request.method, 'DELETE');
        expect(request.url.queryParameters['bookmarkId'], 'local-bookmark');
        return http.Response('', 204);
      }),
    );

    final bookmarks = await client.bookmarks();
    final playback = await client.resolveBookmarkPlayback(
      bookmarks.single.dbId,
    );
    final savedBookmarkId = await client.addLiveBookmark(
      accountDbId: 'local-account',
      categoryDbId: 'local-category',
      channelDbId: 'local-channel',
    );
    await client.deleteBookmark(bookmarks.single.dbId);

    expect(bookmarks.single.channelName, 'Station');
    expect(bookmarks.single.categoryId, 'saved-category');
    expect(playback['url'], 'https://stream.test/live.m3u8');
    expect(savedBookmarkId, 'local-bookmark');
  });

  test(
    'favourite categories, category-filtered lists, and recent history',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          expect(request.headers['authorization'], 'Bearer test-token');
          if (request.method == 'GET' &&
              request.url.path == '/api/v1/bookmark-categories') {
            return http.Response(
              jsonEncode([
                {'id': 'saved-category', 'name': 'Cricket'},
              ]),
              200,
            );
          }
          if (request.method == 'GET' &&
              request.url.path == '/api/v1/bookmarks') {
            expect(request.url.queryParameters['categoryId'], 'saved-category');
            return http.Response(
              jsonEncode({
                'items': [
                  {
                    'dbId': 'bookmark-1',
                    'categoryId': 'saved-category',
                    'channelName': 'Station',
                    'categoryTitle': 'Sports',
                    'accountName': 'Main',
                    'logo': '',
                  },
                ],
                'offset': 0,
                'limit': 100,
                'hasMore': false,
              }),
              200,
            );
          }
          if (request.method == 'GET' &&
              request.url.path == '/api/v1/bookmarks/recent') {
            return http.Response(
              jsonEncode({
                'items': [
                  {
                    'dbId': 'recent:12',
                    'channelName': 'Recent station',
                    'categoryTitle': 'News',
                    'accountName': 'Main',
                    'logo': '',
                  },
                ],
                'count': 1,
              }),
              200,
            );
          }
          expect(request.method, 'DELETE');
          expect(request.url.path, '/api/v1/bookmarks/recent');
          return http.Response(jsonEncode({'status': 'ok'}), 200);
        }),
      );

      final categories = await client.bookmarkCategories();
      final bookmarks = await client.bookmarks(
        categoryId: categories.single.id,
      );
      final recent = await client.recentBookmarks();
      await client.clearRecentBookmarks();

      expect(categories.single.name, 'Cricket');
      expect(bookmarks.single.categoryId, 'saved-category');
      expect(recent.single.dbId, 'recent:12');
      expect(recent.single.channelName, 'Recent station');
    },
  );

  test('favourite management and translations use the local v1 API', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.headers['authorization'], 'Bearer test-token');
        if (request.method == 'GET') {
          expect(request.url.path, '/api/v1/translations');
          expect(
            request.url.queryParameters['keys'],
            'bookmarkRecent,autoManage',
          );
          return http.Response(
            jsonEncode({'bookmarkRecent': 'Recent', 'autoManage': 'Manage'}),
            200,
          );
        }
        if (request.method == 'POST') {
          expect(request.url.path, '/api/v1/bookmark-categories');
          expect(jsonDecode(request.body), {'name': 'Cricket'});
          return http.Response(jsonEncode({'status': 'created'}), 201);
        }
        expect(request.method, 'DELETE');
        expect(request.url.path, '/api/v1/bookmark-categories');
        expect(request.url.queryParameters['categoryId'], 'category-1');
        return http.Response(jsonEncode({'status': 'deleted'}), 200);
      }),
    );

    final translations = await client.translations([
      'bookmarkRecent',
      'autoManage',
    ]);
    await client.addBookmarkCategory('Cricket');
    await client.deleteBookmarkCategory('category-1');

    expect(translations['bookmarkRecent'], 'Recent');
    expect(translations['autoManage'], 'Manage');
  });

  test('all saved bookmarks fetches every bounded page', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/bookmarks');
        expect(request.url.queryParameters['limit'], '2');
        expect(request.url.queryParameters['categoryId'], 'category-1');
        final offset = request.url.queryParameters['offset'];
        expect(offset, anyOf('0', '2'));
        final names = offset == '0' ? ['One', 'Two'] : ['Three'];
        return http.Response(
          jsonEncode({
            'items': [
              for (var index = 0; index < names.length; index++)
                {
                  'dbId': 'bookmark-${index + (offset == '0' ? 1 : 3)}',
                  'categoryId': 'category-1',
                  'channelName': names[index],
                  'categoryTitle': 'Sports',
                  'accountName': 'Main',
                  'logo': '',
                },
            ],
            'hasMore': offset == '0',
          }),
          200,
        );
      }),
    );

    final bookmarks = await client.allBookmarks(
      categoryId: 'category-1',
      pageSize: 2,
    );

    expect(bookmarks.map((bookmark) => bookmark.channelName), [
      'One',
      'Two',
      'Three',
    ]);
  });

  test(
    'bulk account import submits parser options and returns safe summaries',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          expect(request.method, 'POST');
          expect(request.url.path, '/api/v1/accounts/import');
          expect(jsonDecode(request.body), {
            'sourceType': 'm3u',
            'data': 'https://provider.test/get.php?username=user&password=secret&type=m3u',
            'groupAccounts': false,
            'convertM3uToXtreme': false,
            'startVerificationAfterParsing': false,
          });
          return http.Response(
            jsonEncode({
              'items': [
                {
                  'dbId': 'local-account',
                  'accountName': 'Provider',
                  'type': 'M3U8_URL',
                  'pinToTop': false,
                  'webPlaybackProxy': false,
                },
              ],
              'count': 1,
              'verificationAccountIds': [],
            }),
            201,
          );
        }),
      );

      final imported = await client.importAccounts(
        sourceType: 'm3u',
        data: 'https://provider.test/get.php?username=user&password=secret&type=m3u',
        groupAccounts: false,
        convertM3uToXtreme: false,
        startVerificationAfterParsing: false,
      );

      expect(imported.accounts.single.dbId, 'local-account');
      expect(imported.accounts.single.accountName, 'Provider');
      expect(imported.verificationAccountIds, isEmpty);
    },
  );

  test('watching now parses display-safe series and VOD summaries', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/watching-now') {
          expect(request.url.queryParameters['offset'], '0');
          expect(request.url.queryParameters['limit'], '100');
          return http.Response(
            jsonEncode({
              'series': [
                {
                  'playbackId': 'resume-series',
                  'accountName': 'Main',
                  'seriesTitle': 'Example Series',
                  'seriesPoster': '',
                  'episodeName': 'Episode 2',
                  'season': '1',
                  'episodeNum': 2,
                  'updatedAt': 20,
                },
              ],
              'vod': [
                {
                  'playbackId': 'resume-vod',
                  'accountName': 'Movies',
                  'vodName': 'Example Film',
                  'vodLogo': '',
                  'plot': 'A film',
                  'releaseDate': '2025',
                  'rating': '8.0',
                  'duration': '90',
                  'updatedAt': 10,
                },
              ],
              'hasMoreSeries': false,
              'hasMoreVod': false,
            }),
            200,
          );
        }
        expect(request.url.path, '/api/v1/player');
        expect(request.url.queryParameters['watchingNowId'], 'resume-series');
        expect(request.url.queryParameters['mode'], 'series');
        return http.Response(
          jsonEncode({'url': 'https://stream.test/episode'}),
          200,
        );
      }),
    );

    final snapshot = await client.watchingNow();
    final playback = await client.resolveWatchingNowPlayback(
      snapshot.entries.first.playbackId,
      mode: 'series',
    );

    expect(snapshot.entries, hasLength(2));
    expect(snapshot.entries.first.title, 'Example Series');
    expect(snapshot.entries.first.details, 'Episode 2 · S1 · E2');
    expect(snapshot.entries.first.playbackId, 'resume-series');
    expect(snapshot.entries.last.title, 'Example Film');
    expect(snapshot.entries.last.details, '2025 · 90 · 8.0');
    expect(snapshot.hasMoreSeries, isFalse);
    expect(snapshot.hasMoreVod, isFalse);
    expect(playback['url'], 'https://stream.test/episode');
  });

  test(
    'watching episodes use opaque references and bounded pagination',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'test-token',
        httpClient: MockClient((request) async {
          expect(request.url.path, '/api/v1/watching-now/episodes');
          expect(request.url.queryParameters['playbackId'], 'series-reference');
          expect(request.url.queryParameters['limit'], '200');
          final offset = request.url.queryParameters['offset'];
          if (offset == '0') {
            return http.Response(
              jsonEncode({
                'items': [
                  {
                    'playbackId': 'episode-one',
                    'title': 'Episode One',
                    'season': '1',
                    'episodeNum': '1',
                    'image': '',
                  },
                ],
                'offset': 0,
                'nextOffset': 1,
                'hasMore': true,
              }),
              200,
            );
          }
          expect(offset, '1');
          return http.Response(
            jsonEncode({
              'items': [
                {
                  'playbackId': 'episode-two',
                  'title': 'Episode Two',
                  'season': '2',
                  'episodeNum': '1',
                  'image': '',
                },
              ],
              'offset': 1,
              'nextOffset': 2,
              'hasMore': false,
            }),
            200,
          );
        }),
      );

      final episodes = await client.watchingNowEpisodes('series-reference');

      expect(episodes, hasLength(2));
      expect(episodes.map((episode) => episode.playbackId), [
        'episode-one',
        'episode-two',
      ]);
      expect(episodes.last.season, '2');
    },
  );

  test(
    'release update check parses the current official release safely',
    () async {
      final client = UiptvApiClient(
        baseUrl: 'http://127.0.0.1:8888',
        bearerToken: 'desktop-api-token',
        httpClient: MockClient((request) async {
          expect(request.url.host, 'api.github.com');
          expect(request.url.path, '/repos/xixogo5105/uiptv/releases/latest');
          expect(request.headers['authorization'], isNull);
          expect(request.headers['accept'], 'application/vnd.github+json');
          return http.Response(
            jsonEncode({
              'tag_name': 'v1.2.0',
              'name': 'UIPTV 1.2.0',
              'body': 'New version notes',
              'html_url':
                  'https://github.com/xixogo5105/uiptv/releases/tag/v1.2.0',
            }),
            200,
          );
        }),
      );

      final update = await client.checkForUpdates(currentVersion: '1.1.5');

      expect(update.version, '1.2.0');
      expect(update.description, 'New version notes');
      expect(update.isAvailable, isTrue);
      expect(
        update.url,
        'https://github.com/xixogo5105/uiptv/releases/tag/v1.2.0',
      );
      expect(UiptvApiClient.isUpdateAvailable('1.10.0', '1.9.9'), isFalse);
    },
  );

  test('series detail requests use the opaque watching reference', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/watching-now/details');
        expect(request.url.queryParameters['playbackId'], 'series-reference');
        return http.Response(
          jsonEncode({
            'name': 'Example Series',
            'poster': '',
            'plot': 'Series plot',
            'rating': '8.5',
            'genre': 'Drama',
            'releaseDate': '2024',
            'episodesCount': 12,
          }),
          200,
        );
      }),
    );

    final details = await client.watchingNowDetails('series-reference');

    expect(details['name'], 'Example Series');
    expect(details['plot'], 'Series plot');
    expect(details['rating'], '8.5');
  });

  test('removing a Watching item sends only its opaque reference', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient((request) async {
        expect(request.method, 'DELETE');
        expect(request.url.path, '/api/v1/watching-now');
        expect(request.url.queryParameters, {'playbackId': 'opaque-reference'});
        expect(request.body, isEmpty);
        return http.Response(jsonEncode({'status': 'ok'}), 200);
      }),
    );

    await client.removeWatchingNow('opaque-reference');
  });

  test('authentication failure becomes typed API exception', () async {
    final client = UiptvApiClient(
      baseUrl: 'http://127.0.0.1:8888',
      bearerToken: 'test-token',
      httpClient: MockClient(
        (_) async => http.Response(
          jsonEncode({'error': 'Missing or invalid desktop API token'}),
          401,
          headers: {'content-type': 'application/json'},
        ),
      ),
    );

    await expectLater(
      client.health(),
      throwsA(
        isA<UiptvApiException>().having(
          (error) => error.statusCode,
          'statusCode',
          401,
        ),
      ),
    );
  });
}
