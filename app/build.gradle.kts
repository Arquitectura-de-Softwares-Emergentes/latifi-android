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

// A numeric value that is not a number is a build error, so a typo cannot ship a wrong version.
fun cfgInt(key: String, env: String, default: Int): Int {
    val raw = cfg(key, env, default.toString())
    return raw.trim().toIntOrNull()
        ?: throw GradleException("$key (or $env) must be a whole number, but the value is not numeric")
}

val reownProjectId = cfg("reown.projectId", "REOWN_PROJECT_ID", "00000000000000000000000000000000")
val apiBaseUrl = cfg("api.baseUrl", "LATIFI_API_BASE_URL", "https://latifi-api.invalid/api/v1/")

// The live landing page of plan 06-07 is the default for the three public links, so a release build
// points at real pages. The environment and local.properties still override each one.
val landingBase = "https://arquitectura-de-softwares-emergentes.github.io/latifi-landing/"
val landingUrl = cfg("landing.url", "LATIFI_LANDING_URL", landingBase)
val termsUrl = cfg("terms.url", "LATIFI_TERMS_URL", landingBase + "terms.html")
val privacyUrl = cfg("privacy.url", "LATIFI_PRIVACY_URL", landingBase + "privacy.html")

val appVersionCode = cfgInt("version.code", "LATIFI_VERSION_CODE", 1)
val appVersionName = cfg("version.name", "LATIFI_VERSION_NAME", "0.1.0")

// Release signing material. The key lives outside the repository. None of the four values is ever
// printed, logged or written into a build file, and the release build type only gets the signing
// config when all four are present, so debug and CI builds work without a key.
val signingStoreFile = cfg("signing.storeFile", "LATIFI_KEYSTORE_PATH", "")
val signingStorePassword = cfg("signing.storePassword", "LATIFI_KEYSTORE_PASSWORD", "")
val signingKeyAlias = cfg("signing.keyAlias", "LATIFI_KEY_ALIAS", "")
val signingKeyPassword = cfg("signing.keyPassword", "LATIFI_KEY_PASSWORD", "")
val hasSigning = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword)
    .all { it.isNotBlank() }

android {
    namespace = "com.latifi.wallet"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.latifi.wallet"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName
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
            "\"$landingUrl\"",
        )
        buildConfigField(
            "String", "TERMS_URL",
            "\"$termsUrl\"",
        )
        buildConfigField(
            "String", "PRIVACY_URL",
            "\"$privacyUrl\"",
        )
        buildConfigField(
            "String", "APP_ICON_URL",
            "\"${cfg("icon.url", "LATIFI_ICON_URL", "https://latifi.invalid/icon.png")}\"",
        )
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
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
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
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
// silently run in Demo mode with a fake wallet and invented hashes, and one without a key outside the
// repository would be unsigned and uninstallable, so it fails here instead. Debug and the CI debug
// APK are not affected: the task only hangs off the release pre-build. The messages name each input
// and never repeat a secret value.
val verifyReleaseConfig by tasks.registering {
    group = "verification"
    description = "Fails a release build that lacks a real Reown project id, API base URL, signing key or version."
    val projectId = reownProjectId
    val baseUrl = apiBaseUrl
    val keystorePath = signingStoreFile
    val storePassword = signingStorePassword
    val alias = signingKeyAlias
    val keyPassword = signingKeyPassword
    val versionName = appVersionName
    val projectRoot = rootProject.projectDir
    doLast {
        val problems = mutableListOf<String>()
        if (!Regex("^[0-9a-f]{32}$").matches(projectId) || projectId == "00000000000000000000000000000000") {
            problems += "REOWN_PROJECT_ID (or reown.projectId in local.properties) is missing or not a real 32 hex character id"
        }
        val host = baseUrl.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
        if (host.isEmpty() || host.endsWith(".invalid")) {
            problems += "LATIFI_API_BASE_URL (or api.baseUrl in local.properties) is missing or still the .invalid placeholder"
        }
        if (!baseUrl.startsWith("https://") || !baseUrl.endsWith("/api/v1/")) {
            problems += "LATIFI_API_BASE_URL must start with https:// and end with /api/v1/"
        }
        if (keystorePath.isBlank()) {
            problems += "LATIFI_KEYSTORE_PATH (or signing.storeFile) is blank"
        } else {
            val keystore = File(keystorePath)
            if (!keystore.isFile) {
                problems += "the keystore file named by LATIFI_KEYSTORE_PATH does not exist"
            } else {
                val root = projectRoot.canonicalFile.toPath()
                if (keystore.canonicalFile.toPath().startsWith(root)) {
                    problems += "the keystore lives inside the project directory, keep the release key outside the repository"
                }
            }
        }
        if (alias.isBlank()) problems += "LATIFI_KEY_ALIAS (or signing.keyAlias) is blank"
        if (storePassword.isBlank()) problems += "LATIFI_KEYSTORE_PASSWORD (or signing.storePassword) is blank"
        if (keyPassword.isBlank()) problems += "LATIFI_KEY_PASSWORD (or signing.keyPassword) is blank"
        if (!Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(-untagged)?$").matches(versionName)) {
            problems += "LATIFI_VERSION_NAME (or version.name) must look like 1.2.3, with an optional -untagged suffix"
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "A release build must run in Live mode and be signed, but: " + problems.joinToString("; ") +
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
