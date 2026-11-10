package com.latifi.wallet.domain.profile

import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.Profile

/**
 * The off-chain profile of the signed-in person (OpenAPI 0.2.0 `GET` and `PUT /profiles/{address}`).
 * The HTTP codes stay behind `Result`: a missing profile fails with `AppException(ProfileNotFound)`
 * and every other failure carries its own `AppError`.
 */
interface ProfileRepository {
    /** The person's own profile, contact included. Fails with `ProfileNotFound` when there is none. */
    suspend fun get(address: String): Result<Profile>

    /** Creates or replaces the profile. The API answers 201 on create and 200 on update. */
    suspend fun upsert(address: String, displayName: String, contact: Contact): Result<Profile>

    /** The display name anyone may see, or null when the address has no profile or the call failed. */
    suspend fun publicName(address: String): String?
}
