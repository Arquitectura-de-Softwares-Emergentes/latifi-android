package com.latifi.wallet.domain.session

import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.wallet.WalletGateway
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        // The wallet gets a limited time: with the relay down the SDK may never answer, and the
        // token, the address and the pending transaction record must not wait for it.
        try {
            withTimeoutOrNull(WALLET_DISCONNECT_TIMEOUT_MILLIS) { wallet.disconnect() }
        } finally {
            // NonCancellable: a cancelled caller (the person leaves the screen) still clears the disk.
            withContext(NonCancellable) {
                sessions.clear()
                prefs.clearRole()
                hooks.forEach { hook -> runCatching { hook.onSignOut() } }
            }
        }
    }

    companion object {
        const val WALLET_DISCONNECT_TIMEOUT_MILLIS = 5_000L
    }
}
