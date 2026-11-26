import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val releaseKeystorePath = providers.environmentVariable("UIPTV_ANDROID_KEYSTORE").orNull
val releaseKeystorePassword = providers.environmentVariable("UIPTV_ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("UIPTV_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("UIPTV_ANDROID_KEY_PASSWORD").orNull
val pinnedLibmpvCommitFile = layout.projectDirectory.file("../../scripts/libmpv-android.ref")
val pinnedLibmpvCommit = pinnedLibmpvCommitFile.asFile.readText().trim()
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val api24LibmpvAar = layout.projectDirectory.file("libs/libmpv-android-api24.aar")
val verifyApi24LibmpvAar = tasks.register("verifyApi24LibmpvAar") {
    inputs.file(api24LibmpvAar)
        .withPropertyName("api24LibmpvAar")
        .optional()
    inputs.file(pinnedLibmpvCommitFile)
        .withPropertyName("pinnedLibmpvCommit")
    doLast {
        if (!api24LibmpvAar.asFile.isFile) {
            throw GradleException("Missing ${api24LibmpvAar.asFile}. Build or copy the Android 7/API 24 libmpv AAR before assembling the app.")
        }
        ZipFile(api24LibmpvAar.asFile).use { archive ->
            if (archive.getEntry("META-INF/uiptv-quiet-jni-v1") == null) {
                throw GradleException("libmpv AAR still enables native logging. Rebuild with scripts/build-mobile-with-libmpv.sh.")
            }
            val commitEntry = archive.getEntry("META-INF/uiptv-libmpv-commit")
                ?: throw GradleException("libmpv AAR does not record its upstream commit. Rebuild with scripts/build-mobile-with-libmpv.sh.")
            val aarCommit = archive.getInputStream(commitEntry).bufferedReader().use { it.readText().trim() }
            if (aarCommit != pinnedLibmpvCommit) {
                throw GradleException("libmpv AAR commit $aarCommit does not match pinned commit $pinnedLibmpvCommit. Rebuild with scripts/build-mobile-with-libmpv.sh.")
            }
        }
    }
}

tasks.matching { task ->
    task.name == "preBuild" || (task.name.startsWith("pre") && task.name.endsWith("Build"))
}.configureEach {
    dependsOn(verifyApi24LibmpvAar)
}

android {
    namespace = "com.uiptv.mobile.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.uiptv.mobile"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        val buildNumber = nextBuildNumber()
        versionCode = 10101
        versionName = "1.1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BUILD_NUMBER", "\"$buildNumber\"")
        buildConfigField("String", "PRO_PRODUCT_ID", "\"uiptv_pro_lifetime\"")
    }

    sourceSets.getByName("androidTest").assets.directories.add(
        layout.projectDirectory.file("../../core/src/test/resources").asFile.absolutePath
    )

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (providers.gradleProperty("uiptv.isolatedTestApp").orNull == "true") {
                applicationIdSuffix = ".epgtest"
            }
            // Google's official test App ID and Interstitial Ad Unit ID prevent invalid-traffic bans during testing.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
            buildConfigField("int", "AD_COOLDOWN_MINUTES", "15")
            buildConfigField("String", "ADMOB_TEST_DEVICE_IDS", "\"56E41FE65FFE85D0203676A7F7BAFD41\"")
        }
        getByName("release") {
            val useTestAds = providers.gradleProperty("uiptv.useTestAds")
                .orNull
                ?.toBooleanStrictOrNull() == true
            val releaseAdCooldownMinutes = providers.gradleProperty("uiptv.adCooldownMinutes")
                .orNull
                ?.toIntOrNull()
                ?: if (useTestAds) 15 else 60
            manifestPlaceholders["admobAppId"] = if (useTestAds) {
                "ca-app-pub-3940256099942544~3347511713"
            } else {
                "ca-app-pub-8756147848785001~7816014408"
            }
            buildConfigField(
                "String",
                "ADMOB_INTERSTITIAL_AD_UNIT_ID",
                if (useTestAds) "\"ca-app-pub-3940256099942544/1033173712\"" else "\"ca-app-pub-8756147848785001/7876930408\""
            )
            buildConfigField("int", "AD_COOLDOWN_MINUTES", "$releaseAdCooldownMinutes")
            buildConfigField(
                "String",
                "ADMOB_TEST_DEVICE_IDS",
                if (useTestAds) "\"56E41FE65FFE85D0203676A7F7BAFD41\"" else "\"\""
            )
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets {
        getByName("main") {
            assets.directories.add(layout.projectDirectory.file("../../core/src/main/resources").asFile.absolutePath)
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.play.services.ads)
    implementation(libs.play.services.ump)
    implementation(libs.billing.ktx)
    implementation(files(api24LibmpvAar))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}

fun nextBuildNumber(): Int {
    val buildNumberFile = file("build/build-number.txt")
    val next = if (buildNumberFile.isFile) {
        buildNumberFile.readText().trim().toIntOrNull()?.inc() ?: 1
    } else {
        1
    }
    buildNumberFile.parentFile?.mkdirs()
    buildNumberFile.writeText(next.toString())
    return next
}
