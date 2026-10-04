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

    // The baseline SQL is the single schema artifact both platforms already share: core loads it from the
    // classpath and androidApp repackages it as an asset. It drives both the generated mirror below and
    // UiptvSyncSchemaBaselineContractTest, so schema drift fails the build instead of surfacing at restore time.
    sourceSets {
        commonTest {
            resources.srcDir("../../core/src/main/resources")
        }
    }

    // Generates UiptvGeneratedSchema.kt from 0000_baseline.sql. Keeping the Android column list derived
    // rather than hand-written means adding a column to the baseline propagates to the cross-platform
    // contract on its own; the contract test still re-derives the list and fails if this output is stale.
    // rootProject is mobile/, so the repository root (which owns core/) is two levels up.
val repoRoot = layout.projectDirectory.dir("../../").asFile
val baselineFile = file("$repoRoot/core/src/main/resources/db/migrations/0000_baseline.sql")
val migrationsList = file("$repoRoot/core/src/main/resources/db/migrations/migrations.txt")
val generatedSchemaFile =
    layout.projectDirectory.file("src/commonMain/kotlin/com/uiptv/mobile/shared/db/UiptvGeneratedSchema.kt")

val generateUiptvSchemaMirror by tasks.registering {
    description = "Generates UiptvGeneratedSchema.kt from core/src/main/resources/db/migrations/0000_baseline.sql"
    group = "build"

    inputs.file(baselineFile)
    inputs.file(migrationsList)
    outputs.file(generatedSchemaFile)

    doLast {
        val sql = baselineFile.readText()
            val tables = LinkedHashMap<String, String>()
            val header = Regex(
                """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["`\[]?(\w+)["`\]]?\s*\(""",
                RegexOption.IGNORE_CASE
            )
            var cursor = 0
            while (cursor < sql.length) {
                val match = header.find(sql, cursor) ?: break
                val open = match.range.last
                var depth = 0
                var quote: Char? = null
                var i = open
                while (i < sql.length) {
                    val ch = sql[i]
                    when {
                        quote != null -> if (ch == quote) quote = null
                        ch == '\'' || ch == '"' || ch == '`' -> quote = ch
                        ch == '(' -> depth++
                        ch == ')' -> {
                            depth--
                            if (depth == 0) break
                        }
                    }
                    i++
                }
                require(i < sql.length) { "Unbalanced parentheses in $baselineFile at $open" }
                tables[match.groupValues[1]] = sql.substring(open + 1, i)
                cursor = i + 1
            }

            fun columnsOf(table: String): List<String> {
                val entries = mutableListOf<String>()
                val current = StringBuilder()
                var depth = 0
                var quote: Char? = null
                for (ch in tables.getValue(table)) {
                    when {
                        quote != null -> {
                            current.append(ch)
                            if (ch == quote) quote = null
                        }
                        ch == '\'' || ch == '"' || ch == '`' -> {
                            quote = ch
                            current.append(ch)
                        }
                        ch == '(' -> { depth++; current.append(ch) }
                        ch == ')' -> { depth--; current.append(ch) }
                        ch == ',' && depth == 0 -> { entries.add(current.toString()); current.setLength(0) }
                        else -> current.append(ch)
                    }
                }
                if (current.isNotBlank()) entries.add(current.toString())
                return entries.mapNotNull { entry ->
                    val first = entry.trim().split(Regex("\\s+")).firstOrNull { it.isNotEmpty() }
                        ?: return@mapNotNull null
                    if (first.uppercase() in setOf("PRIMARY", "FOREIGN", "UNIQUE", "CHECK", "CONSTRAINT", "KEY")) {
                        null
                    } else {
                        first.trim('"', '`', '[', ']')
                    }
                }
            }

            val schemaVersion = migrationsList.readLines()
                .map { it.trim() }
                .last { it.isNotEmpty() && !it.startsWith("#") }
                .substringBefore('_')
                .removeSuffix(".sql")

            fun listOf(name: String, values: List<String>): String =
                values.joinToString(separator = ",\n", prefix = "    val $name: List<String> = listOf(\n", postfix = "\n    )") { "        \"$it\"" }

            generatedSchemaFile.asFile.writeText(
                """
                |package com.uiptv.mobile.shared.db
                |
                |/**
                | * GENERATED FILE - DO NOT EDIT.
                | *
                | * Produced by the `generateUiptvSchemaMirror` Gradle task in `mobile/shared/build.gradle.kts`
                | * from `core/src/main/resources/db/migrations/0000_baseline.sql`.
                | *
                | * `0000_baseline.sql` is the single schema artifact both platforms share: core loads it from the
                | * classpath and androidApp repackages it as an asset. Generating the mirror from it means a new
                | * column reaches the Android contract by adding one `ALTER TABLE` to the baseline, instead of
                | * by editing this file and the matching Java literal in `uiptv-shared` and hoping every copy
                | * stays in step.
                | *
                | * Regenerate with:
                | *   ./gradlew :shared:generateUiptvSchemaMirror
                | *
                | * `UiptvSyncSchemaBaselineContractTest` re-derives the same list from the baseline and fails
                | * the build if this file is stale, so a hand edit cannot survive CI either.
                | */
                |internal object UiptvGeneratedSchema {
                |    const val SCHEMA_VERSION: String = "$schemaVersion"
                |
                |    /** Configuration columns, in `0000_baseline.sql` declaration order. */
                |${listOf("configurationColumns", columnsOf("Configuration"))}
                |
                |    /** Every table declared by the baseline, which is also the set that must survive a restore. */
                |${listOf("baselineTables", tables.keys.sorted())}
                |}
                |
                """.trimMargin()
            )
            logger.lifecycle("Generated ${generatedSchemaFile.asFile.name} (schema $schemaVersion, ${tables.size} tables)")
        }
    }

    // Consumers depend on the generated mirror, so a baseline change regenerates it before compiling.
    tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach {
        dependsOn(generateUiptvSchemaMirror)
    }

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
                    "com.uiptv.mobile.shared.browse.MobileWatchingNowEpisode",
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
                    "com.uiptv.mobile.shared.settings.BackupRestoreResult",
                    "com.uiptv.mobile.shared.settings.MobileBackupArchive",
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
