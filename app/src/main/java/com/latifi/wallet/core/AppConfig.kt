package com.latifi.wallet.core

import com.latifi.wallet.BuildConfig

enum class AppMode { Demo, Live }

/**
 * Build-time configuration. Demo mode uses fakes, Live mode uses the real wallet and API.
 * Values come from local.properties, the environment or a placeholder (see app/build.gradle.kts).
 */
data class AppConfig(
    val reownProjectId: String,
    val amoyRpcUrl: String,
    val apiBaseUrl: String,
    val landingUrl: String,
    val termsUrl: String,
    val privacyUrl: String,
    val appIconUrl: String,
) {
    /** True only for a real 32 hex character id, never for the 32 zero placeholder. */
    val isWalletConfigured: Boolean
        get() = PROJECT_ID_PATTERN.matches(reownProjectId) && reownProjectId != PLACEHOLDER_PROJECT_ID

    /** The placeholder host uses the reserved .invalid TLD, so it never names a real server. */
    val isApiConfigured: Boolean
        get() = host(apiBaseUrl)?.endsWith(".invalid") == false

    val mode: AppMode
        get() = if (isWalletConfigured && isApiConfigured) AppMode.Live else AppMode.Demo

    /**
     * A release build ([liveRequired]) must never fall back to Demo mode, which would show a fake
     * wallet and invented transaction hashes as if they were real. It fails at start-up with a
     * message that names what is missing. Debug and CI builds pass `false` and keep Demo mode.
     */
    fun requireLiveWhenRequired(liveRequired: Boolean): AppConfig {
        if (liveRequired && mode != AppMode.Live) {
            val missing = buildList {
                if (!isWalletConfigured) add("the Reown project id")
                if (!isApiConfigured) add("the API base URL")
            }.joinToString(" and ")
            throw IllegalStateException("Release build without Live configuration: $missing is missing")
        }
        return this
    }

    companion object {
        const val PLACEHOLDER_PROJECT_ID = "00000000000000000000000000000000"
        private val PROJECT_ID_PATTERN = Regex("^[0-9a-f]{32}$")

        private fun host(url: String): String? =
            url.substringAfter("://", "").substringBefore('/').substringBefore(':')
                .takeIf { it.isNotEmpty() }?.lowercase()

        fun fromBuildConfig(): AppConfig = AppConfig(
            reownProjectId = BuildConfig.REOWN_PROJECT_ID,
            amoyRpcUrl = BuildConfig.AMOY_RPC_URL,
            apiBaseUrl = BuildConfig.API_BASE_URL,
            landingUrl = BuildConfig.LANDING_URL,
            termsUrl = BuildConfig.TERMS_URL,
            privacyUrl = BuildConfig.PRIVACY_URL,
            appIconUrl = BuildConfig.APP_ICON_URL,
        ).requireLiveWhenRequired(BuildConfig.LIVE_REQUIRED)
    }
}
