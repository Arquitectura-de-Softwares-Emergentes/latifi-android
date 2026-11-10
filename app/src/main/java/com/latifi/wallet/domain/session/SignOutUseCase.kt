package com.latifi.wallet.domain.session

import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.wallet.WalletGateway
import javax.inject.Inject

/**
 * Something a later feature clears when the person disconnects (the pending transaction record,
 * cached lists, the rates cache). Hooks are collected through a Hilt multibinding, so a plan adds
 * one with `@Binds @IntoSet` and never edits [SignOutUseCase].
 */
interface SignOutHook {
    suspend fun onSignOut()
}

/**
 * Disconnect. It ends the wallet session, clears the token and the stored address (the session
 * store), clears the role so role selection shows again, and runs every hook. Language and local
 * currency are device preferences and stay. It never touches the chain or the wallet itself.
 */
class SignOutUseCase @Inject constructor(
    private val wallet: WalletGateway,
    private val sessions: SessionStore,
    private val prefs: PrefsStore,
    private val hooks: @JvmSuppressWildcards Set<SignOutHook>,
) {
    suspend operator fun invoke() {
        // One failing step must not leave credentials behind, so the local clearing always runs.
        try {
            wallet.disconnect()
        } finally {
            sessions.clear()
            prefs.clearRole()
            hooks.forEach { hook -> runCatching { hook.onSignOut() } }
        }
    }
}
