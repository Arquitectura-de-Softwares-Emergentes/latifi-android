package com.latifi.wallet.support

import com.latifi.wallet.core.AppConfig

/** A config with made up values. The hosts use the reserved .invalid and .example names. */
fun testConfig() = AppConfig(
    reownProjectId = AppConfig.PLACEHOLDER_PROJECT_ID,
    amoyRpcUrl = "https://rpc.example/",
    apiBaseUrl = "https://api.latifi.invalid/api/v1/",
    landingUrl = "https://latifi.invalid",
    termsUrl = "https://latifi.invalid/terms",
    privacyUrl = "https://latifi.invalid/privacy",
    appIconUrl = "https://latifi.invalid/icon.png",
)
