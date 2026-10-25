# Mobile stability investigation — 25 October 2026

## Reproduced failure

Device: Xiaomi Mi Max 3 (nitrogen), custom Infinity-X Android 16, arm64-v8a.
Repository started clean on main at 7c94018f. Installed package com.uiptv.mobile was debug build 450, last updated at 11:24:32 device time. It did not contain the current source's secure_delete rawQuery fix.

Launching the installed app and opening Settings reproduced an application exception at 12:17:27.764 device time (PID 17584):

```
SQLiteException: Queries can be performed using SQLiteDatabase query or rawQuery methods only.
SQLiteDatabase.execSQL
AndroidUiptvMigrationApplier.applyMigration:48
AndroidUiptvDatabaseHelper.onCreate
AndroidSQLiteFilterSettingsRepository.load
```

The failing operation was the PRAGMA restoration in the migration finally block. It escaped the migration error handler and propagated from the IO worker into a Compose coroutine. Exit history classifies this as APP CRASH(EXCEPTION), not a native signal or ANR. The current source already uses consumed, closed rawQuery cursors for both PRAGMA writes; installing that code was essential.

## Changes made in this investigation

- Give every migration a savepoint, including inside SQLiteOpenHelper's outer transaction. Failed legacy rebuilds now roll back partial writes before being recorded as failed; the migration chain continues.
- Prefix savepoint rollback with a SQL comment for Android 7/8 compatibility. Their legacy SQL classifier treats bare ROLLBACK TO as a complete transaction abort: see [AOSP Android 8 DatabaseUtils](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-8.0.0_r1/core/java/android/database/DatabaseUtils.java).
- Keep a SurfaceTexture alive until MPV detaches from it, then release its Surface and texture. Queue native attachment, stop, loadfile and destruction in order; resume playback after attachment succeeds. Invalidate pending loads on stop/surface destruction. Preserve a still-attached surface across background transitions rather than attaching it twice.
- Stop and load streams off the main thread. Retain concise surface/option/migration warnings; command failure messages no longer include command arguments that could expose stream credentials.
- Add migration rollback, upgrade/data-preservation and real-video lifecycle regression coverage. Correct snapshot test staging directories to match the existing restore contract.

The darkTheme failures in legacy migrations 0167, 0199 and 0206 are expected when applying historical migrations to the current baseline. They remain recorded as failed. Tests verify current Configuration columns, rollback of staging tables, Settings preservation and database integrity. No migration SQL, desktop behavior or shared business logic was changed. No startup VACUUM was introduced.

## Verification

- Shared mobile tests: 102 tests, no failures/errors.
- Android app local unit tests: 12 tests, no failures/errors.
- ProviderIdentityContractTest: 4 tests passed.
- Connected database compatibility suite: 24 tests passed, using com.uiptv.mobile.epgtest; never the user's database. Covers fresh creation, standalone migration application, rollback inside a helper transaction, reopening, previous-version upgrade, account/Settings preservation, snapshot restore and catalog/playback contracts.
- Connected MPV smoke test passed: three player instances, each playing a generated local H.264/AAC video, destroying/recreating the TextureView surface, backgrounding/resuming, and releasing. Playback advanced after each transition; decoded width and gpu output were asserted.
- Runtime MPV properties: mpv v0.41.0, current-vo=gpu, hwdec-current=mediacodec-copy. No option rejection warnings or new crash-buffer entries were observed during final verification. Hardware/software fallback was not forced because no decoder failure was demonstrated.
- Regular debug build 459 installed in place. Bookmarks loaded, Settings opened, artwork setting changed and survived a full process restart. Original artwork setting restored afterward. No new crash entries during this check.
- git diff --check passed. No commit or push.

## Limits and cleanup

This is a bounded test on one device with generated video, not proof of stability for every provider, codec, graphics driver or Android version. A native MPV crash was not reproduced. Available tombstone headers belong to system/camera processes, not UIPTV. Android denied access to the historical ANR trace; its main-thread stack could not be verified in this session. Android 7/8 compatibility was inspected in source, not tested on hardware here.

The isolated app/test package, local-video reverse tunnel and temporary video server were removed/stopped. The regular app remains installed. No user database reset was performed. Unrelated catalog/metadata edits appeared concurrently in the working tree and were preserved; they are outside this investigation's changes.

## Follow-up: confirmed Huawei slow-motion regression

Device: Huawei AGS2-W09, Android 8.0, arm64-v8a. Reproduced on the first live stream opened from the existing app. EPG was disabled for this playback, so no guide request was active. The stream had a 50 fps H.264 video track; cache remained populated and MPV speed stayed at 1.00.

The regression came from commit `7c94018f`: it changed the renderer from `mediacodec_embed,gpu` to `gpu` and the decoder selection from `mediacodec,mediacodec-copy` to `auto-safe`. On this device, the latter combination selected `current-vo=gpu` with `hwdec-current=mediacodec-copy`. During that run, MPV's playback clock advanced at 0.66–0.73x, A/V sync drift grew from 1.54 to 4.72 seconds, and video-output drops rose from 135 to 444 while decoder drops stayed at zero. The cache held about 14–20 seconds. This isolates the failure to the output/render path after decoding, rather than EPG, stream buffering, or a software-versus-hardware decoder choice.

Replaying the same stream with the parent commit's complete renderer/decoder pair selected `current-vo=mediacodec_embed` and `hwdec-current=mediacodec`. Playback clock stayed at 0.98–1.03x, A/V sync stayed at zero, and output drops remained at one. A mixed `mediacodec_embed` plus `auto` configuration produced audio without video, confirming these options must be kept as a matched pair.

The Android default is restored to the verified pair. Playback settings now persist a rendering profile: Direct MediaCodec (the verified default) or GPU output. The hardware toggle returns to the selected profile's matching decoder option. Temporary per-second timing and EPG diagnostics were removed after the capture; concise option, surface, player-init and database warnings remain.
