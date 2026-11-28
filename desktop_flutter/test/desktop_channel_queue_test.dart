import 'package:desktop_flutter/api/uiptv_api_client.dart';
import 'package:desktop_flutter/player/desktop_channel_queue.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'visible and playable channel queue preserves filtered catalogue order',
    () {
      final channels = [
        _channel('sports-1', 'Sports One'),
        _channel('news-hidden', 'News Unavailable', playable: false),
        _channel('news-1', 'News One'),
        _channel('news-2', 'Other Channel', description: 'News network'),
      ];

      final queue = DesktopChannelQueue.filter(
        channels: channels,
        query: ' news ',
        playableOnly: true,
      );

      expect(queue.map((channel) => channel.dbId), ['news-1', 'news-2']);
    },
  );

  test(
    'pagination appends only matching playable items and avoids duplicates',
    () {
      final current = [_channel('news-1', 'News One')];
      final nextPage = [
        _channel('news-1', 'Duplicate News One'),
        _channel('news-2', 'News Two'),
        _channel('sports-2', 'Sports Two'),
        _channel('news-hidden', 'News Unavailable', playable: false),
      ];

      final queue = DesktopChannelQueue.appendFiltered(
        current: current,
        nextPage: nextPage,
        query: 'news',
      );

      expect(queue.map((channel) => channel.dbId), ['news-1', 'news-2']);
    },
  );
}

UiptvChannelSummary _channel(
  String dbId,
  String name, {
  String description = '',
  bool playable = true,
}) => UiptvChannelSummary(
  dbId: dbId,
  playbackId: '',
  name: name,
  number: '',
  logo: '',
  description: description,
  playable: playable,
);
