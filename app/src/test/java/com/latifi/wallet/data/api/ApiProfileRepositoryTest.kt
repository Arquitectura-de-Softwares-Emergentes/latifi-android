package com.latifi.wallet.data.api

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiProfileRepositoryTest {
    private val rig = ApiTestRig()
    private val repository = ApiProfileRepository(rig.api)
    private val address = "0xAbC1111111111111111111111111111111111111"
    private val own = """{"address":"$address","displayName":"Sample User","contact":{"type":"EMAIL","value":"user@example.org"},"createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:00:00Z"}"""
    private val publicOnly = """{"address":"$address","displayName":"Sample User","createdAt":"2026-01-01T12:00:00Z","updatedAt":"2026-01-01T12:00:00Z"}"""

    @After
    fun tearDown() = rig.close()

    @Test
    fun getReturnsTheOwnProfileWithItsContact() = runBlocking {
        rig.enqueueJson(own)

        val profile = repository.get(address).getOrThrow()

        assertEquals("/api/v1/profiles/${address.lowercase()}", rig.server.takeRequest().path)
        assertEquals("Sample User", profile.displayName)
        assertEquals(Contact(ContactType.EMAIL, "user@example.org"), profile.contact)
    }

    @Test
    fun aMissingProfileFailsWithProfileNotFound() = runBlocking {
        rig.enqueueError(404, "PROFILE_NOT_FOUND")

        val failure = repository.get(address).exceptionOrNull() as AppException

        assertEquals(AppError.ProfileNotFound, failure.error)
    }

    @Test
    fun publicNameReturnsTheDisplayNameAndNeverExpectsAContact() = runBlocking {
        rig.enqueueJson(publicOnly)

        assertEquals("Sample User", repository.publicName(address))
    }

    @Test
    fun publicNameIsNullWhenThereIsNoProfileOrTheCallFails() = runBlocking {
        rig.enqueueError(404, "PROFILE_NOT_FOUND")
        rig.enqueueError(500, "INTERNAL_ERROR")

        assertNull(repository.publicName(address))
        assertNull(repository.publicName(address))
    }

    @Test
    fun upsertAcceptsBoth201AndTheSecond200() = runBlocking {
        rig.enqueueJson(own, code = 201)
        rig.enqueueJson(own, code = 200)
        val contact = Contact(ContactType.EMAIL, "user@example.org")

        val created = repository.upsert(address, "Sample User", contact).getOrThrow()
        val updated = repository.upsert(address, "Sample User", contact).getOrThrow()

        assertEquals("Sample User", created.displayName)
        assertEquals("Sample User", updated.displayName)
        val request = rig.server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/v1/profiles/${address.lowercase()}", request.path)
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Sample User", body.getValue("displayName").jsonPrimitive.content)
        assertEquals("EMAIL", body.getValue("contact").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("user@example.org", body.getValue("contact").jsonObject.getValue("value").jsonPrimitive.content)
    }

    @Test
    fun aWriteForSomeoneElsesAddressIsForbiddenAndKeepsItsCodeOutOfTheCopy() = runBlocking {
        rig.enqueueError(403, "FORBIDDEN_NOT_OWNER")

        val failure = repository.upsert(address, "Sample User", Contact(ContactType.PHONE, "+51999999999")).exceptionOrNull() as AppException

        assertEquals(AppError.Unknown("FORBIDDEN_NOT_OWNER"), failure.error)
    }

    @Test
    fun aValidationFailureMapsToValidation() = runBlocking {
        rig.enqueueError(400, "VALIDATION_FAILED")

        val failure = repository.upsert(address, "", Contact(ContactType.EMAIL, "x")).exceptionOrNull() as AppException

        assertEquals(AppError.Validation(), failure.error)
    }
}
