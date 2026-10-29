package com.latifi.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    @Test
    fun applicationIdIsStable() {
        assertEquals("com.latifi.wallet", BuildConfig.APPLICATION_ID)
    }
}
