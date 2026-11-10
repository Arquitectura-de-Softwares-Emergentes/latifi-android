package com.latifi.wallet.domain.profile

import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import javax.inject.Inject

/** What the gate found. */
enum class GateResult {
    /** The profile exists and has a contact, so publishing and funding may go on. */
    Complete,

    /** There is no profile, or it has no contact. The form must open first. */
    Missing,
}

/**
 * Decides whether a person may publish a request or fund one. Only a missing profile or an empty
 * contact gives [GateResult.Missing]. Any other failure (offline, server) is handed back as a
 * failed `Result`, so a broken network can never look like a pass.
 */
class ProfileGate @Inject constructor(private val profiles: ProfileRepository) {
    suspend fun check(address: String): Result<GateResult> {
        val result = profiles.get(address)
        val profile = result.getOrNull()
        if (profile != null) {
            val hasContact = profile.contact?.value?.isNotBlank() == true
            return Result.success(if (hasContact) GateResult.Complete else GateResult.Missing)
        }
        val error = (result.exceptionOrNull() as? AppException)?.error
        return if (error == AppError.ProfileNotFound) {
            Result.success(GateResult.Missing)
        } else {
            Result.failure(result.exceptionOrNull() ?: AppException(AppError.Unknown()))
        }
    }
}
