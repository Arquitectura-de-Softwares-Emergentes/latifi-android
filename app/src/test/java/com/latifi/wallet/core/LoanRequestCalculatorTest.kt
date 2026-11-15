package com.latifi.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class LoanRequestCalculatorTest {
    private fun big(value: Long): BigInteger = BigInteger.valueOf(value)

    private fun valid(amount: String, rate: String, days: Int, installments: Int): LoanRequestResult.Valid {
        val result = LoanRequestCalculator.calculate(amount, rate, days, installments)
        assertTrue("expected a valid request, got $result", result is LoanRequestResult.Valid)
        return result as LoanRequestResult.Valid
    }

    private fun errors(amount: String, rate: String, days: Int, installments: Int): Map<RequestField, FieldError> {
        val result = LoanRequestCalculator.calculate(amount, rate, days, installments)
        assertTrue("expected an invalid request, got $result", result is LoanRequestResult.Invalid)
        return (result as LoanRequestResult.Invalid).errors
    }

    @Test
    fun tracerInputBecomesTheContractPreviewAndCalldata() {
        val result = valid("100", "10.00", 30, 2)

        val preview = result.preview
        assertEquals(big(100_000_000L), preview.principal)
        assertEquals(1000, preview.bps)
        assertEquals(30, preview.termDays)
        assertEquals(2, preview.installments)
        assertEquals(big(821_917L), preview.interest)
        assertEquals(big(100_821_917L), preview.totalDue)
        assertEquals(listOf(big(50_410_958L), big(50_410_959L)), preview.installmentAmounts)

        assertTrue(result.calldata, result.calldata.startsWith("0x430a09d0"))
        assertEquals(
            "0x430a09d0" +
                "0000000000000000000000000000000000000000000000000000000005f5e100" +
                "00000000000000000000000000000000000000000000000000000000000003e8" +
                "000000000000000000000000000000000000000000000000000000000000001e" +
                "0000000000000000000000000000000000000000000000000000000000000002",
            result.calldata,
        )
    }

    @Test
    fun theLowerBoundariesAreValid() {
        val result = valid("1", "0.01", 1, 1)
        assertEquals(big(1_000_000L), result.preview.principal)
        assertEquals(1, result.preview.bps)
        assertEquals(1, result.preview.termDays)
        assertEquals(1, result.preview.installments)
    }

    @Test
    fun theUpperBoundariesAreValid() {
        val result = valid("10000", "50.00", 365, 6)
        assertEquals(big(10_000_000_000L), result.preview.principal)
        assertEquals(5000, result.preview.bps)
        assertEquals(big(5_000_000_000L), result.preview.interest)
        assertEquals(6, result.preview.installmentAmounts.size)
    }

    @Test
    fun anAmountOutsideOneToTenThousandIsOutOfRange() {
        assertEquals(mapOf(RequestField.AMOUNT to FieldError.OUT_OF_RANGE), errors("0.999999", "10", 30, 2))
        assertEquals(mapOf(RequestField.AMOUNT to FieldError.OUT_OF_RANGE), errors("10000.000001", "10", 30, 2))
        assertEquals(mapOf(RequestField.AMOUNT to FieldError.OUT_OF_RANGE), errors("0", "10", 30, 2))
    }

    @Test
    fun aRateOutsideZeroPointZeroOneToFiftyIsOutOfRange() {
        assertEquals(mapOf(RequestField.RATE to FieldError.OUT_OF_RANGE), errors("100", "0", 30, 2))
        assertEquals(mapOf(RequestField.RATE to FieldError.OUT_OF_RANGE), errors("100", "50.01", 30, 2))
    }

    @Test
    fun aTermOutsideOneToThreeHundredSixtyFiveIsOutOfRange() {
        assertEquals(mapOf(RequestField.TERM to FieldError.OUT_OF_RANGE), errors("100", "10", 0, 2))
        assertEquals(mapOf(RequestField.TERM to FieldError.OUT_OF_RANGE), errors("100", "10", 366, 2))
    }

    @Test
    fun installmentsOutsideOneToSixAreOutOfRange() {
        assertEquals(mapOf(RequestField.INSTALLMENTS to FieldError.OUT_OF_RANGE), errors("100", "10", 30, 0))
        assertEquals(mapOf(RequestField.INSTALLMENTS to FieldError.OUT_OF_RANGE), errors("100", "10", 30, 7))
    }

    @Test
    fun emptyAndMalformedTextGetTheirOwnReasons() {
        assertEquals(
            mapOf(RequestField.AMOUNT to FieldError.EMPTY, RequestField.RATE to FieldError.EMPTY),
            errors("", "", 30, 2),
        )
        assertEquals(
            mapOf(RequestField.AMOUNT to FieldError.MALFORMED, RequestField.RATE to FieldError.MALFORMED),
            errors("abc", "1.2.3", 30, 2),
        )
        // More decimals than the amount or the rate allows is a malformed entry for the form.
        assertEquals(mapOf(RequestField.AMOUNT to FieldError.MALFORMED), errors("1.0000001", "10", 30, 2))
        assertEquals(mapOf(RequestField.RATE to FieldError.MALFORMED), errors("100", "10.001", 30, 2))
    }

    @Test
    fun everyFieldFailsAtOnceWithOneEntryEach() {
        val all = errors("", "", 0, 9)
        assertEquals(
            mapOf(
                RequestField.AMOUNT to FieldError.EMPTY,
                RequestField.RATE to FieldError.EMPTY,
                RequestField.TERM to FieldError.OUT_OF_RANGE,
                RequestField.INSTALLMENTS to FieldError.OUT_OF_RANGE,
            ),
            all,
        )
    }

    @Test
    fun theUiLimitsStayInsideWhatTheContractAccepts() {
        assertEquals(5000, LoanRequestCalculator.MAX_BPS)
        assertTrue(LoanRequestCalculator.MAX_AMOUNT < BigInteger.TWO.pow(127))
    }
}
