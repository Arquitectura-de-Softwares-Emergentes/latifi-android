package com.latifi.wallet.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.AppRole

/** The six tabs of the app. Each role shows four of them, see [tabsFor]. */
enum class TabDestination(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val route: String,
) {
    Home(R.string.nav_home, R.drawable.ic_home, Routes.HOME),
    Loans(R.string.nav_loans, R.drawable.ic_payments, Routes.LOANS),
    Reputation(R.string.nav_reputation, R.drawable.ic_workspace_premium, Routes.REPUTATION),
    Invest(R.string.nav_invest, R.drawable.ic_trending_up, Routes.INVEST),
    Portfolio(R.string.nav_portfolio, R.drawable.ic_account_balance_wallet, Routes.PORTFOLIO),
    Profile(R.string.nav_profile, R.drawable.ic_person, Routes.PROFILE),
    ;

    companion object {
        fun fromRoute(route: String?): TabDestination? = entries.firstOrNull { it.route == route }
    }
}

/** Borrower: Home, Loans, Reputation, Profile. Lender: Home, Invest, Portfolio, Profile. */
fun tabsFor(role: AppRole): List<TabDestination> = when (role) {
    AppRole.Borrower -> listOf(
        TabDestination.Home,
        TabDestination.Loans,
        TabDestination.Reputation,
        TabDestination.Profile,
    )
    AppRole.Lender -> listOf(
        TabDestination.Home,
        TabDestination.Invest,
        TabDestination.Portfolio,
        TabDestination.Profile,
    )
}

/** From this font scale up, the bar and rail give way to a drawer so labels never truncate. */
const val LARGE_TEXT_SCALE = 1.3f

fun isLargeTextMode(fontScale: Float): Boolean = fontScale >= LARGE_TEXT_SCALE
