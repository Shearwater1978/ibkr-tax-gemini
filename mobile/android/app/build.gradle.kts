import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/** Git facts for the build stamp; "unknown" when git is not available. */
fun git(vararg args: String): String =
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.map { it.trim() }.getOrElse("")

val gitCommit = git("rev-parse", "--short=7", "HEAD").ifEmpty { "unknown" }
val gitDirty = git("status", "--porcelain").isNotEmpty()
val gitCommitCount = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
val buildTimeUtc: String = LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

android {
    namespace = "com.ibkrtax.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibkrtax.mobile"
        // Android 10, the supported minimum in mobile-app-core design.
        minSdk = 29
        targetSdk = 35
        // Commit count keeps increasing, so every newer build installs as an update.
        versionCode = gitCommitCount
        // e.g. 0.1.0+5c4e2a1, or 0.1.0+5c4e2a1-dirty when built with uncommitted changes.
        versionName = "0.1.0+" + gitCommit + if (gitDirty) "-dirty" else ""
        buildConfigField("String", "BUILD_TIME_UTC", "\"$buildTimeUtc\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Synthetic Flex Query fixtures shared with iOS and the Python reference tests.
    sourceSets["test"].resources.srcDir("../../fixtures")
    sourceSets["androidTest"].assets.srcDir("../../fixtures")
    // Debug builds can load the synthetic reports to exercise the UI; release builds never package them.
    sourceSets["debug"].assets.srcDir("../../fixtures")

    testOptions {
        unitTests.all {
            // Route any socket through a closed local SOCKS port so tests cannot reach the network.
            it.systemProperty("socksProxyHost", "127.0.0.1")
            it.systemProperty("socksProxyPort", "9")
        }
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)
    implementation(libs.bouncycastle)
    implementation(libs.play.services.blockstore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json for JVM unit tests; android.jar only ships stubs.
    testImplementation(libs.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
