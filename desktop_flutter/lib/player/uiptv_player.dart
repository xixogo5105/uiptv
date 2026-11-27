import 'package:media_kit/media_kit.dart';
import 'package:media_kit_video/media_kit_video.dart';

import 'dart:async';

class UiptvPlayer {
  UiptvPlayer({this._onStateChanged}) {
    player = Player();
    controller = VideoController(player);
    _playingSubscription = player.stream.playing.listen((value) {
      playing = value;
      _onStateChanged?.call();
    });
    _bufferingSubscription = player.stream.buffering.listen((value) {
      buffering = value;
      _onStateChanged?.call();
    });
    _errorSubscription = player.stream.error.listen((value) {
      error = value;
      _onStateChanged?.call();
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
  }

  final void Function()? _onStateChanged;
  late final Player player;
  late final VideoController controller;
  late final StreamSubscription<bool> _playingSubscription;
  late final StreamSubscription<bool> _bufferingSubscription;
  late final StreamSubscription<String> _errorSubscription;
  late final StreamSubscription<int?> _widthSubscription;
  late final StreamSubscription<int?> _heightSubscription;
  late final StreamSubscription<Duration> _positionSubscription;
  late final StreamSubscription<Duration> _durationSubscription;

  bool playing = false;
  bool buffering = false;
  String? error;
  int? width;
  int? height;
  Duration position = Duration.zero;
  Duration duration = Duration.zero;

  Future<void> playUrl(String url, {Map<String, String>? headers}) async {
    await player.open(Media(url, httpHeaders: headers));
  }

  Future<void> play() => player.play();

  Future<void> pause() => player.pause();

  Future<void> stop() => player.stop();

  Future<void> seek(Duration value) => player.seek(value);

  Future<void> setVolume(double volume) =>
      player.setVolume(volume.clamp(0, 100).toDouble());

  Future<void> dispose() async {
    await _playingSubscription.cancel();
    await _bufferingSubscription.cancel();
    await _errorSubscription.cancel();
    await _widthSubscription.cancel();
    await _heightSubscription.cancel();
    await _positionSubscription.cancel();
    await _durationSubscription.cancel();
    await player.dispose();
  }
}
