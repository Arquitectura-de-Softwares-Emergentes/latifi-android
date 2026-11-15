package com.latifi.wallet.ui.root

import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.ui.nav.Routes
import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationResolverTest {
    @Test
    fun noSessionAndOnboardingNotSeenOpensOnboarding() {
        assertEquals(Routes.ONBOARDING, StartDestinationResolver.resolve(hasSession = false, role = null, onboardingSeen = false))
    }

    @Test
    fun noSessionAndOnboardingSeenOpensConnect() {
        assertEquals(Routes.CONNECT, StartDestinationResolver.resolve(hasSession = false, role = null, onboardingSeen = true))
    }

    @Test
    fun aSessionWithoutARoleOpensRoleSelection() {
        assertEquals(Routes.ROLE, StartDestinationResolver.resolve(hasSession = true, role = null, onboardingSeen = true))
    }

    @Test
    fun aSessionWithARoleOpensTheTabs() {
        assertEquals(Routes.MAIN, StartDestinationResolver.resolve(hasSession = true, role = AppRole.Lender, onboardingSeen = true))
    }

    @Test
    fun aSessionWithAnExpiredTokenStillOpensTheTabs() {
        // The resolver only sees that a session exists. The first 401 asks for a new signature.
        assertEquals(Routes.MAIN, StartDestinationResolver.resolve(hasSession = true, role = AppRole.Borrower, onboardingSeen = false))
    }

    @Test
    fun aStoredRoleWithoutASessionStillStartsAtOnboardingOrConnect() {
        assertEquals(Routes.CONNECT, StartDestinationResolver.resolve(hasSession = false, role = AppRole.Borrower, onboardingSeen = true))
        assertEquals(Routes.ONBOARDING, StartDestinationResolver.resolve(hasSession = false, role = AppRole.Borrower, onboardingSeen = false))
    }
}
