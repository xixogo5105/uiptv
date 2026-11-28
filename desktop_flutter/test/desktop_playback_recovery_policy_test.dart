import 'package:desktop_flutter/player/desktop_playback_recovery_policy.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'live playback refresh attempts use JavaFX backoff and stop at five',
    () {
      final delays = [
        for (
          var attempt = 0;
          attempt < DesktopPlaybackRecoveryPolicy.maxAttempts;
          attempt++
        )
          DesktopPlaybackRecoveryPolicy.delayBeforeNextAttempt(
            live: true,
            repeatEnabled: false,
            completedAttempts: attempt,
          )!.inSeconds,
      ];

      expect(delays, [1, 2, 5, 10, 20]);
      expect(
        DesktopPlaybackRecoveryPolicy.delayBeforeNextAttempt(
          live: true,
          repeatEnabled: false,
          completedAttempts: 5,
        ),
        isNull,
      );
    },
  );

  test('on-demand repeat retries immediately then every ten seconds', () {
    final delays = [
      for (
        var attempt = 0;
        attempt < DesktopPlaybackRecoveryPolicy.maxAttempts;
        attempt++
      )
        DesktopPlaybackRecoveryPolicy.delayBeforeNextAttempt(
          live: false,
          repeatEnabled: true,
          completedAttempts: attempt,
        )!.inSeconds,
    ];

    expect(delays, [0, 10, 10, 10, 10]);
    expect(
      DesktopPlaybackRecoveryPolicy.delayBeforeNextAttempt(
        live: false,
        repeatEnabled: false,
        completedAttempts: 0,
      ),
      isNull,
    );
  });
}
