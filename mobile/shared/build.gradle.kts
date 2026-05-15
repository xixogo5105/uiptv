import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kover)
}

kotlin {
    android {
        namespace = "com.uiptv.mobile.shared"
        compileSdk = libs.versions.android.compile.sdk.get().toInt()
        minSdk = libs.versions.android.min.sdk.get().toInt()

        withHostTestBuilder {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    val enableIosTargets = providers.gradleProperty("uiptv.enableIosTargets")
        .map(String::toBoolean)
        .orElse(canUseXcode())
        .get()

    if (enableIosTargets) {
        iosArm64()
        iosSimulatorArm64()
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "UiptvMobileShared"
            isStatic = true
        }
    }

    jvmToolchain(17)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.kotlinx.coroutines.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

kover {
    reports {
        filters {
            // Kover only counts JVM/local unit tests for KMP/Android. Keep this gate on
            // shared mobile logic that can run on the host; Android framework and Compose
            // UI glue still need device/Compose tests, which Kover does not merge here.
            includes {
                classes(
                    "com.uiptv.mobile.shared.accounts.AccountCacheSummary",
                    "com.uiptv.mobile.shared.accounts.MobileAccount",
                    "com.uiptv.mobile.shared.accounts.MobileAccountType",
                    "com.uiptv.mobile.shared.browse.BrowseAccountOption",
                    "com.uiptv.mobile.shared.browse.BrowseMode",
                    "com.uiptv.mobile.shared.browse.MobileBookmark",
                    "com.uiptv.mobile.shared.browse.MobileBookmarkCategory",
                    "com.uiptv.mobile.shared.browse.MobileBrowseCategory",
                    "com.uiptv.mobile.shared.browse.MobileBrowseItem",
                    "com.uiptv.mobile.shared.browse.MobileBrowseSnapshot",
                    "com.uiptv.mobile.shared.browse.MobileWatchingNowItem",
                    "com.uiptv.mobile.shared.cache.CacheRefreshAction",
                    "com.uiptv.mobile.shared.cache.CacheRefreshJobRequest",
                    "com.uiptv.mobile.shared.cache.CacheRefreshJobState",
                    "com.uiptv.mobile.shared.cache.CacheRefreshJobStatus",
                    "com.uiptv.mobile.shared.db.DatabaseSyncReport",
                    "com.uiptv.mobile.shared.db.MigrationDirective*",
                    "com.uiptv.mobile.shared.db.TableSyncResult",
                    "com.uiptv.mobile.shared.db.UiptvMigrationSql",
                    "com.uiptv.mobile.shared.db.UiptvSchemaInfo",
                    "com.uiptv.mobile.shared.db.UiptvSyncSchema",
                    "com.uiptv.mobile.shared.playback.MobilePlaybackKt",
                    "com.uiptv.mobile.shared.playback.PlaybackLaunchResult",
                    "com.uiptv.mobile.shared.playback.PlaybackTarget",
                    "com.uiptv.mobile.shared.playback.PlayerChoice",
                    "com.uiptv.mobile.shared.settings.AndroidFilterSettings",
                    "com.uiptv.mobile.shared.settings.AndroidOnlyPreferenceKeys",
                    "com.uiptv.mobile.shared.settings.AndroidPlayerPreference",
                    "com.uiptv.mobile.shared.settings.AndroidPreferenceSnapshot",
                    "com.uiptv.mobile.shared.settings.PlayerPreference",
                    "com.uiptv.mobile.shared.settings.RemoteEndpointPreference",
                    "com.uiptv.mobile.shared.sync.ConfigurationSyncProfile",
                    "com.uiptv.mobile.shared.sync.PullFromDesktopSyncUseCase",
                    "com.uiptv.mobile.shared.sync.RemoteSyncContractsKt",
                    "com.uiptv.mobile.shared.sync.RemoteSyncDirection",
                    "com.uiptv.mobile.shared.sync.RemoteSyncOptions",
                    "com.uiptv.mobile.shared.sync.RemoteSyncProgress",
                    "com.uiptv.mobile.shared.sync.RemoteSyncProgressStep",
                    "com.uiptv.mobile.shared.sync.RemoteSyncPullResult",
                    "com.uiptv.mobile.shared.sync.RemoteSyncRequest",
                    "com.uiptv.mobile.shared.sync.RemoteSyncSessionState",
                    "com.uiptv.mobile.shared.sync.RemoteSyncStatus"
                )
            }
            excludes {
                classes(
                    "com.uiptv.mobile.shared.accounts.Android*",
                    "com.uiptv.mobile.shared.browse.Android*",
                    "com.uiptv.mobile.shared.cache.Android*",
                    "com.uiptv.mobile.shared.db.Android*",
                    "com.uiptv.mobile.shared.settings.AndroidDataStore*",
                    "com.uiptv.mobile.shared.settings.AndroidSQLite*",
                    "com.uiptv.mobile.shared.sync.Android*",
                    "com.uiptv.mobile.shared.ui.*"
                )
            }
        }
        verify {
            rule("mobile shared Kotlin line coverage") {
                minBound(80)
            }
        }
    }
}

fun canUseXcode(): Boolean {
    if (System.getProperty("os.name").contains("Mac", ignoreCase = true).not()) {
        return false
    }

    return try {
        ProcessBuilder("xcrun", "xcodebuild", "-version")
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Exception) {
        false
    }
}
