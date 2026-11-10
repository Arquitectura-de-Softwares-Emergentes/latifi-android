package com.latifi.wallet

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.data.language.AppCompatLanguageController
import com.latifi.wallet.domain.language.AppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The per-app language through `AppCompatDelegate.setApplicationLocales`. The same class runs on
 * the API 34 emulator (platform support) and on the API 30 emulator (the AppCompat backport that
 * stores the choice in `AppLocalesMetadataHolderService`), which is why it has no SdkSuppress.
 *
 * Each test ends by clearing the app locale, so a later test class starts from the device language.
 */
@RunWith(AndroidJUnit4::class)
class LocaleSwitchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val controller = AppCompatLanguageController()

    /** A string as it reads in [tag], read through a configuration context so the device stays as is. */
    private fun stringIn(tag: String, res: Int): String {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return context.createConfigurationContext(config).getString(res)
    }

    private fun setLanguage(language: AppLanguage) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.set(language) }
    }

    @After
    fun clearTheChoice() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        }
    }

    @Test
    fun spanishAndEnglishResourcesDiffer() {
        assertNotEquals(stringIn("en-US", R.string.nav_home), stringIn("es-419", R.string.nav_home))
    }

    @Test
    fun settingEs419RecreatesTheActivityAndTheStringsReadInSpanish() {
        val spanish = stringIn("es-419", R.string.nav_home)
        val english = stringIn("en-US", R.string.nav_home)
        setLanguage(AppLanguage.English)
        rule.waitUntil(timeoutMillis = 10_000) { rule.activity.getString(R.string.nav_home) == english }

        setLanguage(AppLanguage.Spanish)

        rule.waitUntil(timeoutMillis = 10_000) { rule.activity.getString(R.string.nav_home) == spanish }
        assertEquals(AppLanguage.Spanish, controller.current())
    }

    @Test
    fun theChoiceIsStillEs419AfterASecondRecreation() {
        val spanish = stringIn("es-419", R.string.nav_home)
        setLanguage(AppLanguage.Spanish)
        rule.waitUntil(timeoutMillis = 10_000) { rule.activity.getString(R.string.nav_home) == spanish }

        rule.activityRule.scenario.recreate()

        rule.waitUntil(timeoutMillis = 10_000) { rule.activity.getString(R.string.nav_home) == spanish }
        assertTrue(AppCompatDelegate.getApplicationLocales().toLanguageTags().startsWith("es"))
        assertEquals(AppLanguage.Spanish, controller.current())
    }

    @Test
    fun theFirstScreenShowsInSpanishAfterTheSwitch() {
        val titles = listOf(R.string.onb1_title, R.string.connect_title, R.string.connect_title_return)
            .map { stringIn("es-419", it) }
        setLanguage(AppLanguage.Spanish)

        rule.waitUntil(timeoutMillis = 10_000) {
            titles.any { rule.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun aDeviceRequestForEsPeResolvesToTheEs419Resources() {
        // Research assumption A1: Android falls back from es-PE to the es-419 folder.
        assertEquals(stringIn("es-419", R.string.nav_home), stringIn("es-PE", R.string.nav_home))
        assertEquals(stringIn("es-419", R.string.profile_section_wallet), stringIn("es-MX", R.string.profile_section_wallet))
    }

    @Test
    fun anUnsupportedDeviceLanguageResolvesToTheDefaultEnglishResources() {
        assertEquals(stringIn("en-US", R.string.nav_home), stringIn("fr-FR", R.string.nav_home))
    }
}
