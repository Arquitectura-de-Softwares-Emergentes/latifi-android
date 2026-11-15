package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.LoanPageDto
import com.latifi.wallet.data.api.dto.TokenResponseDto
import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.domain.error.AppError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * WR-12: a number from the server that the loan arithmetic could not take is rejected in the mapper.
 * It ends as a [SerializationException], which the call helper turns into [AppError.Server], and
 * never as a `require` that crashes the screen.
 */
class ServerValueRangeTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun loanPage(change: (String) -> String): LoanPageDto =
        json.decodeFromString(LoanPageDto.serializer(), change(ApiTestRig.fixture("loan-page.json")))

    private fun token(expiresIn: String) = json.decodeFromString(
        TokenResponseDto.serializer(),
        """{"accessToken":"a.b.c","tokenType":"Bearer","expiresIn":$expiresIn,"address":"0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD"}""",
    )

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("the value should have been rejected")
        } catch (expected: SerializationException) {
            // This exception ends as AppError.Server in apiResult.
            assertEquals(AppError.Server, ApiErrorMapper.map(expected))
        }
    }

    @Test
    fun theUntouchedFixtureStillMaps() {
        assertTrue(loanPage { it }.toDomain().items.isNotEmpty())
    }

    @Test
    fun zeroInstallmentsIsRejected() {
        assertRejected { loanPage { it.replaceFirst(Regex("\"installments\":\\s*\\d+"), "\"installments\": 0") }.toDomain() }
    }

    @Test
    fun moreInstallmentsThanTheContractAllowsIsRejected() {
        assertRejected { loanPage { it.replaceFirst(Regex("\"installments\":\\s*\\d+"), "\"installments\": 7") }.toDomain() }
    }

    @Test
    fun aNegativeRateIsRejected() {
        assertRejected { loanPage { it.replaceFirst(Regex("\"interestRateBps\":\\s*\\d+"), "\"interestRateBps\": -1") }.toDomain() }
    }

    @Test
    fun aRateAboveTheContractMaximumIsRejected() {
        assertRejected { loanPage { it.replaceFirst(Regex("\"interestRateBps\":\\s*\\d+"), "\"interestRateBps\": 5001") }.toDomain() }
    }

    @Test
    fun aTermOfZeroOrBeyondAYearIsRejected() {
        assertRejected { loanPage { it.replaceFirst(Regex("\"termDays\":\\s*\\d+"), "\"termDays\": 0") }.toDomain() }
        assertRejected { loanPage { it.replaceFirst(Regex("\"termDays\":\\s*\\d+"), "\"termDays\": 366") }.toDomain() }
    }

    @Test
    fun moreInstallmentsPaidThanTheLoanHasIsRejected() {
        assertRejected { loanPage { it.replaceFirst(Regex("\"installmentsPaid\":\\s*\\d+"), "\"installmentsPaid\": 6") }.toDomain() }
    }

    @Test
    fun anAbsurdOrNonPositiveTokenLifetimeIsRejected() {
        assertRejected { token("0").toDomain() }
        assertRejected { token("-5").toDomain() }
        assertRejected { token("86401").toDomain() }
        assertRejected { token("9223372036854775807").toDomain() }
    }

    @Test
    fun theNormalTokenLifetimeStillMaps() {
        assertEquals(900L, token("900").toDomain().expiresInSeconds)
    }
}
