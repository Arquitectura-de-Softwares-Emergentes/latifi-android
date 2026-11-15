package com.latifi.wallet.domain.profile

import javax.inject.Inject

/**
 * Runs once after a successful sign-in. When the address already has a profile (a reinstall and
 * reconnect), the profile loads silently and its display name waits in [SessionHints] for Home to
 * greet the person. A missing profile or a failed call queues nothing and never blocks the person.
 */
class PostSignInUseCase @Inject constructor(
    private val profiles: ProfileRepository,
    private val hints: SessionHints,
) {
    suspend fun run(address: String) {
        val profile = profiles.get(address).getOrNull() ?: return
        if (profile.displayName.isNotBlank()) hints.queueGreeting(profile.displayName)
    }
}
