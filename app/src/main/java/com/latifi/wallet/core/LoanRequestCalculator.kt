package com.latifi.wallet.core

import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.LoanBookAbi
import java.math.BigInteger

/** The four inputs of the request form. */
enum class RequestField { AMOUNT, RATE, TERM, INSTALLMENTS }

/** Why one field of the request form is not valid. */
enum class FieldError { EMPTY, MALFORMED, OUT_OF_RANGE }

/** What the person sees before publishing, all in base units. */
data class LoanPreview(
    val principal: BigInteger,
    val bps: Int,
    val termDays: Int,
    val installments: Int,
    val interest: BigInteger,
    val totalDue: BigInteger,
    val installmentAmounts: List<BigInteger>,
)

/** Result of checking the request form. */
sealed interface LoanRequestResult {
    /** A valid input: the preview and the `requestLoan` calldata that matches it. */
    data class Valid(val preview: LoanPreview, val calldata: String) : LoanRequestResult

    /** One entry per field that failed. */
    data class Invalid(val errors: Map<RequestField, FieldError>) : LoanRequestResult
}

/**
 * Turns the typed request into a validated preview and the calldata to sign. The limits are the
 * ones of the UI-SPEC (1 to 10,000 tUSD, 0.01 to 50.00 percent a year, 1 to 365 days, 1 to 6
 * installments), which sit inside what the contract accepts.
 */
object LoanRequestCalculator {
    val MIN_AMOUNT: BigInteger = TokenAmount.ONE_TOKEN
    val MAX_AMOUNT: BigInteger = TokenAmount.ONE_TOKEN.multiply(BigInteger.valueOf(10_000L))
    const val MIN_BPS = 1
    const val MAX_BPS = Deployment.MAX_RATE_BPS

    fun calculate(
        amountText: String,
        rateText: String,
        termDays: Int,
        installments: Int,
    ): LoanRequestResult {
        val errors = linkedMapOf<RequestField, FieldError>()

        var principal: BigInteger? = null
        when (val amount = TokenAmount.parse(amountText)) {
            is AmountParse.Invalid -> errors[RequestField.AMOUNT] = amount.reason.toFieldError()
            is AmountParse.Valid -> {
                val units = amount.amount.baseUnits
                if (units < MIN_AMOUNT || units > MAX_AMOUNT) {
                    errors[RequestField.AMOUNT] = FieldError.OUT_OF_RANGE
                } else {
                    principal = units
                }
            }
        }

        var bps: Int? = null
        when (val rate = LoanMath.parseRate(rateText)) {
            is RateParse.Invalid -> errors[RequestField.RATE] = rate.reason.toFieldError()
            is RateParse.Valid -> {
                val value = rate.bps
                if (value < BigInteger.valueOf(MIN_BPS.toLong()) || value > BigInteger.valueOf(MAX_BPS.toLong())) {
                    errors[RequestField.RATE] = FieldError.OUT_OF_RANGE
                } else {
                    bps = value.toInt()
                }
            }
        }

        if (termDays !in 1..Deployment.MAX_TERM_DAYS) errors[RequestField.TERM] = FieldError.OUT_OF_RANGE
        if (installments !in 1..Deployment.MAX_INSTALLMENTS) {
            errors[RequestField.INSTALLMENTS] = FieldError.OUT_OF_RANGE
        }

        if (errors.isNotEmpty() || principal == null || bps == null) return LoanRequestResult.Invalid(errors)

        val interest = LoanMath.interest(principal, bps, termDays)
        val totalDue = principal.add(interest)
        val preview = LoanPreview(
            principal = principal,
            bps = bps,
            termDays = termDays,
            installments = installments,
            interest = interest,
            totalDue = totalDue,
            installmentAmounts = LoanMath.installmentAmounts(totalDue, installments),
        )
        return LoanRequestResult.Valid(
            preview = preview,
            calldata = LoanBookAbi.requestLoan(principal, bps, termDays, installments),
        )
    }

    private fun ParseFailure.toFieldError(): FieldError = when (this) {
        ParseFailure.EMPTY -> FieldError.EMPTY
        ParseFailure.MALFORMED, ParseFailure.TOO_MANY_DECIMALS -> FieldError.MALFORMED
    }
}
