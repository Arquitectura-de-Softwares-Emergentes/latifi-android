package com.latifi.wallet.data.fake

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.profile.ProfileRepository
import java.time.Clock

/**
 * An in-memory profile service for the demo mode and for tests. The seed map holds public names
 * of other addresses (the feed plans read them through [publicName]). A test queues a failure with
 * [failNext] and reads what was saved from [upserts].
 */
class FakeProfileRepository(
    private val clock: Clock = Clock.systemUTC(),
    private val publicNames: Map<String, String> = DEFAULT_PUBLIC_NAMES,
) : ProfileRepository {
    private val own = mutableMapOf<String, Profile>()

    /** The next call to [get] or [upsert] fails with this error, once. */
    var failNext: AppError? = null

    /** Every save, in order, as the address and the profile that came back. */
    val upserts = mutableListOf<Pair<String, Profile>>()

    var getCalls = 0
        private set

    /** Puts a profile in place, for a test or a demo script (a reinstall of an existing person). */
    fun seed(profile: Profile) {
        own[key(profile.address)] = profile
    }

    override suspend fun get(address: String): Result<Profile> {
        getCalls += 1
        takeFailure()?.let { return Result.failure(AppException(it)) }
        val profile = own[key(address)] ?: return Result.failure(AppException(AppError.ProfileNotFound))
        return Result.success(profile)
    }

    override suspend fun upsert(address: String, displayName: String, contact: Contact): Result<Profile> {
        takeFailure()?.let { return Result.failure(AppException(it)) }
        val now = clock.instant()
        val previous = own[key(address)]
        val saved = Profile(
            address = address,
            displayName = displayName,
            contact = contact,
            createdAt = previous?.createdAt ?: now,
            updatedAt = now,
        )
        own[key(address)] = saved
        upserts += address to saved
        return Result.success(saved)
    }

    override suspend fun publicName(address: String): String? =
        own[key(address)]?.displayName ?: publicNames[key(address)]

    private fun takeFailure(): AppError? = failNext.also { failNext = null }

    // Addresses compare without case, like the API does.
    private fun key(address: String) = address.lowercase()

    companion object {
        val DEFAULT_PUBLIC_NAMES: Map<String, String> = mapOf(
            "0x1111111111111111111111111111111111111111" to "Lucia Fernandez",
            "0x2222222222222222222222222222222222222222" to "Mateo Quispe",
            "0x3333333333333333333333333333333333333333" to "Camila Rojas",
        )
    }
}
