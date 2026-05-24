plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

val releaseKeystorePath = providers.environmentVariable("UIPTV_ANDROID_KEYSTORE").orNull
val releaseKeystorePassword = providers.environmentVariable("UIPTV_ANDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("UIPTV_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("UIPTV_ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val api24LibmpvAar = layout.projectDirectory.file("libs/libmpv-android-api24.aar")
val verifyApi24LibmpvAar by tasks.registering {
    inputs.file(api24LibmpvAar)
        .withPropertyName("api24LibmpvAar")
        .optional()
    doLast {
        if (!api24LibmpvAar.asFile.isFile) {
            throw GradleException("Missing ${api24LibmpvAar.asFile}. Build or copy the Android 7/API 24 libmpv AAR before assembling the app.")
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
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

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
        getByName("release") {
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("../../core/src/main/resources")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.runtime)
    implementation(compose.ui)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(files(api24LibmpvAar))
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
