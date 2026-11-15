package com.latifi.wallet.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** S17 on a device: row sizes, radio lists, the copy announcement, the dialog and the attribution link. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class ProfileScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(res: Int) = context.getString(res)

    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private val events = mutableListOf<ProfileEvent>()

    private val full = ProfileUiState(
        displayName = "Ana Torres",
        contact = Contact(ContactType.EMAIL, "ana@example.com"),
        shortAddress = "0x5290...9EE7",
        role = AppRole.Borrower,
        language = AppLanguage.English,
        currency = LocalCurrency.PEN,
        versionName = "0.1.0",
        mode = AppMode.Demo,
    )

    /** Shows the screen. A language, a currency and the dialog react like the real view model. */
    private fun show(initial: ProfileUiState = full) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = false) {
                var state by remember { mutableStateOf(initial) }
                ProfileScreen(
                    state = state,
                    title = text(R.string.nav_profile),
                    onEvent = { event ->
                        events += event
                        state = when (event) {
                            is ProfileEvent.SelectLanguage -> state.copy(language = event.language)
                            is ProfileEvent.SelectCurrency -> state.copy(currency = event.currency)
                            ProfileEvent.AskDisconnect -> state.copy(disconnectDialogOpen = true)
                            ProfileEvent.KeepConnected -> state.copy(disconnectDialogOpen = false)
                            ProfileEvent.MessageShown -> state.copy(message = null)
                            else -> state
                        }
                    },
                )
            }
        }
    }

    @Test
    fun everyRowIsAtLeast56dpHigh() {
        show()
        val labels = listOf(
            text(R.string.profile_edit),
            text(R.string.profile_copy_address),
            text(R.string.disconnect_action),
            text(R.string.profile_role_switch_lender),
            text(R.string.language_name_en),
            text(R.string.language_name_es),
            text(R.string.currency_pen),
            text(R.string.currency_cop),
            text(R.string.currency_mxn),
            text(R.string.currency_ars),
            text(R.string.currency_brl),
            text(R.string.currency_clp),
            text(R.string.profile_how),
            text(R.string.terms_link),
            text(R.string.privacy_link),
            text(R.string.profile_version, "0.1.0"),
            text(R.string.profile_network),
            text(R.string.profile_mode_demo),
        )
        labels.forEach { label ->
            rule.onNodeWithText(label).performScrollTo().assertHeightIsAtLeast(56.dp)
        }
    }

    /** A node of the dialog window. The dialog confirm and the Disconnect row share the same text. */
    private fun dialogText(res: Int) = rule.onNode(hasText(text(res)) and hasAnyAncestor(isDialog()))

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    @Test
    fun theGroupsAreHeadings() {
        show()
        val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
        listOf(
            R.string.profile_section_personal, R.string.profile_section_wallet, R.string.profile_section_prefs,
            R.string.profile_section_help, R.string.profile_section_about,
        ).forEach { rule.onNodeWithText(text(it)).performScrollTo().assert(heading) }
    }

    @Test
    fun theLanguageListHasRadioSemanticsAndTheSelectedState() {
        show()
        rule.onNodeWithText("English").performScrollTo().assert(isRadio).assertIsSelected()
        rule.onNodeWithText("Español").performScrollTo().assert(isRadio).assertIsNotSelected()

        rule.onNodeWithText("Español").performClick()

        rule.onNodeWithText("Español").assertIsSelected()
        rule.onNodeWithText("English").assertIsNotSelected()
        assertTrue(events.contains(ProfileEvent.SelectLanguage(AppLanguage.Spanish)))
    }

    @Test
    fun theCurrencyListShowsTheSixOptionsAndPenIsSelected() {
        show()
        val names = listOf(
            R.string.currency_pen, R.string.currency_cop, R.string.currency_mxn,
            R.string.currency_ars, R.string.currency_brl, R.string.currency_clp,
        )
        names.forEach { rule.onNodeWithText(text(it)).performScrollTo().assert(isRadio) }
        rule.onNodeWithText(text(R.string.currency_pen)).assertIsSelected()
        rule.onNodeWithText(text(R.string.currency_clp)).performClick().assertIsSelected()
        assertTrue(events.contains(ProfileEvent.SelectCurrency(LocalCurrency.CLP)))
    }

    @Test
    fun copyAddressSendsItsEventAndTheAnnouncementShows() {
        show()
        rule.onNodeWithText(text(R.string.profile_copy_address)).performScrollTo().assert(isButton).performClick()
        assertEquals(listOf<ProfileEvent>(ProfileEvent.CopyAddress), events)
    }

    @Test
    fun theCopiedMessageAppearsInASnackbar() {
        show(full.copy(message = ProfileMessage.AddressCopied))
        rule.onNodeWithText(text(R.string.profile_address_copied)).assertIsDisplayed()
    }

    @Test
    fun disconnectOpensTheDialogWithTheSafeActionFocusedFirst() {
        // The v2 rule starts in touch mode, where a button cannot hold input focus. A person with a
        // keyboard or a D-pad is not in touch mode, so the window leaves it first (one attempt, see deferred-items).
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        show()
        rule.onNodeWithText(text(R.string.disconnect_action)).performScrollTo().assert(isButton).performClick()

        rule.onNodeWithText(text(R.string.disconnect_title)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.disconnect_body)).assertIsDisplayed()
        dialogText(R.string.disconnect_confirm).assertIsDisplayed()
        rule.waitForIdle()
        rule.onNodeWithText(text(R.string.disconnect_keep)).assertIsDisplayed().assertIsFocused()
    }

    @Test
    fun theSafeChoiceClosesTheDialogWithoutDisconnecting() {
        show()
        rule.onNodeWithText(text(R.string.disconnect_action)).performScrollTo().performClick()
        rule.onNodeWithText(text(R.string.disconnect_keep)).performClick()

        rule.onNodeWithText(text(R.string.disconnect_title)).assertDoesNotExist()
        assertTrue(events.none { it == ProfileEvent.ConfirmDisconnect })
    }

    @Test
    fun theConfirmChoiceSendsTheDisconnectEvent() {
        show()
        rule.onNodeWithText(text(R.string.disconnect_action)).performScrollTo().performClick()
        dialogText(R.string.disconnect_confirm).performClick()
        assertTrue(events.contains(ProfileEvent.ConfirmDisconnect))
    }

    @Test
    fun theAttributionLinkIsA48dpButton() {
        show()
        rule.onNodeWithText(text(R.string.rates_attribution)).performScrollTo()
            .assert(isButton)
            .assertHeightIsAtLeast(48.dp)
            .performClick()
        assertTrue(events.contains(ProfileEvent.OpenRatesProvider))
    }

    @Test
    fun theAboutGroupShowsTheModeOfTheBuild() {
        show(full.copy(mode = AppMode.Live))
        rule.onNodeWithText(text(R.string.profile_mode_live)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.profile_mode_demo)).assertDoesNotExist()
    }
}
