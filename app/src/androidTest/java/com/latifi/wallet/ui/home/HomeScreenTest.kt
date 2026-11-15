package com.latifi.wallet.ui.home

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.latifi.wallet.R
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.BorrowerReputation
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.model.RateQuote
import com.latifi.wallet.domain.model.RateSnapshot
import com.latifi.wallet.support.WithFontScale
import com.latifi.wallet.support.assertNoTruncatedText
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.theme.LatiFiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

/**
 * S06 on a device for both roles: the spoken balance, the role blocks, exactly one primary action
 * per variant, the fee banner without a button, the zero balance, a failed section next to healthy
 * ones, the greeting, 200 percent text, both themes and a picture of each theme for the workflow.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 34)
class HomeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val locale = context.resources.configuration.locales[0]
    private val events = mutableListOf<HomeEvent>()
    private val paid = mutableListOf<BigInteger>()
    private var requested = 0
    private var browsed = 0

    private fun text(res: Int, vararg args: Any) = context.getString(res, *args)

    private val asOf = Instant.parse("2026-11-10T12:00:00Z")
    private val ready = RatesState.Ready(
        RateSnapshot(listOf(RateQuote("USD", "PEN", BigDecimal("3.75"), asOf, stale = false)), asOf, stale = false),
    )
    private val thousand = TokenAmount.ofTokens(1_000)
    private val due = NextDueCard(BigInteger.valueOf(11), TokenAmount.ofTokens(83), Instant.now().plusSeconds(3 * 86_400))

    private fun borrower(
        balance: Loadable<TokenAmount> = Loadable.Success(thousand),
        reputation: Loadable<BorrowerReputation?> = Loadable.Success(BorrowerReputation(620, ReputationLevel.TRUSTED)),
        nextInstallment: Loadable<NextDueCard?> = Loadable.Success(due),
        lowFee: Boolean = false,
        greeting: String? = null,
    ) = HomeUiState(
        role = AppRole.Borrower,
        balance = balance,
        reputation = reputation,
        nextInstallment = nextInstallment,
        lowFee = lowFee,
        greeting = greeting,
        rates = ready,
    )

    private fun lender(
        openRequests: Loadable<Long> = Loadable.Success(6),
        nextPayment: Loadable<NextDueCard?> = Loadable.Success(due),
    ) = HomeUiState(
        role = AppRole.Lender,
        balance = Loadable.Success(thousand),
        openRequests = openRequests,
        nextPayment = nextPayment,
        rates = ready,
    )

    private fun show(state: HomeUiState, darkTheme: Boolean = false, fontScale: Float? = null) {
        rule.enableAccessibilityChecks()
        rule.setContent {
            LatiFiTheme(darkTheme = darkTheme) {
                val screen = @Composable {
                    HomeScreen(
                        state = state,
                        onEvent = { events += it },
                        onPay = { paid += it },
                        onRequestLoan = { requested += 1 },
                        onBrowse = { browsed += 1 },
                    )
                }
                if (fontScale != null) WithFontScale(fontScale) { screen() } else screen()
            }
        }
    }

    private val spokenBalance: String
        get() {
            val local = LocalCurrency.PEN.format(BigDecimal("3750.00"), locale)
            return listOf(text(R.string.home_balance), thousand.format(locale), text(R.string.local_about_a11y, local)).joinToString(", ")
        }

    @Test
    fun theBalanceIsSpokenAsOnePhraseWithItsLocalValue() {
        show(borrower())

        rule.onNodeWithContentDescription(spokenBalance).assertIsDisplayed()
    }

    @Test
    fun theBorrowerSeesTheChipTheNextInstallmentAndOnePrimaryRequestButton() {
        show(borrower())

        rule.onNodeWithContentDescription(text(R.string.rep_chip_a11y, text(R.string.rep_level_trusted), 620)).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.home_next_installment)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.pay_cta)).performScrollTo().assertHasClickAction()
        rule.onNodeWithText(text(R.string.request_title)).performScrollTo().assertHasClickAction().performClick()
        assertEquals(1, requested)
        // The lender's primary does not exist for a borrower.
        rule.onAllNodesWithTextCount(text(R.string.home_browse), 0)
    }

    @Test
    fun payInstallmentSendsTheLoanOfTheCard() {
        show(borrower())

        rule.onNodeWithText(text(R.string.pay_cta)).performScrollTo().performClick()

        assertEquals(listOf(BigInteger.valueOf(11)), paid)
    }

    @Test
    fun aBorrowerWithNothingDueSeesTheNoInstallmentText() {
        show(borrower(nextInstallment = Loadable.Success(null)))

        rule.onNodeWithText(text(R.string.home_no_installment)).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTextCount(text(R.string.pay_cta), 0)
    }

    @Test
    fun noReputationYetDrawsNoChip() {
        show(borrower(reputation = Loadable.Success(null)))

        rule.onAllNodesWithTextCount(text(R.string.rep_level_trusted), 0)
        rule.onNodeWithContentDescription(spokenBalance).assertIsDisplayed()
    }

    @Test
    fun theLenderSeesTheOpenRequestsCountAndBrowseAsTheSinglePrimary() {
        show(lender())

        rule.onNodeWithContentDescription("${text(R.string.home_open_requests)}, 6").assertIsDisplayed()
        rule.onNodeWithText(text(R.string.home_next_payment_in)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.home_browse)).performScrollTo().assertHasClickAction().performClick()
        assertEquals(1, browsed)
        rule.onAllNodesWithTextCount(text(R.string.request_title), 0)
        rule.onAllNodesWithTextCount(text(R.string.pay_cta), 0)
    }

    @Test
    fun aLenderWithNoPaymentExpectedSeesTheNoPaymentText() {
        show(lender(nextPayment = Loadable.Success(null)))

        rule.onNodeWithText(text(R.string.home_no_payment_in)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLowFeeBannerHasTheTextAndNoButton() {
        show(borrower(lowFee = true))

        val banner = rule.onNodeWithText(text(R.string.home_fee_low))
        banner.performScrollTo().assertIsDisplayed()
        banner.assert(!hasClickAction())
        banner.assert(!hasAnyAncestor(hasClickAction()))
    }

    @Test
    fun withoutALowFeeThereIsNoBanner() {
        show(borrower(lowFee = false))

        rule.onAllNodesWithTextCount(text(R.string.home_fee_low), 0)
    }

    @Test
    fun aZeroBalanceExplainsItselfAndPointsToTheCoinsAction() {
        show(borrower(balance = Loadable.Success(TokenAmount(BigInteger.ZERO))))

        rule.onNodeWithText(text(R.string.home_zero_balance)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.coins_cta)).performScrollTo().assertHasClickAction().performClick()
        assertEquals(listOf<HomeEvent>(HomeEvent.GetCoins), events)
    }

    @Test
    fun theGetCoinsActionIsThereForTheLenderToo() {
        show(lender())

        rule.onNodeWithText(text(R.string.coins_cta)).performScrollTo().assertHasClickAction()
    }

    @Test
    fun aFailedSectionShowsReloadAndTheOtherSectionsStay() {
        show(borrower(reputation = Loadable.Error(ErrorKind.Offline)))

        rule.onNodeWithText(text(R.string.err_offline_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription(spokenBalance).assertIsDisplayed()
        rule.onNodeWithText(text(R.string.home_next_installment)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        assertEquals(listOf<HomeEvent>(HomeEvent.Reload(HomeSection.Reputation)), events)
    }

    @Test
    fun aFailedBalanceKeepsTheRoleBlocks() {
        show(borrower(balance = Loadable.Error(ErrorKind.Server)))

        rule.onNodeWithText(text(R.string.err_server_title)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.home_next_installment)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(text(R.string.retry_reload)).performScrollTo().performClick()
        assertEquals(listOf<HomeEvent>(HomeEvent.Reload(HomeSection.Balance)), events)
    }

    @Test
    fun theGreetingIsAVisibleRowAndAPoliteAnnouncement() {
        show(borrower(greeting = "Ana"))

        val greeting = rule.onNodeWithText(text(R.string.welcome_back, "Ana"))
        greeting.assertIsDisplayed()
        greeting.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun noGreetingMeansNoRow() {
        show(borrower(greeting = null))

        rule.onAllNodesWithTextCount(text(R.string.welcome_back, "Ana"), 0)
    }

    @Test
    fun theAttributionRowIsOnTheScreen() {
        show(borrower())

        rule.onNodeWithText(text(R.string.rates_attribution)).performScrollTo().assertHasClickAction()
    }

    @Test
    fun atTwoHundredPercentTextNothingIsTruncatedForTheBorrower() {
        show(borrower(lowFee = true, greeting = "Ana"), fontScale = 2.0f)

        rule.onNodeWithText(text(R.string.home_fee_low)).assertExists()
        rule.assertNoTruncatedText()
    }

    @Test
    fun atTwoHundredPercentTextNothingIsTruncatedForTheLender() {
        show(lender(), fontScale = 2.0f)

        rule.onNodeWithText(text(R.string.home_browse)).assertExists()
        rule.assertNoTruncatedText()
    }

    @Test
    fun theBorrowerHomeRendersInTheLightThemeAndLeavesAPicture() {
        show(borrower(lowFee = true, greeting = "Ana"), darkTheme = false)

        rule.onNodeWithContentDescription(spokenBalance).assertIsDisplayed()
        saveScreenshot("home-borrower-light.png")
    }

    @Test
    fun theBorrowerHomeRendersInTheDarkThemeAndLeavesAPicture() {
        show(borrower(lowFee = true, greeting = "Ana"), darkTheme = true)

        rule.onNodeWithContentDescription(spokenBalance).assertIsDisplayed()
        saveScreenshot("home-borrower-dark.png")
    }

    @Test
    fun theLenderHomeRendersInTheDarkTheme() {
        show(lender(), darkTheme = true)

        rule.onNodeWithContentDescription("${text(R.string.home_open_requests)}, 6").assertIsDisplayed()
    }

    /** Asserts that no node shows [text]. The count form keeps the intent readable in each test. */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String, expected: Int) {
        assertEquals("nodes with text \"$text\"", expected, onAllNodes(hasText(text)).fetchSemanticsNodes().size)
    }

    /**
     * Writes the rendered screen as a PNG to the additional test output folder, which the workflow
     * uploads (UI-SPEC Verification Plan, screenshot row for S06). The runner passes the folder as
     * `additionalTestOutputDir`. A device that cannot write there keeps the test green but the
     * picture is then missing, and the bitmap itself is still checked to hold pixels.
     */
    private fun saveScreenshot(name: String) {
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("the screenshot is empty", bitmap.width > 0 && bitmap.height > 0)
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: context.getExternalFilesDir(null)?.path
            ?: return
        runCatching {
            File(dir).mkdirs()
            FileOutputStream(File(dir, name)).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
