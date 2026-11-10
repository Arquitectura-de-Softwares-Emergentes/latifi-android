package com.latifi.wallet.domain.prefs

import com.latifi.wallet.domain.model.AppRole
import kotlinx.coroutines.flow.Flow

/** Non sensitive choices that stay on the device. */
interface PrefsStore {
    /** The chosen role, or null before the first choice. */
    val role: Flow<AppRole?>

    /** True once the person has been through the four onboarding pages. */
    val onboardingSeen: Flow<Boolean>

    suspend fun setRole(role: AppRole)

    suspend fun clearRole()

    suspend fun setOnboardingSeen(seen: Boolean)
}
