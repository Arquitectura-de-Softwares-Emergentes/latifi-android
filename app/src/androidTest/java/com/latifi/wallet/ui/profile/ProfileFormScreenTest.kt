package com.latifi.wallet.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.support.InMemoryPrefsStore
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.nav.RouteArgs
import com.latifi.wallet.ui.theme.LatiFiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * S05 on a device. The screen runs with the real view model over fakes, so the error summary,
 * the first-invalid-field focus and the contact type switch are exercised end to end.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ProfileFormScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private lateinit var viewModel: ProfileFormViewModel

    private fun show(fontScale: Float? = null, fromGate: Boolean = true) {
        rule.enableAccessibilityChecks()
        val sessions = InMemorySessionStore()
        runBlocking { sessions.save(Session("0xAbC0000000000000000000000000000000000001", "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        val config = AppConfig(
            reownProjectId = AppConfig.PLACEHOLDER_PROJECT_ID,
            amoyRpcUrl = "https://rpc.example/",
            apiBaseUrl = "https://api.latifi.invalid/api/v1/",
            landingUrl = "https://latifi.invalid",
            termsUrl = "https://latifi.invalid/terms",
            privacyUrl = "https://latifi.invalid/privacy",
            appIconUrl = "https://latifi.invalid/icon.png",
        )
        viewModel = ProfileFormViewModel(
            SavedStateHandle(mapOf(RouteArgs.GATE to fromGate)),
            FakeProfileRepository(),
            sessions,
            InMemoryPrefsStore(role = AppRole.Borrower),
            config,
        )
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                val state by viewModel.state.collectAsState()
                if (fontScale != null) {
                    WithFontScale(fontScale) { Form(state) }
                } else {
                    Form(state)
                }
            }
        }
    }

    @Composable
    private fun Form(state: ProfileFormState) {
        ProfileFormScreen(state = state, onEvent = viewModel::onEvent, onBack = {})
    }

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    @Test
    fun labelsAreVisibleAndStayPutAfterTyping() {
        show()
        rule.onNodeWithText(text(R.string.field_name), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.field_contact_email), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.field_contact_type)).assertIsDisplayed()

        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Ana Torres")

        rule.onNodeWithText(text(R.string.field_name), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theGateShowsItsTitleAndTheBodyForTheRole() {
        show()
        rule.onNodeWithText(text(R.string.profile_gate_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.profile_gate_body_borrow)).assertIsDisplayed()
    }

    @Test
    fun aSubmitWithAnEmptyNameAndABadContactShowsBothErrorsAndOneSummary() {
        show()
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("nope")

        rule.onNodeWithText(text(R.string.profile_save)).performScrollTo().performClick()

        rule.onNodeWithText(text(R.string.err_name_required), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.err_contact_email), useUnmergedTree = true).assertIsDisplayed()
        val summary = context.resources.getQuantityString(R.plurals.profile_errors_count, 2, 2)
        assertEquals("2 fields need your attention.", summary)
        rule.onNodeWithText(summary).performScrollTo().assertIsDisplayed().assert(polite)
    }

    @Test
    fun theFirstInvalidFieldTakesFocus() {
        show()
        rule.onNodeWithText(text(R.string.profile_save)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[0].assertIsFocused()
    }

    @Test
    fun withTheNameFilledTheContactFieldTakesFocus() {
        show()
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("Ana Torres")
        rule.onNodeWithText(text(R.string.profile_save)).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodes(hasSetTextAction())[1].assertIsFocused()
    }

    @Test
    fun switchingToPhoneChangesTheLabelAndTheAutofillHint() {
        show()
        val email = SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.EmailAddress)
        val phone = SemanticsMatcher.expectValue(SemanticsProperties.ContentType, ContentType.PhoneNumber)
        rule.onAllNodes(hasSetTextAction())[1].assert(email)

        rule.onNodeWithText(text(R.string.contact_phone)).performClick()

        rule.onNodeWithText(text(R.string.field_contact_phone), useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodes(hasSetTextAction())[1].assert(phone)
    }

    @Test
    fun anEightyCharacterNameWrapsWithoutTruncationAtFontScaleTwo() {
        show(fontScale = 2f)
        val longName = "Maria de los Angeles Fernandez de la Torre y Villanueva Castillo ".trim().padEnd(80, 'x')
        assertEquals(80, longName.length)

        rule.onAllNodes(hasSetTextAction())[0].performTextInput(longName)

        rule.onAllNodes(hasSetTextAction())[0].assert(SemanticsMatcher("shows the whole name") {
            it.config.getOrNull(SemanticsProperties.EditableText)?.text == longName
        })
        rule.assertNoTruncatedText()
    }

    @Test
    fun theTermsAndPrivacyLinksAreAtLeast48dpHigh() {
        show()
        rule.onNodeWithText(text(R.string.terms_link)).performScrollTo().assertHeightIsAtLeast(48.dp)
        rule.onNodeWithText(text(R.string.privacy_link)).performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun thePrivacyLineIsShownUnderTheButton() {
        show()
        rule.onNodeWithText(text(R.string.contact_privacy)).performScrollTo().assertIsDisplayed()
    }
}
