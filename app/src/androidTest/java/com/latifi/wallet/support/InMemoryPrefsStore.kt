package com.latifi.wallet.support

import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.prefs.PrefsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An in-memory [PrefsStore] so a screen test never touches the real DataStore file. */
class InMemoryPrefsStore(role: AppRole? = AppRole.Borrower) : PrefsStore {
    private val roleState = MutableStateFlow(role)
    private val seenState = MutableStateFlow(true)
    private val currencyState = MutableStateFlow(LocalCurrency.PEN)

    override val role: Flow<AppRole?> = roleState
    override val onboardingSeen: Flow<Boolean> = seenState
    override val currency: Flow<LocalCurrency> = currencyState

    override suspend fun setRole(role: AppRole) {
        roleState.value = role
    }

    override suspend fun clearRole() {
        roleState.value = null
    }

    override suspend fun setOnboardingSeen(seen: Boolean) {
        seenState.value = seen
    }

    override suspend fun setCurrency(currency: LocalCurrency) {
        currencyState.value = currency
    }
}
