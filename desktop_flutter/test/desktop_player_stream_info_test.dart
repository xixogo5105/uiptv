import 'package:flutter_test/flutter_test.dart';

import 'package:desktop_flutter/player/desktop_player_stream_info.dart';

void main() {
  test('stream details use JavaFX resolution and codec labels', () {
    expect(
      DesktopPlayerStreamInfo.build(
        width: 1920,
        height: 1080,
        videoCodec: 'avc1.640028',
        audioCodec: 'aac',
      ),
      '1080p · H.264/AVC · AAC',
    );
    expect(
      DesktopPlayerStreamInfo.build(
        width: 3840,
        height: 2160,
        videoCodec: 'hevc',
        audioCodec: 'mp4a',
      ),
      '4K UHD · H.265/HEVC · MP4A',
    );
    expect(
      DesktopPlayerStreamInfo.build(
        width: 640,
        height: 480,
        videoCodec: '',
        audioCodec: '',
      ),
      '640x480',
    );
  });

  test('guide summary shows only the current programme and local times', () {
    final start = DateTime(2026, 11, 27, 18, 30).millisecondsSinceEpoch;
    final stop = DateTime(2026, 11, 27, 19).millisecondsSinceEpoch;

    expect(
      DesktopPlayerStreamInfo.guideSummary({
        'now': {'title': 'Tonight', 'start': start, 'stop': stop},
        'next': {'title': 'Later', 'start': stop, 'stop': stop + 1800000},
      }),
      '18:30–19:00 Tonight',
    );
    expect(DesktopPlayerStreamInfo.guideSummary(null), isEmpty);
  });
}
