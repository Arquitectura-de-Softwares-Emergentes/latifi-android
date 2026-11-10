package com.latifi.wallet.domain.profile

import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PostSignInUseCaseTest {
    private val address = "0xAbC0000000000000000000000000000000000001"
    private val at = Instant.parse("2026-11-01T00:00:00Z")

    private fun seeded(name: String = "Ana Torres") = FakeProfileRepository().apply {
        seed(Profile(address, name, Contact(ContactType.EMAIL, "ana@example.com"), at, at))
    }

    @Test
    fun anExistingProfileQueuesItsNameOnce() = runBlocking {
        val hints = SessionHints()
        PostSignInUseCase(seeded(), hints).run(address)

        assertEquals("Ana Torres", hints.greetingName.value)
        assertEquals("Ana Torres", hints.consume())
        assertNull(hints.consume())
        assertNull(hints.greetingName.value)
    }

    @Test
    fun aMissingProfileQueuesNothing() = runBlocking {
        val hints = SessionHints()
        PostSignInUseCase(FakeProfileRepository(), hints).run(address)
        assertNull(hints.greetingName.value)
    }

    @Test
    fun aFailedCallQueuesNothingAndDoesNotThrow() = runBlocking {
        val repo = seeded().apply { failNext = AppError.Offline }
        val hints = SessionHints()
        PostSignInUseCase(repo, hints).run(address)
        assertNull(hints.greetingName.value)
    }

    @Test
    fun aBlankDisplayNameQueuesNothing() = runBlocking {
        val hints = SessionHints()
        PostSignInUseCase(seeded(name = "  "), hints).run(address)
        assertNull(hints.greetingName.value)
    }

    @Test
    fun aSecondSignInReplacesTheQueuedName() = runBlocking {
        val hints = SessionHints()
        hints.queueGreeting("Old")
        PostSignInUseCase(seeded(), hints).run(address)
        assertEquals("Ana Torres", hints.consume())
    }
}
