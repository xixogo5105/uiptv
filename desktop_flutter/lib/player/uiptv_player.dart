import 'dart:async';

import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

class UiptvPlayer {
  UiptvPlayer({
    this._onStateChanged,
    this.onPlaybackStarted,
    this.onPlaybackFailure,
    this.onPlaybackCompleted,
  }) {
    player = Player(
      configuration: const PlayerConfiguration(bufferSize: 64 * 1024 * 1024),
    );
    controller = VideoController(
      player,
      configuration: const VideoControllerConfiguration(
        enableHardwareAcceleration: true,
        hwdec: 'auto-safe',
      ),
    );
    _playingSubscription = player.stream.playing.listen((value) {
      playing = value;
      if (value && !_stopping) onPlaybackStarted?.call();
      _onStateChanged?.call();
    });
    _bufferingSubscription = player.stream.buffering.listen((value) {
      buffering = value;
      _onStateChanged?.call();
    });
    _errorSubscription = player.stream.error.listen((value) {
      error = value;
      if (value.isNotEmpty && !_stopping) onPlaybackFailure?.call(value);
      _onStateChanged?.call();
    });
    _completedSubscription = player.stream.completed.listen((value) {
      if (value && !_stopping) onPlaybackCompleted?.call();
    });
    _widthSubscription = player.stream.width.listen((value) {
      width = value;
      _onStateChanged?.call();
    });
    _heightSubscription = player.stream.height.listen((value) {
      height = value;
      _onStateChanged?.call();
    });
    _positionSubscription = player.stream.position.listen((value) {
      position = value;
      _onStateChanged?.call();
    });
    _durationSubscription = player.stream.duration.listen((value) {
      duration = value;
      _onStateChanged?.call();
    });
    _tracksSubscription = player.stream.tracks.listen((value) {
      tracks = value;
      _onStateChanged?.call();
    });
    _trackSubscription = player.stream.track.listen((_) {
      _onStateChanged?.call();
    });
  }

  final void Function()? _onStateChanged;
  final void Function()? onPlaybackStarted;
  final void Function(String)? onPlaybackFailure;
  final void Function()? onPlaybackCompleted;
  late final Player player;
  late final VideoController controller;
  late final StreamSubscription<bool> _playingSubscription;
  late final StreamSubscription<bool> _bufferingSubscription;
  late final StreamSubscription<String> _errorSubscription;
  late final StreamSubscription<bool> _completedSubscription;
  late final StreamSubscription<int?> _widthSubscription;
  late final StreamSubscription<int?> _heightSubscription;
  late final StreamSubscription<Duration> _positionSubscription;
  late final StreamSubscription<Duration> _durationSubscription;
  late final StreamSubscription<Tracks> _tracksSubscription;
  late final StreamSubscription<Track> _trackSubscription;

  bool playing = false;
  bool buffering = false;
  String? error;
  int? width;
  int? height;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;
  Tracks tracks = const Tracks();
  Media? _currentMedia;
  double _volume = 50;
  bool _muted = true;
  bool _stopping = false;

  String get videoCodec {
    final selected = player.state.track.video.codec;
    if (selected != null && selected.trim().isNotEmpty) return selected;
    for (final track in tracks.video) {
      if (track.codec?.trim().isNotEmpty == true) return track.codec!;
    }
    return '';
  }

  String get audioCodec {
    final selected = player.state.track.audio.codec;
    if (selected != null && selected.trim().isNotEmpty) return selected;
    for (final track in tracks.audio) {
      if (track.codec?.trim().isNotEmpty == true) return track.codec!;
    }
    return '';
  }

  int? get videoWidth =>
      width ?? player.state.track.video.w ?? _firstVideoTrack?.w;

  int? get videoHeight =>
      height ?? player.state.track.video.h ?? _firstVideoTrack?.h;

  VideoTrack? get _firstVideoTrack =>
      tracks.video.isEmpty ? null : tracks.video.first;

  Future<void> playUrl(String url, {Map<String, String>? headers}) async {
    _stopping = false;
    _currentMedia = Media(url, httpHeaders: headers);
    error = null;
    _onStateChanged?.call();
    await player.setVolume(_muted ? 0 : _volume);
    await player.open(_currentMedia!);
  }

  Future<void> play() => player.play();

  Future<void> pause() => player.pause();

  Future<void> stop() async {
    _stopping = true;
    _currentMedia = null;
    try {
      await player.stop();
    } finally {
      _stopping = false;
    }
  }

  Future<void> seek(Duration value) => player.seek(value);

  Future<void> seekBy(Duration offset) async {
    var target = position + offset;
    if (target < Duration.zero) target = Duration.zero;
    if (duration > Duration.zero && target > duration) target = duration;
    await seek(target);
  }

  Future<void> reload() async {
    final media = _currentMedia;
    if (media != null) await player.open(media);
  }

  Future<void> setPlaylistMode(PlaylistMode mode) =>
      player.setPlaylistMode(mode);

  Future<void> setVideoTrack(VideoTrack track) => player.setVideoTrack(track);

  Future<void> setAudioTrack(AudioTrack track) => player.setAudioTrack(track);

  Future<void> setSubtitleTrack(SubtitleTrack track) =>
      player.setSubtitleTrack(track);

  Future<void> setVolume(double volume) async {
    _volume = volume.clamp(0, 100).toDouble();
    await player.setVolume(_muted ? 0 : _volume);
  }

  Future<void> setMuted(bool muted) async {
    _muted = muted;
    await player.setVolume(_muted ? 0 : _volume);
  }

  Future<void> dispose() async {
    await _playingSubscription.cancel();
    await _bufferingSubscription.cancel();
    await _errorSubscription.cancel();
    await _completedSubscription.cancel();
    await _widthSubscription.cancel();
    await _heightSubscription.cancel();
    await _positionSubscription.cancel();
    await _durationSubscription.cancel();
    await _tracksSubscription.cancel();
    await _trackSubscription.cancel();
    await player.dispose();
  }
}
