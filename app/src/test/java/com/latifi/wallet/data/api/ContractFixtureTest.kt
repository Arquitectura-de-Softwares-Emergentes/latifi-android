package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.ApiErrorDto
import com.latifi.wallet.data.api.dto.BorrowerReputationDto
import com.latifi.wallet.data.api.dto.ChallengeRequestDto
import com.latifi.wallet.data.api.dto.ChallengeResponseDto
import com.latifi.wallet.data.api.dto.ContactDto
import com.latifi.wallet.data.api.dto.ConvertResponseDto
import com.latifi.wallet.data.api.dto.LoanPageDto
import com.latifi.wallet.data.api.dto.LoanSummaryDto
import com.latifi.wallet.data.api.dto.ProfileResponseDto
import com.latifi.wallet.data.api.dto.ProfileUpdateRequestDto
import com.latifi.wallet.data.api.dto.RateQuoteDto
import com.latifi.wallet.data.api.dto.ReputationEventDto
import com.latifi.wallet.data.api.dto.ReputationHistoryPageDto
import com.latifi.wallet.data.api.dto.ReputationResponseDto
import com.latifi.wallet.data.api.dto.TokenResponseDto
import com.latifi.wallet.data.api.dto.VerifyRequestDto
import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.data.api.dto.toSnapshotOrNull
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationLevel
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant

/**
 * Two guards. First, every DTO has the property names of its schema and is optional exactly when
 * the schema does not require the property. Second, the fixtures, written from the contract
 * examples, parse into the DTOs and map to domain values.
 */
class ContractFixtureTest {
    private val json = ApiClientFactory.json

    private fun fixture(name: String) = json.parseToJsonElement(ApiTestRig.fixture(name)).jsonObject

    @Test
    fun everyDtoMatchesTheSchemaOfTheContract() {
        val pairs: List<Pair<String, KSerializer<*>>> = listOf(
            "ChallengeRequest" to ChallengeRequestDto.serializer(),
            "ChallengeResponse" to ChallengeResponseDto.serializer(),
            "VerifyRequest" to VerifyRequestDto.serializer(),
            "TokenResponse" to TokenResponseDto.serializer(),
            "Contact" to ContactDto.serializer(),
            "ProfileUpdateRequest" to ProfileUpdateRequestDto.serializer(),
            "ProfileResponse" to ProfileResponseDto.serializer(),
            "ReputationResponse" to ReputationResponseDto.serializer(),
            "ReputationEvent" to ReputationEventDto.serializer(),
            "ReputationHistoryPage" to ReputationHistoryPageDto.serializer(),
            "BorrowerReputation" to BorrowerReputationDto.serializer(),
            "LoanSummary" to LoanSummaryDto.serializer(),
            "LoanPage" to LoanPageDto.serializer(),
            "ConvertResponse" to ConvertResponseDto.serializer(),
            "RateQuote" to RateQuoteDto.serializer(),
            "ApiError" to ApiErrorDto.serializer(),
        )
        for ((schemaName, serializer) in pairs) {
            val schema = ContractSpec.schemas[schemaName] ?: error("Schema $schemaName is not in the contract copy")
            val descriptor = serializer.descriptor
            val names = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
            assertEquals("Properties of $schemaName", schema.properties.toSet(), names.toSet())
            // The error body is read leniently on purpose, so a malformed one never crashes the mapper.
            if (schemaName == "ApiError") continue
            val dtoRequired = names.filterIndexed { index, _ -> !descriptor.isElementOptional(index) }.toSet()
            assertEquals("Required properties of $schemaName", schema.required, dtoRequired)
        }
    }

    @Test
    fun theLoanPageFixtureParsesAndMaps() {
        val page = json.decodeFromString(LoanPageDto.serializer(), ApiTestRig.fixture("loan-page.json")).toDomain()
        assertEquals(3, page.items.size)
        assertEquals(42L, page.totalItems)
    }

    @Test
    fun theReputationSummaryFixtureMapsToTheDomain() {
        val dto = json.decodeFromJsonElement(ReputationResponseDto.serializer(), fixture("reputation.json").getValue("summary"))

        val summary = dto.toDomain()

        assertEquals(620, summary.score)
        assertEquals(ReputationLevel.TRUSTED, summary.level)
        assertEquals(3, summary.onTimeCount)
        assertEquals(1, summary.lateCount)
        assertTrue(summary.profileComplete)
        assertEquals(Instant.parse("2026-01-01T12:00:00Z"), summary.updatedAt)
    }

    @Test
    fun theHistoryFixtureKeepsNullTransactionReferences() {
        val dto = json.decodeFromJsonElement(ReputationHistoryPageDto.serializer(), fixture("reputation.json").getValue("history"))

        val page = dto.toDomain()

        assertEquals(3, page.items.size)
        val late = page.items[0]
        assertEquals(ReputationEventType.REPAID_LATE, late.type)
        assertEquals(BigInteger.valueOf(7), late.loanId)
        assertEquals(4, late.daysLate)
        assertEquals(3, late.logIndex)
        assertEquals("0x" + "2".repeat(64), late.txHash)
        val onTime = page.items[1]
        assertNull(onTime.daysLate)
        val profile = page.items[2]
        assertEquals(ReputationEventType.PROFILE_COMPLETED, profile.type)
        assertNull(profile.loanId)
        assertNull(profile.txHash)
        assertNull(profile.logIndex)
    }

    @Test
    fun anUnknownEventTypeIsSkipped() {
        val text = ApiTestRig.fixture("reputation.json")
        val dto = json.decodeFromJsonElement(ReputationHistoryPageDto.serializer(), json.parseToJsonElement(text.replace("REPAID_ON_TIME", "BONUS")).jsonObject.getValue("history"))

        val page = dto.toDomain()

        assertEquals(listOf(ReputationEventType.REPAID_LATE, ReputationEventType.PROFILE_COMPLETED), page.items.map { it.type })
    }

    @Test
    fun theRateFixturesCarryTheStaleFlagAndTheOldestQuoteTime() {
        val fresh = json.decodeFromJsonElement(ListSerializer(RateQuoteDto.serializer()), fixture("rates.json").getValue("fresh")).toSnapshotOrNull()!!
        val stale = json.decodeFromJsonElement(ListSerializer(RateQuoteDto.serializer()), fixture("rates.json").getValue("stale")).toSnapshotOrNull()!!

        assertFalse(fresh.stale)
        assertEquals(BigDecimal("3.7512"), fresh.quotes[0].rate)
        assertEquals(BigDecimal("950.5"), fresh.quotes[1].rate)
        assertTrue(stale.stale)
        assertEquals(Instant.parse("2026-01-01T09:00:00Z"), stale.asOf)
    }

    @Test
    fun anEmptyRateListHasNoSnapshot() {
        assertNull(emptyList<RateQuoteDto>().toSnapshotOrNull())
    }

    @Test
    fun theConvertFixtureMapsWithExactDecimals() {
        val converted = json.decodeFromJsonElement(ConvertResponseDto.serializer(), fixture("rates.json").getValue("convert")).toDomain()

        assertEquals(BigDecimal("937.80"), converted.value)
        assertEquals(BigDecimal("3.7512"), converted.rate)
        assertFalse(converted.stale)
    }

    @Test
    fun aProfileParsesWithAndWithoutContact() {
        val own = """{"address":"0xAbC1111111111111111111111111111111111111","displayName":"Sample User","contact":{"type":"EMAIL","value":"user@example.org"},"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-02T12:00:00Z"}"""
        val other = own.replace(""","contact":{"type":"EMAIL","value":"user@example.org"}""", "")

        val withContact = json.decodeFromString(ProfileResponseDto.serializer(), own).toDomain()
        val withoutContact = json.decodeFromString(ProfileResponseDto.serializer(), other).toDomain()

        assertEquals("0xabc1111111111111111111111111111111111111", withContact.address)
        assertEquals(ContactType.EMAIL, withContact.contact?.type)
        assertEquals("user@example.org", withContact.contact?.value)
        assertNull(withoutContact.contact)
        assertEquals("Sample User", withoutContact.displayName)
    }

    @Test
    fun aTokenResponseMapsToTheDomainToken() {
        val text = """{"accessToken":"a.b.c","tokenType":"Bearer","expiresIn":900,"address":"0xABCDEFabcdefABCDEFabcdefABCDEFabcdefABCD"}"""

        val token = json.decodeFromString(TokenResponseDto.serializer(), text).toDomain()

        assertEquals("a.b.c", token.accessToken)
        assertEquals(900L, token.expiresInSeconds)
        assertEquals("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd", token.address)
    }

    @Test
    fun aChallengeKeepsItsMessageByteForByte() {
        val message = "latifi.example wants you to sign in\\n0x5290\\n\\nNonce: 9c1d\\nIssued At: 2026-01-01T12:00:00Z"
        val text = """{"challengeId":"3f2b8c1e-5d4a-4c7b-9a10-2e6f7d8c9b01","nonce":"9c1d","message":"$message","issuedAt":"2026-01-01T12:00:00Z","expiresAt":"2026-01-01T12:05:00Z"}"""

        val challenge = json.decodeFromString(ChallengeResponseDto.serializer(), text).toDomain()

        assertEquals("latifi.example wants you to sign in\n0x5290\n\nNonce: 9c1d\nIssued At: 2026-01-01T12:00:00Z", challenge.message)
        assertEquals(Instant.parse("2026-01-01T12:05:00Z"), challenge.expiresAt)
    }

    @Test
    fun aMalformedUint256IsRejectedInsteadOfRoundedOrGuessed() {
        val text = ApiTestRig.fixture("loan-page.json").replaceFirst("\"principal\": \"250000000\"", "\"principal\": \"2.5e8\"")
        val dto = json.decodeFromString(LoanPageDto.serializer(), text)

        try {
            dto.toDomain()
            fail("A principal that is not a digit string must not map")
        } catch (e: SerializationException) {
            // expected: the call helper turns this into AppError.Server
        }
    }
}
