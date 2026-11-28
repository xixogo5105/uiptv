class DesktopPlaybackRecoveryPolicy {
  static const maxAttempts = 5;
  static const liveRetryDelays = [1, 2, 5, 10, 20];

  static Duration? delayBeforeNextAttempt({
    required bool live,
    required bool repeatEnabled,
    required int completedAttempts,
  }) {
    if (completedAttempts < 0 || completedAttempts >= maxAttempts) return null;
    if (live) {
      return Duration(seconds: liveRetryDelays[completedAttempts]);
    }
    if (!repeatEnabled) return null;
    return completedAttempts == 0 ? Duration.zero : const Duration(seconds: 10);
  }
}
