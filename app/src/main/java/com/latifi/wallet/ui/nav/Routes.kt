package com.latifi.wallet.ui.nav

import java.math.BigInteger
import java.net.URLEncoder

/** Argument names shared by the route patterns and the screens that read them. */
object RouteArgs {
    const val LOAN_ID = "loanId"
    const val RETURN_TO = "returnTo"
}

/**
 * Every route of the 20 screens, declared once. A constant is the route pattern. Routes that
 * carry an argument have a builder function below the constants.
 *
 * A loan id is an unsigned 256 bit number, so it travels as a decimal string and never as a
 * Long. RoutesRegistryTest fails when a name below is neither registered with `composable(`
 * nor listed as pending, so each later plan must register the screens it builds.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val HOW_IT_WORKS = "how_it_works"
    const val CONNECT = "connect"
    const val ROLE = "role"
    const val PROFILE_FORM = "profile_form?returnTo={returnTo}"

    /** The tab container. Its tabs are nested destinations. */
    const val MAIN = "main"
    const val HOME = "home"
    const val LOANS = "loans"
    const val REPUTATION = "reputation"
    const val INVEST = "invest"
    const val PORTFOLIO = "portfolio"
    const val PROFILE = "profile"

    const val LOAN_DETAIL = "loan/{loanId}"
    const val REQUEST_DETAIL = "request/{loanId}"
    const val INVESTMENT_DETAIL = "investment/{loanId}"
    const val REQUEST_LOAN = "request_loan"
    const val PAY = "pay/{loanId}"
    const val FUND = "fund/{loanId}"

    /** Every pattern above, in declaration order. */
    val ALL: List<String> = listOf(
        ONBOARDING, HOW_IT_WORKS, CONNECT, ROLE, PROFILE_FORM,
        MAIN, HOME, LOANS, REPUTATION, INVEST, PORTFOLIO, PROFILE,
        LOAN_DETAIL, REQUEST_DETAIL, INVESTMENT_DETAIL, REQUEST_LOAN, PAY, FUND,
    )

    /** [returnTo] is the route to open after saving. Null means the form closes on its own. */
    fun profileForm(returnTo: String? = null): String =
        if (returnTo == null) "profile_form" else "profile_form?returnTo=${URLEncoder.encode(returnTo, "UTF-8")}"

    fun loanDetail(loanId: BigInteger): String = "loan/$loanId"

    fun requestDetail(loanId: BigInteger): String = "request/$loanId"

    fun investmentDetail(loanId: BigInteger): String = "investment/$loanId"

    fun pay(loanId: BigInteger): String = "pay/$loanId"

    fun fund(loanId: BigInteger): String = "fund/$loanId"
}

/**
 * Sheets that must appear above any screen. They are not destinations: a repository can
 * request one through OverlayController while a screen is open.
 */
enum class Overlays { SIGN_IN, TX_STATUS, COINS, WALLET_HELP }
