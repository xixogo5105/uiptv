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

val libmpvProfile = providers.gradleProperty("uiptv.libmpv.profile")
    .orElse("maven")
    .map(String::trim)
    .get()
val useApi24LocalLibmpv = when (libmpvProfile) {
    "maven" -> false
    "api24Local" -> true
    else -> throw GradleException(
        "Unsupported uiptv.libmpv.profile '$libmpvProfile'. Use 'maven' or 'api24Local'."
    )
}

android {
    namespace = "com.uiptv.mobile.android"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.uiptv.mobile"
        minSdk = if (useApi24LocalLibmpv) {
            libs.versions.android.min.sdk.api24.get().toInt()
        } else {
            libs.versions.android.min.sdk.maven.get().toInt()
        }
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
    implementation(libs.androidx.media3.ui)
    if (useApi24LocalLibmpv) {
        val api24Aar = file("libs/libmpv-android-api24.aar")
        if (!api24Aar.isFile) {
            throw GradleException(
                "Missing $api24Aar. Run ../scripts/build-mobile-with-libmpv.sh or use -Puiptv.libmpv.profile=maven."
            )
        }
        implementation(files(api24Aar))
    } else {
        implementation(libs.libmpv)
    }
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.core)
}
