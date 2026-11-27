import 'dart:math' as math;

class DesktopPlayerStreamInfo {
  static const _resolutionProfiles = [
    (1280, 720, '720p'),
    (1920, 1080, '1080p'),
    (2560, 1440, '2K'),
    (3840, 2160, '4K UHD'),
    (7680, 4320, '8K UHD'),
  ];

  static const _codecLabels = <String, List<String>>{
    'H.264/AVC': ['h.264', 'h264', 'avc'],
    'H.265/HEVC': ['h.265', 'h265', 'hevc'],
    'MPEG-2': ['mpeg2', 'mpeg-2'],
    'MPEG-4': ['mpeg4', 'mpeg-4', 'mp4v'],
    'AV1': ['av1', 'av01'],
    'AAC': ['aac'],
    'E-AC-3': ['eac3', 'e-ac-3'],
    'AC-3': ['ac3', 'ac-3'],
    'Opus': ['opus'],
    'FLAC': ['flac'],
    'MP3': ['mp3'],
    'TrueHD': ['truehd'],
  };

  static String build({
    required int? width,
    required int? height,
    required String videoCodec,
    required String audioCodec,
  }) {
    final details = <String>[];
    final resolution = _resolutionLabel(width, height);
    if (resolution.isNotEmpty) details.add(resolution);
    final videoLabel = normalizeCodec(videoCodec);
    if (videoLabel.isNotEmpty) details.add(videoLabel);
    final audioLabel = normalizeCodec(audioCodec);
    if (audioLabel.isNotEmpty) details.add(audioLabel);
    return details.join(' · ');
  }

  static String guideSummary(Map<String, dynamic>? guide) {
    final now = guide?['now'];
    if (now is! Map) return '';
    final programme = Map<String, dynamic>.from(now);
    final title = programme['title']?.toString().trim() ?? '';
    if (title.isEmpty) return '';
    final start = _programmeTime(programme['start']);
    final stop = _programmeTime(programme['stop']);
    if (start == null || stop == null) return title;
    return '${_formatTime(start)}–${_formatTime(stop)} $title';
  }

  static String normalizeCodec(String codec) {
    final normalized = codec.trim().toLowerCase();
    if (normalized.isEmpty) return '';
    for (final entry in _codecLabels.entries) {
      if (entry.value.any(normalized.contains)) return entry.key;
    }
    return codec.trim().toUpperCase();
  }

  static String _resolutionLabel(int? width, int? height) {
    if (width == null || height == null || width <= 0 || height <= 0) {
      return '';
    }
    if (height < 701) return '${width}x$height';
    for (final profile in _resolutionProfiles) {
      final widthTolerance = math.max(24, (profile.$1 * 0.03).round());
      final heightTolerance = math.max(24, (profile.$2 * 0.03).round());
      if ((width - profile.$1).abs() <= widthTolerance &&
          (height - profile.$2).abs() <= heightTolerance) {
        return profile.$3;
      }
    }
    return '${width}x$height';
  }

  static DateTime? _programmeTime(Object? value) {
    if (value is num) {
      return DateTime.fromMillisecondsSinceEpoch(value.toInt()).toLocal();
    }
    final text = value?.toString().trim() ?? '';
    if (text.isEmpty) return null;
    final epoch = int.tryParse(text);
    if (epoch != null) {
      final milliseconds = text.length <= 10 ? epoch * 1000 : epoch;
      return DateTime.fromMillisecondsSinceEpoch(milliseconds).toLocal();
    }
    return DateTime.tryParse(text)?.toLocal();
  }

  static String _formatTime(DateTime value) =>
      '${value.hour}:${value.minute.toString().padLeft(2, '0')}';
}
