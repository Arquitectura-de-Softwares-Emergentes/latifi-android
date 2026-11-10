package com.latifi.wallet.ui.nav

import com.latifi.wallet.domain.model.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationModelTest {
    @Test
    fun borrowerSeesHomeLoansReputationProfileInThatOrder() {
        assertEquals(
            listOf(TabDestination.Home, TabDestination.Loans, TabDestination.Reputation, TabDestination.Profile),
            tabsFor(AppRole.Borrower),
        )
    }

    @Test
    fun lenderSeesHomeInvestPortfolioProfileInThatOrder() {
        assertEquals(
            listOf(TabDestination.Home, TabDestination.Invest, TabDestination.Portfolio, TabDestination.Profile),
            tabsFor(AppRole.Lender),
        )
    }

    @Test
    fun everyTabHasItsOwnRouteLabelAndIcon() {
        val tabs = TabDestination.entries
        assertEquals(6, tabs.size)
        assertEquals(tabs.size, tabs.map { it.route }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.label }.toSet().size)
        assertEquals(tabs.size, tabs.map { it.icon }.toSet().size)
    }

    @Test
    fun everyTabRouteIsAMemberOfTheRouteMap() {
        for (tab in TabDestination.entries) {
            assertTrue("${tab.route} is not in Routes.ALL", tab.route in Routes.ALL)
        }
    }

    @Test
    fun routeLookupFindsEachTabAndRejectsOthers() {
        for (tab in TabDestination.entries) assertEquals(tab, TabDestination.fromRoute(tab.route))
        assertNull(TabDestination.fromRoute(Routes.MAIN))
        assertNull(TabDestination.fromRoute(null))
    }

    @Test
    fun bothRolesShareHomeAndProfileAndKeepTheirOwnMiddleTabs() {
        val borrower = tabsFor(AppRole.Borrower)
        val lender = tabsFor(AppRole.Lender)
        assertEquals(borrower.first(), lender.first())
        assertEquals(borrower.last(), lender.last())
        assertEquals(emptySet<TabDestination>(), borrower.drop(1).dropLast(1).toSet() intersect lender.toSet())
    }

    @Test
    fun routeBuildersFillTheLoanIdPlaceholder() {
        val id = java.math.BigInteger("115792089237316195423570985008687907853269984665640564039457584007913129639935")
        assertEquals("loan/$id", Routes.loanDetail(id))
        assertEquals("request/$id", Routes.requestDetail(id))
        assertEquals("investment/$id", Routes.investmentDetail(id))
        assertEquals("pay/$id", Routes.pay(id))
        assertEquals("fund/$id", Routes.fund(id))
        assertEquals("loan/{loanId}".replace("{loanId}", id.toString()), Routes.loanDetail(id))
    }

    @Test
    fun profileFormRouteEncodesItsReturnTarget() {
        assertEquals("profile_form", Routes.profileForm())
        val route = Routes.profileForm("loan/7")
        assertEquals("profile_form?returnTo=loan%2F7", route)
        assertNotNull(Routes.PROFILE_FORM)
    }

    @Test
    fun theRouteMapListsEveryPatternOnceAndOverlaysAreFour() {
        assertEquals(Routes.ALL.size, Routes.ALL.toSet().size)
        assertEquals(18, Routes.ALL.size)
        assertEquals(listOf("SIGN_IN", "TX_STATUS", "COINS", "WALLET_HELP"), Overlays.entries.map { it.name })
    }
}
