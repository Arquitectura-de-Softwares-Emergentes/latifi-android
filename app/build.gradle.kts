import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Build values come from the git-ignored local.properties, then the environment, then a
// placeholder. A blank value counts as absent so an empty CI variable falls back cleanly.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun cfg(key: String, env: String, default: String): String {
    val fromFile = localProps.getProperty(key)?.takeIf { it.isNotBlank() }
    val fromEnv = providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
    return fromFile ?: fromEnv ?: default
}

val reownProjectId = cfg("reown.projectId", "REOWN_PROJECT_ID", "00000000000000000000000000000000")
val apiBaseUrl = cfg("api.baseUrl", "LATIFI_API_BASE_URL", "https://latifi-api.invalid/api/v1/")

android {
    namespace = "com.latifi.wallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.latifi.wallet"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // A WalletConnect project id is not a secret, but a real one is never committed.
        buildConfigField(
            "String", "REOWN_PROJECT_ID",
            "\"$reownProjectId\"",
        )
        buildConfigField(
            "String", "AMOY_RPC_URL",
            "\"${cfg("amoy.rpcUrl", "AMOY_RPC_URL", "https://polygon-amoy-bor-rpc.publicnode.com")}\"",
        )
        // The .invalid TLD is reserved, so a placeholder host can never reach a real server.
        buildConfigField(
            "String", "API_BASE_URL",
            "\"$apiBaseUrl\"",
        )
        buildConfigField(
            "String", "LANDING_URL",
            "\"${cfg("landing.url", "LATIFI_LANDING_URL", "https://latifi.invalid")}\"",
        )
        buildConfigField(
            "String", "TERMS_URL",
            "\"${cfg("terms.url", "LATIFI_TERMS_URL", "https://latifi.invalid/terms")}\"",
        )
        buildConfigField(
            "String", "PRIVACY_URL",
            "\"${cfg("privacy.url", "LATIFI_PRIVACY_URL", "https://latifi.invalid/privacy")}\"",
        )
        buildConfigField(
            "String", "APP_ICON_URL",
            "\"${cfg("icon.url", "LATIFI_ICON_URL", "https://latifi.invalid/icon.png")}\"",
        )
    }

    buildTypes {
        // A release build must run Live. LIVE_REQUIRED makes the app refuse to start in Demo mode
        // (AppConfig.requireLiveWhenRequired), and verifyReleaseConfig below stops the build first.
        debug {
            buildConfigField("boolean", "LIVE_REQUIRED", "false")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "LIVE_REQUIRED", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // AGP generates the locale list from the values-* folders so it cannot drift.
    androidResources {
        generateLocaleConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        error += listOf("MissingTranslation", "ExtraTranslation")
    }
}

// A release build with the placeholder project id or the placeholder .invalid API host would
// silently run in Demo mode with a fake wallet and invented hashes, so it fails here instead.
// Debug and the CI debug APK are not affected: the task only hangs off the release pre-build.
val verifyReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails a release build that has no real Reown project id or API base URL."
    val projectId = reownProjectId
    val baseUrl = apiBaseUrl
    doLast {
        val problems = mutableListOf<String>()
        if (!Regex("^[0-9a-f]{32}$").matches(projectId) || projectId == "00000000000000000000000000000000") {
            problems += "REOWN_PROJECT_ID (or reown.projectId in local.properties) is missing or not a real 32 hex character id"
        }
        val host = baseUrl.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
        if (host.isEmpty() || host.endsWith(".invalid")) {
            problems += "LATIFI_API_BASE_URL (or api.baseUrl in local.properties) is missing or still the .invalid placeholder"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "A release build must run in Live mode, but: " + problems.joinToString("; ") +
                    ". Set them or build the debug variant.",
            )
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseConfig) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.reown.bom))
    implementation(platform(libs.okhttp.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navsuite)
    implementation(libs.androidx.compose.material.m2)             // AppKit host only
    implementation(libs.androidx.compose.material.navigation)     // AppKit host only
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.browser)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // junit:junit:4.12 leaks through kethereum and spongycastle and breaks androidTest resolution
    implementation(libs.reown.core) { exclude(group = "junit", module = "junit") }
    implementation(libs.reown.appkit) { exclude(group = "junit", module = "junit") }
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.web3j.abi)      // only abi and utils, see docs/spikes/reown-appkit.md
    implementation(libs.web3j.utils)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
