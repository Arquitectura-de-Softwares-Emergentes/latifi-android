package com.latifi.wallet.ui.root

import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.ui.nav.Routes

/**
 * Decides where a cold start lands. Pure: the same three inputs always give the same route.
 *
 * - a session without a role opens role selection
 * - a session with a role opens the tabs (an expired token still counts, the first 401 asks
 *   for a new signature lazily)
 * - no session and onboarding seen opens Connect, titled "Reconnect your wallet"
 * - anything else opens onboarding
 */
object StartDestinationResolver {
    fun resolve(hasSession: Boolean, role: AppRole?, onboardingSeen: Boolean): String = when {
        hasSession && role == null -> Routes.ROLE
        hasSession -> Routes.MAIN
        onboardingSeen -> Routes.CONNECT
        else -> Routes.ONBOARDING
    }
}
