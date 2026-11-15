package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.ProfileUpdateRequestDto
import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.data.api.dto.toDto
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.profile.ProfileRepository

/**
 * `GET` and `PUT /profiles/{address}`. The API returns the contact only to the owner, so
 * [publicName] reads the display name and never expects a contact. `PUT` answers 201 on create
 * and 200 on update, and both are a saved profile.
 */
class ApiProfileRepository(private val api: LatiFiApi) : ProfileRepository {
    override suspend fun get(address: String): Result<Profile> =
        apiResult(call = { api.getProfile(address.lowercase()) }, transform = { it.toDomain() })

    override suspend fun upsert(address: String, displayName: String, contact: Contact): Result<Profile> =
        apiResult(
            call = { api.upsertProfile(address.lowercase(), ProfileUpdateRequestDto(displayName, contact.toDto())) },
            transform = { it.toDomain() },
        )

    override suspend fun publicName(address: String): String? =
        get(address).getOrNull()?.displayName?.takeIf { it.isNotBlank() }
}
