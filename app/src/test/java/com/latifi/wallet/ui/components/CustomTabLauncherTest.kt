package com.latifi.wallet.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomTabLauncherTest {
    @Test
    fun acceptsPlainHttpsAddresses() {
        assertTrue(CustomTabLauncher.isAllowed("https://www.exchangerate-api.com"))
        assertTrue(CustomTabLauncher.isAllowed("https://latifi.invalid/terms"))
        assertTrue(CustomTabLauncher.isAllowed("HTTPS://Example.com/path?x=1"))
    }

    @Test
    fun rejectsEveryOtherScheme() {
        assertFalse(CustomTabLauncher.isAllowed("http://example.com"))
        assertFalse(CustomTabLauncher.isAllowed("javascript:alert(1)"))
        assertFalse(CustomTabLauncher.isAllowed("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(CustomTabLauncher.isAllowed("file:///sdcard/a.html"))
        assertFalse(CustomTabLauncher.isAllowed("latifi-wallet://request"))
        assertFalse(CustomTabLauncher.isAllowed("content://com.example/doc"))
    }

    @Test
    fun rejectsMalformedAndHostlessInput() {
        assertFalse(CustomTabLauncher.isAllowed(""))
        assertFalse(CustomTabLauncher.isAllowed("not a url"))
        assertFalse(CustomTabLauncher.isAllowed("https://"))
        assertFalse(CustomTabLauncher.isAllowed("https:///path"))
        assertFalse(CustomTabLauncher.isAllowed("//example.com"))
    }

    @Test
    fun rejectsUserInfoThatHidesTheRealHost() {
        assertFalse(CustomTabLauncher.isAllowed("https://exchangerate-api.com@evil.example/"))
        assertFalse(CustomTabLauncher.isAllowed("https://user:pass@example.com/"))
    }
}
