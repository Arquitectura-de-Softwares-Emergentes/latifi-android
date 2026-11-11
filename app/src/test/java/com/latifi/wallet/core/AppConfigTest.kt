package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {
    private fun config(
        projectId: String = AppConfig.PLACEHOLDER_PROJECT_ID,
        apiBaseUrl: String = "https://latifi-api.invalid/api/v1/",
    ) = AppConfig(
        reownProjectId = projectId,
        amoyRpcUrl = "https://polygon-amoy-bor-rpc.publicnode.com",
        apiBaseUrl = apiBaseUrl,
        landingUrl = "https://latifi.invalid",
        termsUrl = "https://latifi.invalid/terms",
        privacyUrl = "https://latifi.invalid/privacy",
        appIconUrl = "https://latifi.invalid/icon.png",
    )

    @Test
    fun placeholderProjectIdIsNotConfigured() {
        assertFalse(config().isWalletConfigured)
    }

    @Test
    fun thirtyTwoHexProjectIdIsConfigured() {
        assertTrue(config(projectId = "0123456789abcdef0123456789abcdef").isWalletConfigured)
    }

    @Test
    fun malformedProjectIdsAreNotConfigured() {
        assertFalse(config(projectId = "").isWalletConfigured)
        assertFalse(config(projectId = "abc123").isWalletConfigured)
        assertFalse(config(projectId = "0123456789ABCDEF0123456789ABCDEF").isWalletConfigured)
    }

    @Test
    fun invalidHostMeansApiNotConfiguredAndDemoMode() {
        val withRealId = config(projectId = "0123456789abcdef0123456789abcdef")
        assertFalse(withRealId.isApiConfigured)
        assertEquals(AppMode.Demo, withRealId.mode)
        assertEquals(AppMode.Demo, config().mode)
    }

    @Test
    fun realHostAndRealIdMeansLiveMode() {
        val live = config(
            projectId = "0123456789abcdef0123456789abcdef",
            apiBaseUrl = "https://api.example.org/api/v1/",
        )
        assertTrue(live.isApiConfigured)
        assertEquals(AppMode.Live, live.mode)
    }

    @Test
    fun realHostWithPlaceholderIdStaysInDemoMode() {
        val demo = config(apiBaseUrl = "https://api.example.org/api/v1/")
        assertTrue(demo.isApiConfigured)
        assertEquals(AppMode.Demo, demo.mode)
    }

    @Test
    fun releaseWithoutAnyConfigurationFailsInsteadOfRunningDemo() {
        val error = runCatching { config().requireLiveWhenRequired(liveRequired = true) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("project id"))
        assertTrue(error.message!!.contains("API base URL"))
    }

    @Test
    fun releaseWithOnlyTheProjectIdFailsNamingTheApi() {
        val error = runCatching {
            config(projectId = "0123456789abcdef0123456789abcdef").requireLiveWhenRequired(liveRequired = true)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("API base URL"))
        assertFalse(error.message!!.contains("project id"))
    }

    @Test
    fun releaseWithOnlyTheApiFailsNamingTheProjectId() {
        val error = runCatching {
            config(apiBaseUrl = "https://api.example.org/api/v1/").requireLiveWhenRequired(liveRequired = true)
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("project id"))
    }

    @Test
    fun releaseWithLiveConfigurationStarts() {
        val live = config(
            projectId = "0123456789abcdef0123456789abcdef",
            apiBaseUrl = "https://api.example.org/api/v1/",
        )
        assertEquals(live, live.requireLiveWhenRequired(liveRequired = true))
    }

    @Test
    fun debugAndCiBuildsKeepDemoMode() {
        val demo = config()
        assertEquals(AppMode.Demo, demo.requireLiveWhenRequired(liveRequired = false).mode)
    }
}
