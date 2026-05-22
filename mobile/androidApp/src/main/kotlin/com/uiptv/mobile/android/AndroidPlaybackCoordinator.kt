package com.uiptv.mobile.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.browse.AndroidBookmarkPlayHistoryStore
import com.uiptv.mobile.shared.browse.BrowseMode
import com.uiptv.mobile.shared.browse.MobileBookmark
import com.uiptv.mobile.shared.browse.MobileBrowseItem
import com.uiptv.mobile.shared.browse.MobileWatchingNowEpisode
import com.uiptv.mobile.shared.browse.MobileWatchingNowItem
import com.uiptv.mobile.shared.browse.resolvedEpisodeNumber
import com.uiptv.mobile.shared.browse.resolvedSeason
import com.uiptv.mobile.shared.browse.seasonTab
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import com.uiptv.mobile.shared.playback.PlaybackLaunchResult
import com.uiptv.mobile.shared.playback.PlaybackTarget
import com.uiptv.mobile.shared.playback.PlayerChoice
import com.uiptv.mobile.shared.playback.extractPlayableStreamUrl
import com.uiptv.mobile.shared.playback.shouldResolveStalkerPortalCommand
import com.uiptv.mobile.shared.settings.AndroidPlayerPreference
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import com.uiptv.mobile.shared.settings.PlayerPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID

class AndroidPlaybackCoordinator(
    private val context: Context,
    private val preferences: AndroidPreferencesRepository,
    private val databaseHelper: AndroidUiptvDatabaseHelper,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    private val watchStateStore: AndroidPlaybackWatchStateStore = AndroidPlaybackWatchStateStore(databaseHelper, epochSeconds),
    private val activityStarter: (Intent) -> Unit = { intent -> context.startActivity(intent) }
) {
    companion object {
        private const val MX_PLAYER_PRO_PACKAGE = "com.mxtech.videoplayer.pro"
        private const val MX_PLAYER_FREE_PACKAGE = "com.mxtech.videoplayer.ad"
        private const val KODI_PACKAGE = "org.xbmc.kodi"
        private const val JUST_PLAYER_PACKAGE = "com.brouken.player"
        private const val XPLAYER_PACKAGE = "video.player.videoplayer"

        private const val MX_PLAYER_STORE_URL = "https://play.google.com/store/apps/details?id=com.mxtech.videoplayer.ad"
        private const val JUST_PLAYER_STORE_URL = "https://play.google.com/store/apps/details?id=com.brouken.player"
        private const val XPLAYER_STORE_URL = "https://play.google.com/store/apps/details?id=video.player.videoplayer"
    }

    suspend fun loadPlayerPreference(): PlayerPreference =
        preferences.load().playerPreference

    suspend fun clearPlayerPreference() {
        preferences.savePlayerPreference(PlayerPreference())
    }

    suspend fun savePlayerPreference(player: AndroidPlayerPreference) {
        preferences.savePlayerPreference(
            if (player == AndroidPlayerPreference.ASK_EVERY_TIME) {
                PlayerPreference(AndroidPlayerPreference.ASK_EVERY_TIME, "", false)
            } else {
                PlayerPreference(player, player.resolvePreferredPackageName(), true)
            }
        )
    }

    suspend fun playerChoices(): List<PlayerChoice> = withContext(Dispatchers.IO) {
        val mxProInstalled = isInstalled(MX_PLAYER_PRO_PACKAGE)
        val mxFreeInstalled = isInstalled(MX_PLAYER_FREE_PACKAGE)
        val mxInstalled = mxProInstalled || mxFreeInstalled
        val mxPlayer = if (mxProInstalled) {
            AndroidPlayerPreference.MX_PLAYER_PRO
        } else {
            AndroidPlayerPreference.MX_PLAYER_FREE
        }
        val mxPackage = when {
            mxProInstalled -> MX_PLAYER_PRO_PACKAGE
            mxFreeInstalled -> MX_PLAYER_FREE_PACKAGE
            else -> MX_PLAYER_FREE_PACKAGE
        }
        listOf(
            PlayerChoice(AndroidPlayerPreference.EMBEDDED_PLAYER, "Embedded", true),
            PlayerChoice(AndroidPlayerPreference.NATIVE, "Android Media", true),
            PlayerChoice(mxPlayer, "MX Player", mxInstalled, mxPackage, MX_PLAYER_STORE_URL),
            PlayerChoice(AndroidPlayerPreference.JUST_PLAYER, "Just Player", isInstalled(JUST_PLAYER_PACKAGE), JUST_PLAYER_PACKAGE, JUST_PLAYER_STORE_URL),
            PlayerChoice(AndroidPlayerPreference.XPLAYER, "XPlayer", isInstalled(XPLAYER_PACKAGE), XPLAYER_PACKAGE, XPLAYER_STORE_URL)
        )
    }

    suspend fun openPlayerInstall(choice: PlayerChoice) = withContext(Dispatchers.Main) {
        val packageName = choice.packageName.ifBlank { choice.player.packageName() }
        val storeUrl = choice.storeUrl.ifBlank {
            packageName.takeIf { it.isNotBlank() }?.let { "https://play.google.com/store/apps/details?id=$it" }.orEmpty()
        }
        if (packageName.isBlank() && storeUrl.isBlank()) {
            return@withContext
        }

        val marketIntent = packageName.takeIf { it.isNotBlank() }?.let {
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$it"))
                .setPackage("com.android.vending")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (marketIntent != null && runCatching { activityStarter(marketIntent) }.isSuccess) {
            return@withContext
        }

        activityStarter(
            Intent(Intent.ACTION_VIEW, Uri.parse(storeUrl))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    suspend fun playBrowseItem(
        item: MobileBrowseItem,
        player: AndroidPlayerPreference,
        remember: Boolean
    ): PlaybackLaunchResult = withContext(Dispatchers.IO) {
        val target = item.toPlaybackTarget()
        val result = launch(target, player)
        if (result.launched) {
            if (remember) {
                preferences.savePlayerPreference(PlayerPreference(player, player.resolvePreferredPackageName(), true))
            }
            if (target.mode == BrowseMode.SERIES || !player.usesNativeActivity()) {
                watchStateStore.markOpened(target.resolved())
            }
        }
        result
    }

    suspend fun playBookmark(bookmark: MobileBookmark, player: AndroidPlayerPreference, remember: Boolean): PlaybackLaunchResult =
        withContext(Dispatchers.IO) {
            val result = launch(bookmark.toPlaybackTarget(), player)
            if (result.launched) {
                AndroidBookmarkPlayHistoryStore(databaseHelper.writableDatabase).record(bookmark)
            }
            if (result.launched && remember) {
                preferences.savePlayerPreference(PlayerPreference(player, player.resolvePreferredPackageName(), true))
            }
            result
        }

    suspend fun playWatchingNow(item: MobileWatchingNowItem, player: AndroidPlayerPreference, remember: Boolean): PlaybackLaunchResult =
        withContext(Dispatchers.IO) {
            val result = if (item.command.isBlank()) {
                PlaybackLaunchResult(false, "No playable URL is cached for ${item.title}.")
            } else {
                launch(item.toPlaybackTarget(), player)
            }
            if (result.launched && remember) {
                preferences.savePlayerPreference(PlayerPreference(player, player.resolvePreferredPackageName(), true))
            }
            result
        }

    suspend fun playWatchingNowEpisode(
        episode: MobileWatchingNowEpisode,
        player: AndroidPlayerPreference,
        remember: Boolean
    ): PlaybackLaunchResult = withContext(Dispatchers.IO) {
        val target = episode.toPlaybackTarget()
        val result = if (episode.command.isBlank()) {
            PlaybackLaunchResult(false, "No playable URL is cached for ${episode.title}.")
        } else {
            launch(target, player)
        }
        if (result.launched) {
            if (remember) {
                preferences.savePlayerPreference(PlayerPreference(player, player.resolvePreferredPackageName(), true))
            }
            watchStateStore.markOpened(target.resolved())
        }
        result
    }

    suspend fun playBingeWatchSeason(
        series: MobileWatchingNowItem,
        episodes: List<MobileWatchingNowEpisode>,
        seasonKey: String,
        player: AndroidPlayerPreference,
        remember: Boolean
    ): PlaybackLaunchResult = withContext(Dispatchers.IO) {
        if (series.mode != BrowseMode.SERIES) {
            return@withContext PlaybackLaunchResult(false, "Binge watch is only available for series.")
        }
        if (!player.usesNativeActivity()) {
            return@withContext PlaybackLaunchResult(
                false,
                "Binge watch on mobile uses the embedded or Android Media player so episode URLs can be prepared just before playback."
            )
        }
        val targets = bingeSeasonTargets(episodes, seasonKey)
        if (targets.isEmpty()) {
            return@withContext PlaybackLaunchResult(false, "No episodes are cached for this season.")
        }
        val start = bingeStart(series, targets)
        val session = AndroidBingeWatchSessionStore.create(series.title, targets, start.index)
        val result = launchBingeSession(session, player)
        if (result.launched) {
            if (remember) {
                preferences.savePlayerPreference(PlayerPreference(player, player.resolvePreferredPackageName(), true))
            }
            session.targets.getOrNull(session.startIndex)?.let(watchStateStore::markOpened)
        }
        result
    }

    private suspend fun launch(target: PlaybackTarget, player: AndroidPlayerPreference): PlaybackLaunchResult {
        val playableTarget = resolvePlayableTarget(target)
        if (!playableTarget.url.isPlayableNetworkUrl()) {
            return PlaybackLaunchResult(false, "No direct playable URL is cached for ${target.title}. Refresh this account on Android or choose a direct stream.")
        }
        if (player == AndroidPlayerPreference.NATIVE && !target.nativeSupported()) {
            return PlaybackLaunchResult(false, "Native player cannot open this DRM or inputstream metadata yet. Use an external player.")
        }
        val intent = when (player) {
            AndroidPlayerPreference.EMBEDDED_PLAYER -> embeddedPlayerIntent(playableTarget)
            AndroidPlayerPreference.NATIVE,
            AndroidPlayerPreference.ASK_EVERY_TIME -> nativePlayerIntent(playableTarget)
            AndroidPlayerPreference.SYSTEM_CHOOSER -> Intent.createChooser(viewIntent(playableTarget, null), "Open stream")
            AndroidPlayerPreference.MX_PLAYER_PRO,
            AndroidPlayerPreference.MX_PLAYER_FREE,
            AndroidPlayerPreference.KODI,
            AndroidPlayerPreference.JUST_PLAYER,
            AndroidPlayerPreference.XPLAYER -> return launchExternal(playableTarget, player)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            activityStarter(intent)
            PlaybackLaunchResult(true, "Opening ${playableTarget.title}.")
        } catch (_: ActivityNotFoundException) {
            PlaybackLaunchResult(false, "${player.displayLabel()} is not available.")
        } catch (ex: Exception) {
            PlaybackLaunchResult(false, ex.message ?: "Unable to open stream.")
        }
    }

    private fun launchBingeSession(
        session: AndroidBingeWatchSession,
        player: AndroidPlayerPreference
    ): PlaybackLaunchResult {
        val target = session.targets.getOrNull(session.startIndex)
            ?: return PlaybackLaunchResult(false, "No episodes are cached for this season.")
        if (player == AndroidPlayerPreference.NATIVE && !target.nativeSupported()) {
            return PlaybackLaunchResult(false, "Android Media cannot open this DRM or inputstream metadata yet. Use Embedded for binge watch.")
        }
        val intent = when (player) {
            AndroidPlayerPreference.EMBEDDED_PLAYER -> embeddedPlayerIntent(target)
            AndroidPlayerPreference.NATIVE,
            AndroidPlayerPreference.ASK_EVERY_TIME -> nativePlayerIntent(target)
            AndroidPlayerPreference.SYSTEM_CHOOSER,
            AndroidPlayerPreference.MX_PLAYER_PRO,
            AndroidPlayerPreference.MX_PLAYER_FREE,
            AndroidPlayerPreference.KODI,
            AndroidPlayerPreference.JUST_PLAYER,
            AndroidPlayerPreference.XPLAYER -> return PlaybackLaunchResult(
                false,
                "Binge watch on mobile uses the embedded or Android Media player so episode URLs can be prepared just before playback."
            )
        }
            .putExtra(NativePlayerActivity.EXTRA_BINGE_SESSION_ID, session.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            activityStarter(intent)
            PlaybackLaunchResult(true, "Starting ${session.seriesTitle}.")
        } catch (_: ActivityNotFoundException) {
            PlaybackLaunchResult(false, "${player.displayLabel()} is not available.")
        } catch (ex: Exception) {
            PlaybackLaunchResult(false, ex.message ?: "Unable to start binge watch.")
        }
    }

    private fun embeddedPlayerIntent(target: PlaybackTarget): Intent =
        playbackIntent(target, MpvEmbeddedPlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    private fun nativePlayerIntent(target: PlaybackTarget): Intent =
        playbackIntent(target, NativePlayerActivity::class.java)

    private fun playbackIntent(target: PlaybackTarget, activityClass: Class<*>): Intent =
        Intent(context, activityClass)
            .putExtra(NativePlayerActivity.EXTRA_URL, target.url)
            .putExtra(NativePlayerActivity.EXTRA_TITLE, target.title)
            .putExtra(NativePlayerActivity.EXTRA_MIME_TYPE, target.mimeType())
            .putExtra(NativePlayerActivity.EXTRA_DRM_TYPE, target.drmType)
            .putExtra(NativePlayerActivity.EXTRA_DRM_LICENSE_URL, target.drmLicenseUrl)
            .putExtra(NativePlayerActivity.EXTRA_ACCOUNT_ID, target.accountId)
            .putExtra(NativePlayerActivity.EXTRA_ACCOUNT_NAME, target.accountName)
            .putExtra(NativePlayerActivity.EXTRA_MODE, target.mode.name)
            .putExtra(NativePlayerActivity.EXTRA_CATEGORY_PROVIDER_ID, target.categoryProviderId)
            .putExtra(NativePlayerActivity.EXTRA_CATEGORY_ROW_ID, target.categoryRowId)
            .putExtra(NativePlayerActivity.EXTRA_CHANNEL_ID, target.channelId)
            .putExtra(NativePlayerActivity.EXTRA_LOGO, target.logo)
            .putExtra(NativePlayerActivity.EXTRA_SERIES_ID, target.seriesId)
            .putExtra(NativePlayerActivity.EXTRA_SERIES_TITLE, target.seriesTitle)
            .putExtra(NativePlayerActivity.EXTRA_EPISODE_ID, target.episodeId)
            .putExtra(NativePlayerActivity.EXTRA_SEASON, target.season)
            .putExtra(NativePlayerActivity.EXTRA_EPISODE_NUMBER, target.episodeNumber)

    private fun launchExternal(target: PlaybackTarget, player: AndroidPlayerPreference): PlaybackLaunchResult {
        val packageName = player.resolvePreferredPackageName()
        if (packageName.isBlank() || !isInstalled(packageName)) {
            return PlaybackLaunchResult(false, "${player.displayLabel()} is not installed.")
        }

        val attempts = externalIntents(target, packageName)
        var lastError: String? = null
        attempts.forEach { intent ->
            if (!canResolve(intent)) {
                return@forEach
            }
            try {
                activityStarter(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return PlaybackLaunchResult(true, "Opening ${target.title} in ${player.displayLabel()}.")
            } catch (ex: ActivityNotFoundException) {
                lastError = ex.message
            } catch (ex: Exception) {
                lastError = ex.message
            }
        }
        return PlaybackLaunchResult(
            false,
            buildString {
                append("${player.displayLabel()} is installed, but Android did not expose a compatible stream activity for this link.")
                if (!lastError.isNullOrBlank()) {
                    append(" ")
                    append(lastError)
                }
            }
        )
    }

    private fun externalIntents(target: PlaybackTarget, packageName: String): List<Intent> {
        val mimeTypes = listOf(target.mimeType(), "video/*", "*/*")
            .distinct()
        val typedIntents = mimeTypes.map { mimeType -> viewIntent(target, packageName, mimeType) }
        return typedIntents + viewIntent(target, packageName, null)
    }

    private fun canResolve(intent: Intent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            ) != null
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }

    private fun viewIntent(target: PlaybackTarget, packageName: String?, mimeType: String? = target.mimeType()): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            val uri = Uri.parse(target.url)
            if (mimeType == null) {
                data = uri
            } else {
                setDataAndType(uri, mimeType)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, target.title)
            putExtra("title", target.title)
            packageName?.let(::setPackage)
        }

    suspend fun resolvePlayableTarget(target: PlaybackTarget): PlaybackTarget {
        val direct = target.resolved()
        val account = AndroidSQLiteAccountRepository(databaseHelper)
            .listAccounts()
            .firstOrNull { account ->
                account.id == target.accountId ||
                    (target.accountId == 0L && account.accountName == target.accountName)
            }
            ?: return direct
        if (account.type != MobileAccountType.STALKER_PORTAL) {
            return direct
        }
        if (target.url.isBlank()) {
            return direct
        }
        if (direct.url.isPlayableNetworkUrl() && !shouldResolveStalkerPortalCommand(target.url)) {
            return direct
        }

        val candidates = stalkerCommandCandidates(account, target)
        for (candidate in candidates) {
            val resolved = runCatching { resolveStalkerCreateLink(account, target.copy(url = candidate)) }.getOrNull().orEmpty()
            val normalized = normalizeStalkerStreamUrl(
                account,
                extractPlayableStreamUrl(resolved.normalizeSeriesStreamPlaceholder(target.stalkerSeriesParam()))
            )
            if (normalized.isPlayableNetworkUrl()) {
                return target.copy(url = normalized)
            }
        }
        return if (shouldResolveStalkerPortalCommand(direct.url)) direct.copy(url = "") else direct
    }

    private fun stalkerCommandCandidates(account: MobileAccount, target: PlaybackTarget): List<String> {
        val commands = linkedSetOf<String>()
        if (target.mode == BrowseMode.LIVE && target.channelId.isNotBlank()) {
            val db = databaseHelper.readableDatabase
            db.rawQuery(
                """
                SELECT ch.cmd, ch.cmd_1, ch.cmd_2, ch.cmd_3
                FROM Channel ch
                JOIN Category cat ON ch.categoryId = CAST(cat.id AS TEXT)
                WHERE cat.accountId = ? AND ch.channelId = ?
                ORDER BY CASE WHEN cat.categoryId = ? THEN 0 ELSE 1 END
                LIMIT 8
                """.trimIndent(),
                arrayOf(account.id?.toString().orEmpty(), target.channelId, target.categoryProviderForLookup())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.commandOrBlank("cmd").takeIf { it.isUsableStalkerLiveCommand() }?.let(commands::add)
                    cursor.commandOrBlank("cmd_1").takeIf { it.isUsableStalkerLiveCommand() }?.let(commands::add)
                    cursor.commandOrBlank("cmd_2").takeIf { it.isUsableStalkerLiveCommand() }?.let(commands::add)
                    cursor.commandOrBlank("cmd_3").takeIf { it.isUsableStalkerLiveCommand() }?.let(commands::add)
                }
            }
        }
        target.url.takeIf { it.isNotBlank() }?.let(commands::add)
        return commands.toList()
    }

    private fun resolveStalkerCreateLink(account: MobileAccount, target: PlaybackTarget): String {
        val portalUrl = normalizePortalUrl(account.serverPortalUrl.ifBlank { account.url })
        val token = stalkerHandshake(account, portalUrl)
        runCatching {
            readStalkerPortal(
                portalUrl = portalUrl,
                account = account,
                token = token,
                params = getStalkerProfileParams(account)
            )
        }
        val body = readStalkerPortal(
            portalUrl = portalUrl,
            account = account,
            token = token,
            params = mapOf(
                "type" to if (target.mode == BrowseMode.SERIES) "vod" else target.mode.accountAction(),
                "action" to "create_link",
                "cmd" to target.url,
                "series" to target.stalkerSeriesParam(),
                "forced_storage" to "undefined",
                "disable_ad" to "0",
                "download" to "0"
            )
        )
        val root = JSONObject(body)
        val js = root.optJSONObject("js")
        return js?.optString("cmd").orEmpty()
            .ifBlank { js?.optString("url").orEmpty() }
            .ifBlank { root.optString("cmd") }
            .ifBlank { root.optString("url") }
    }

    private fun normalizeStalkerStreamUrl(account: MobileAccount, raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) {
            return value
        }
        val portalUri = runCatching {
            Uri.parse(normalizePortalUrl(account.serverPortalUrl.ifBlank { account.url }))
        }.getOrNull()
        val scheme = portalUri?.scheme?.takeIf { it.isNotBlank() } ?: "http"
        return when {
            value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) ->
                alignStalkerPlaybackScheme(value, scheme)
            value.startsWith("//") -> "$scheme:$value"
            value.startsWith("/") && portalUri?.host?.isNotBlank() == true ->
                "$scheme://${portalUri.host}${if (portalUri.port > 0) ":${portalUri.port}" else ""}$value"
            value.matches(Regex("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) -> "$scheme://$value"
            else -> value
        }
    }

    private fun alignStalkerPlaybackScheme(value: String, scheme: String): String {
        val lower = value.lowercase()
        return if (
            scheme.equals("http", ignoreCase = true) &&
            lower.startsWith("https://") &&
            (lower.contains("/live/play/") || lower.contains("/play/movie.php"))
        ) {
            "http://" + value.substring("https://".length)
        } else {
            value
        }
    }

    private fun stalkerHandshake(account: MobileAccount, portalUrl: String): String {
        val body = readStalkerPortal(
            portalUrl = portalUrl,
            account = account,
            token = "",
            params = mapOf("type" to "stb", "action" to "handshake", "token" to "")
        )
        return JSONObject(body).optJSONObject("js")?.optString("token").orEmpty()
    }

    private fun readStalkerPortal(
        portalUrl: String,
        account: MobileAccount,
        token: String,
        params: Map<String, String>
    ): String {
        val payload = (params + ("JsHttpRequest" to "${System.currentTimeMillis()}-xml")).toQueryString()
        val connection = (URL("$portalUrl?$payload").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG250 stbapp ver: 2 rev: 250 Safari/533.3")
            setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
            setRequestProperty("Referer", account.url.ifBlank { portalUrl })
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("Cookie", "mac=${account.macAddress}; stb_lang=en; timezone=${account.timezone.ifBlank { "Europe/London" }};")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status >= 300) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status >= 300) {
                error(body.ifBlank { "Stalker request failed with status $status." })
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun isInstalled(packageName: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                true
            }.getOrDefault(false)
        } else {
            @Suppress("DEPRECATION")
            runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
        }

    private fun MobileBrowseItem.toPlaybackTarget(): PlaybackTarget =
        PlaybackTarget(
            accountId = accountId,
            accountName = accountName,
            mode = mode,
            categoryProviderId = categoryProviderId,
            categoryRowId = categoryRowId,
            channelId = channelId,
            title = name,
            url = command,
            logo = logo,
            drmType = drmType,
            drmLicenseUrl = drmLicenseUrl,
            clearKeysJson = clearKeysJson,
            inputstreamAddon = inputstreamAddon,
            manifestType = manifestType
        )

    private fun MobileBookmark.toPlaybackTarget(): PlaybackTarget =
        PlaybackTarget(
            accountId = accountId,
            accountName = accountName,
            mode = mode,
            categoryProviderId = "",
            categoryRowId = 0,
            channelId = channelId,
            title = channelName,
            url = command,
            drmType = drmType,
            drmLicenseUrl = drmLicenseUrl,
            clearKeysJson = clearKeysJson,
            inputstreamAddon = inputstreamAddon,
            manifestType = manifestType
        )

    private fun MobileWatchingNowItem.toPlaybackTarget(): PlaybackTarget =
        PlaybackTarget(
            accountId = accountId,
            accountName = accountName,
            mode = mode,
            categoryProviderId = categoryProviderId,
            categoryRowId = categoryRowId,
            channelId = contentId.ifBlank { rowId.toString() },
            title = title,
            url = command,
            logo = logo
        )

    private fun MobileWatchingNowEpisode.toPlaybackTarget(): PlaybackTarget =
        PlaybackTarget(
            accountId = accountId,
            accountName = accountName,
            mode = BrowseMode.SERIES,
            categoryProviderId = categoryProviderId,
            categoryRowId = categoryRowId,
            channelId = episodeId,
            title = title,
            url = command,
            logo = logo,
            seriesId = seriesId,
            seriesTitle = seriesTitle,
            episodeId = episodeId,
            season = resolvedSeason(),
            episodeNumber = resolvedEpisodeNumber()
        )

    private fun bingeSeasonTargets(episodes: List<MobileWatchingNowEpisode>, seasonKey: String): List<PlaybackTarget> =
        episodes
            .filter { it.command.isNotBlank() && it.matchesSeasonKey(seasonKey) }
            .sortedWith(
                compareBy<MobileWatchingNowEpisode> { it.resolvedSeason().toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.resolvedEpisodeNumber().toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            .map { it.toPlaybackTarget() }

    private fun MobileWatchingNowEpisode.matchesSeasonKey(seasonKey: String): Boolean =
        when {
            seasonKey.isBlank() -> true
            seasonKey == "other" -> seasonTab().key == "other"
            else -> seasonTab().key == seasonKey
        }

    private fun bingeStart(series: MobileWatchingNowItem, targets: List<PlaybackTarget>): BingeStart {
        val row = databaseHelper.readableDatabase.rawQuery(
            """
            SELECT episodeId, season, episodeNum
            FROM SeriesWatchState
            WHERE accountId = ? AND categoryId = ? AND seriesId = ?
            ORDER BY updatedAt DESC
            LIMIT 1
            """.trimIndent(),
            arrayOf(series.accountId.toString(), series.categoryProviderId, series.contentId)
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                null
            } else {
                LastWatchedEpisode(
                    episodeId = cursor.commandOrBlank("episodeId"),
                    season = cursor.commandOrBlank("season"),
                    episodeNumber = cursor.commandOrBlank("episodeNum")
                )
            }
        } ?: return BingeStart(index = 0)
        val selectedSeason = targets.firstOrNull()?.season.orEmpty().normalizedNumber()
        val watchedSeason = row.season.normalizedNumber()
        if (watchedSeason.isNotBlank() && selectedSeason.isNotBlank() && selectedSeason != watchedSeason) {
            return BingeStart(index = 0)
        }
        return targets.indexOfFirst { target ->
            (row.episodeId.isNotBlank() && target.episodeId == row.episodeId) ||
                (row.episodeNumber.isNotBlank() && target.episodeNumber.normalizedNumber() == row.episodeNumber.normalizedNumber())
        }
            .takeIf { it >= 0 }
            ?.let { BingeStart(index = it) }
            ?: BingeStart(index = 0)
    }

    private fun AndroidPlayerPreference.packageName(): String =
        when (this) {
            AndroidPlayerPreference.MX_PLAYER_PRO -> MX_PLAYER_PRO_PACKAGE
            AndroidPlayerPreference.MX_PLAYER_FREE -> MX_PLAYER_FREE_PACKAGE
            AndroidPlayerPreference.KODI -> KODI_PACKAGE
            AndroidPlayerPreference.JUST_PLAYER -> JUST_PLAYER_PACKAGE
            AndroidPlayerPreference.XPLAYER -> XPLAYER_PACKAGE
            AndroidPlayerPreference.EMBEDDED_PLAYER,
            AndroidPlayerPreference.NATIVE,
            AndroidPlayerPreference.SYSTEM_CHOOSER,
            AndroidPlayerPreference.ASK_EVERY_TIME -> ""
        }

    private fun AndroidPlayerPreference.resolvePreferredPackageName(): String =
        when (this) {
            AndroidPlayerPreference.MX_PLAYER_PRO,
            AndroidPlayerPreference.MX_PLAYER_FREE -> when {
                isInstalled(MX_PLAYER_PRO_PACKAGE) -> MX_PLAYER_PRO_PACKAGE
                isInstalled(MX_PLAYER_FREE_PACKAGE) -> MX_PLAYER_FREE_PACKAGE
                else -> MX_PLAYER_FREE_PACKAGE
            }
            else -> packageName()
        }

    private fun AndroidPlayerPreference.usesNativeActivity(): Boolean =
        this == AndroidPlayerPreference.NATIVE ||
            this == AndroidPlayerPreference.EMBEDDED_PLAYER ||
            this == AndroidPlayerPreference.ASK_EVERY_TIME

    private fun AndroidPlayerPreference.displayLabel(): String =
        when (this) {
            AndroidPlayerPreference.ASK_EVERY_TIME -> "Player picker"
            AndroidPlayerPreference.EMBEDDED_PLAYER -> "Embedded"
            AndroidPlayerPreference.NATIVE -> "Android Media"
            AndroidPlayerPreference.MX_PLAYER_PRO,
            AndroidPlayerPreference.MX_PLAYER_FREE -> "MX Player"
            AndroidPlayerPreference.KODI -> "Kodi"
            AndroidPlayerPreference.JUST_PLAYER -> "Just Player"
            AndroidPlayerPreference.XPLAYER -> "XPlayer"
            AndroidPlayerPreference.SYSTEM_CHOOSER -> "System chooser"
        }

    private fun PlaybackTarget.mimeType(): String =
        when {
            manifestType.equals("mpd", ignoreCase = true) -> "application/dash+xml"
            manifestType.equals("hls", ignoreCase = true) -> "application/x-mpegURL"
            url.contains(".m3u8", ignoreCase = true) -> "application/x-mpegURL"
            url.contains(".mpd", ignoreCase = true) -> "application/dash+xml"
            mode == BrowseMode.VOD -> "video/*"
            else -> "video/*"
        }

    private fun PlaybackTarget.resolved(): PlaybackTarget =
        copy(url = extractPlayableStreamUrl(url))

    private fun PlaybackTarget.stalkerSeriesParam(): String =
        if (mode == BrowseMode.SERIES) {
            episodeNumber.ifBlank { episodeId }.ifBlank { channelId }
        } else {
            ""
        }

    private fun String.normalizeSeriesStreamPlaceholder(seriesParam: String): String {
        if (isBlank() || seriesParam.isBlank()) {
            return this
        }
        val streamToken = seriesParam.substringBefore(':').filter { it.isDigit() }
        if (streamToken.isBlank()) {
            return this
        }
        return replace("stream=.&", "stream=$streamToken&")
            .replace("stream=&", "stream=$streamToken&")
            .let { value ->
                when {
                    value.endsWith("stream=.") -> value.removeSuffix("stream=.") + "stream=$streamToken"
                    value.endsWith("stream=") -> value + streamToken
                    else -> value
                }
            }
    }

    private fun String.isPlayableNetworkUrl(): Boolean {
        val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }

    private fun String.isUsableStalkerLiveCommand(): Boolean {
        if (isBlank()) {
            return false
        }
        val normalized = trim()
            .removePrefix("ffmpeg ")
            .lowercase()
        return !normalized.contains("stream=&")
    }

    private fun Cursor.commandOrBlank(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }

    private fun PlaybackTarget.categoryProviderForLookup(): String =
        categoryProviderId.ifBlank { categoryRowId.takeIf { it > 0 }?.toString().orEmpty() }

    private fun BrowseMode.accountAction(): String =
        when (this) {
            BrowseMode.LIVE -> "itv"
            BrowseMode.VOD -> "vod"
            BrowseMode.SERIES -> "series"
        }

    private fun normalizePortalUrl(source: String): String {
        var candidate = source.trim()
        if (!candidate.contains("://")) {
            candidate = "http://$candidate"
        }
        return if (candidate.lowercase().endsWith("portal.php") || candidate.lowercase().endsWith("load.php")) {
            candidate
        } else {
            "${candidate.trimEnd('/')}/portal.php"
        }
    }

    private fun Map<String, String>.toQueryString(): String =
        entries.joinToString("&") { (key, value) ->
            "${key.url()}=${value.url()}"
        }

    private fun String.url(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun getStalkerProfileParams(account: MobileAccount): Map<String, String> {
        val serial = account.serialNumber.ifBlank { randomId().take(32).uppercase() }
        val device1 = account.deviceId1.ifBlank { randomId() }
        val device2 = account.deviceId2.ifBlank { device1 }
        val signature = account.signature.ifBlank { randomId() }
        return mapOf(
            "type" to "stb",
            "action" to "get_profile",
            "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.9; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
            "num_banks" to "2",
            "sn" to serial,
            "stb_type" to "MAG250",
            "client_type" to "STB",
            "image_version" to "218",
            "video_out" to "hdmi",
            "device_id" to device1,
            "device_id2" to device2,
            "signature" to signature,
            "auth_second_step" to "1",
            "hw_version" to "1.7-BD-00",
            "not_valid_token" to "0",
            "metrics" to "{\"mac\":\"${account.macAddress}\",\"sn\":\"$serial\",\"type\":\"STB\",\"model\":\"MAG250\",\"uid\":\"\",\"random\":\"${randomId()}\"}",
            "hw_version_2" to randomId(),
            "api_signature" to "262",
            "prehash" to ""
        )
    }

    private fun randomId(): String =
        (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")

    private fun PlaybackTarget.nativeSupported(): Boolean =
        inputstreamAddon.isBlank() &&
            clearKeysJson.isBlank() &&
            (
                drmType.isBlank() && drmLicenseUrl.isBlank() ||
                    drmLicenseUrl.isNotBlank() && drmType.isLicenseUrlDrmType()
                )

    private fun String.isLicenseUrlDrmType(): Boolean =
        equals("widevine", ignoreCase = true) ||
            equals("com.widevine.alpha", ignoreCase = true) ||
            equals("clearkey", ignoreCase = true) ||
            equals("org.w3.clearkey", ignoreCase = true)

    private data class LastWatchedEpisode(
        val episodeId: String,
        val season: String,
        val episodeNumber: String
    )

    private data class BingeStart(
        val index: Int
    )

    private fun String.normalizedNumber(): String {
        val digits = filter { it.isDigit() }
        if (digits.isBlank()) {
            return ""
        }
        return digits.trimStart('0').ifBlank { "0" }
    }
}
