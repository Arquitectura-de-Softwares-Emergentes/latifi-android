package com.latifi.wallet.domain.profile

import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ProfileGateTest {
    private val address = "0xAbC0000000000000000000000000000000000001"
    private val at = Instant.parse("2026-11-01T00:00:00Z")

    private fun profile(contact: Contact?) = Profile(address, "Ana Torres", contact, at, at)

    @Test
    fun aMissingProfileIsMissing() = runBlocking {
        val gate = ProfileGate(FakeProfileRepository())
        assertEquals(GateResult.Missing, gate.check(address).getOrThrow())
    }

    @Test
    fun aProfileWithNoContactIsMissing() = runBlocking {
        val repo = FakeProfileRepository().apply { seed(profile(contact = null)) }
        assertEquals(GateResult.Missing, ProfileGate(repo).check(address).getOrThrow())
    }

    @Test
    fun aProfileWithABlankContactIsMissing() = runBlocking {
        val repo = FakeProfileRepository().apply { seed(profile(Contact(ContactType.EMAIL, "   "))) }
        assertEquals(GateResult.Missing, ProfileGate(repo).check(address).getOrThrow())
    }

    @Test
    fun aCompleteProfileIsComplete() = runBlocking {
        val repo = FakeProfileRepository().apply { seed(profile(Contact(ContactType.EMAIL, "ana@example.com"))) }
        assertEquals(GateResult.Complete, ProfileGate(repo).check(address).getOrThrow())
    }

    @Test
    fun aNetworkErrorIsPropagatedAndNeverLooksLikeAPass() = runBlocking {
        val repo = FakeProfileRepository().apply {
            seed(profile(Contact(ContactType.EMAIL, "ana@example.com")))
            failNext = AppError.Offline
        }
        val result = ProfileGate(repo).check(address)
        assertTrue(result.isFailure)
        assertEquals(AppError.Offline, (result.exceptionOrNull() as AppException).error)
    }

    @Test
    fun aServerErrorIsPropagatedToo() = runBlocking {
        val repo = FakeProfileRepository().apply { failNext = AppError.Server }
        val result = ProfileGate(repo).check(address)
        assertEquals(AppError.Server, (result.exceptionOrNull() as AppException).error)
    }

    @Test
    fun theGateLooksTheAddressUpWithoutCase() = runBlocking {
        val repo = FakeProfileRepository().apply { seed(profile(Contact(ContactType.PHONE, "+51987654321"))) }
        assertEquals(GateResult.Complete, ProfileGate(repo).check(address.lowercase()).getOrThrow())
    }
}
