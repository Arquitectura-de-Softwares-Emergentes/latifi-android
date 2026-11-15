package com.latifi.wallet.domain.feed

import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.profile.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BorrowerNameResolverTest {
    private val lucia = "0x1111111111111111111111111111111111111111"
    private val mateo = "0x2222222222222222222222222222222222222222"

    /** Counts the calls and can hold an answer back or fail. */
    private class CountingProfiles(
        private val names: Map<String, String?>,
        private val gate: CompletableDeferred<Unit>? = null,
        var failing: Boolean = false,
    ) : ProfileRepository {
        val calls = mutableListOf<String>()

        override suspend fun get(address: String): Result<Profile> = error("not used by the feed")

        override suspend fun upsert(address: String, displayName: String, contact: Contact): Result<Profile> =
            error("not used by the feed")

        override suspend fun publicName(address: String): String? {
            calls += address
            gate?.await()
            if (failing) throw IllegalStateException("offline")
            return names[address]
        }
    }

    private fun TestScope.resolver(profiles: ProfileRepository) = BorrowerNameResolver(this, profiles)

    @Test
    fun anAddressIsAskedOnceAndItsNameAppears() = runTest {
        val profiles = CountingProfiles(mapOf(lucia to "Lucia Fernandez"))
        val resolver = resolver(profiles)

        resolver.resolve(listOf(lucia))
        advanceUntilIdle()
        resolver.resolve(listOf(lucia))
        advanceUntilIdle()

        assertEquals(listOf(lucia), profiles.calls)
        assertEquals(mapOf(lucia to "Lucia Fernandez"), resolver.names.value)
    }

    @Test
    fun addressesCompareWithoutCase() = runTest {
        val profiles = CountingProfiles(mapOf("0xabcdef0000000000000000000000000000000001" to "Ana Diaz"))
        val resolver = resolver(profiles)

        resolver.resolve(listOf("0xABCDEF0000000000000000000000000000000001"))
        resolver.resolve(listOf("0xabcdef0000000000000000000000000000000001"))
        advanceUntilIdle()

        assertEquals(1, profiles.calls.size)
        assertEquals("Ana Diaz", resolver.names.value["0xabcdef0000000000000000000000000000000001"])
    }

    @Test
    fun concurrentRequestsForTheSameAddressShareOneCall() = runTest {
        val gate = CompletableDeferred<Unit>()
        val profiles = CountingProfiles(mapOf(lucia to "Lucia Fernandez", mateo to "Mateo Quispe"), gate)
        val resolver = resolver(profiles)

        resolver.resolve(listOf(lucia, mateo))
        resolver.resolve(listOf(lucia))
        resolver.resolve(listOf(mateo, lucia))
        runCurrent()
        assertEquals(listOf(lucia, mateo), profiles.calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, resolver.names.value.size)
        assertEquals(2, profiles.calls.size)
    }

    @Test
    fun noNameIsKnownUntilTheProfileAnswers() = runTest {
        val gate = CompletableDeferred<Unit>()
        val resolver = resolver(CountingProfiles(mapOf(lucia to "Lucia Fernandez"), gate))

        resolver.resolve(listOf(lucia))
        runCurrent()

        assertNull(resolver.names.value[lucia])
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals("Lucia Fernandez", resolver.names.value[lucia])
    }

    @Test
    fun aMissingProfileKeepsTheFallbackAndIsNotAskedAgain() = runTest {
        val profiles = CountingProfiles(emptyMap())
        val resolver = resolver(profiles)

        resolver.resolve(listOf(mateo))
        advanceUntilIdle()
        resolver.resolve(listOf(mateo))
        advanceUntilIdle()

        assertNull(resolver.names.value[mateo])
        assertEquals(1, profiles.calls.size)
    }

    @Test
    fun aFailureKeepsTheFallbackAndDoesNotBreakTheOthers() = runTest {
        val profiles = CountingProfiles(mapOf(lucia to "Lucia Fernandez"), failing = true)
        val resolver = resolver(profiles)

        resolver.resolve(listOf(mateo))
        advanceUntilIdle()
        assertNull(resolver.names.value[mateo])

        profiles.failing = false
        resolver.resolve(listOf(lucia))
        advanceUntilIdle()
        assertEquals("Lucia Fernandez", resolver.names.value[lucia])
    }

    @Test
    fun retryMissingAsksAgainOnlyForAddressesThatHadNoName() = runTest {
        val profiles = CountingProfiles(mapOf(lucia to "Lucia Fernandez"), failing = true)
        val resolver = resolver(profiles)
        resolver.resolve(listOf(lucia))
        advanceUntilIdle()
        profiles.failing = false

        resolver.retryMissing()
        resolver.resolve(listOf(lucia))
        advanceUntilIdle()

        assertEquals(listOf(lucia, lucia), profiles.calls)
        assertEquals("Lucia Fernandez", resolver.names.value[lucia])

        resolver.retryMissing()
        resolver.resolve(listOf(lucia))
        advanceUntilIdle()
        assertEquals(2, profiles.calls.size)
    }

    @Test
    fun theFakeProfileRepositorySeedFeedsTheResolver() = runTest {
        val resolver = resolver(FakeProfileRepository())

        resolver.resolve(listOf("0x1111111111111111111111111111111111111111", "0x4444444444444444444444444444444444444444"))
        advanceUntilIdle()

        assertEquals(mapOf(lucia to "Lucia Fernandez"), resolver.names.value)
    }
}
