package com.latifi.wallet.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.prefs.PrefsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** File name of the preferences store, without the extension DataStore adds. */
const val PREFS_FILE_NAME = "latifi_prefs"

/**
 * Preferences kept in a Preferences DataStore. It is built from a given [DataStore] so a test
 * can pass one that lives in a temporary file. Nothing here is sensitive: a role, a flag and the local currency code.
 */
class DataStorePrefsStore(private val store: DataStore<Preferences>) : PrefsStore {
    override val role: Flow<AppRole?> = store.data.map { prefs ->
        prefs[ROLE_KEY]?.let { name -> AppRole.entries.firstOrNull { it.name == name } }
    }

    override val onboardingSeen: Flow<Boolean> = store.data.map { it[ONBOARDING_SEEN_KEY] ?: false }

    override val currency: Flow<LocalCurrency> = store.data.map { prefs ->
        prefs[CURRENCY_KEY]?.let(LocalCurrency::fromCode) ?: LocalCurrency.PEN
    }

    override suspend fun setRole(role: AppRole) {
        store.edit { it[ROLE_KEY] = role.name }
    }

    override suspend fun clearRole() {
        store.edit { it.remove(ROLE_KEY) }
    }

    override suspend fun setOnboardingSeen(seen: Boolean) {
        store.edit { it[ONBOARDING_SEEN_KEY] = seen }
    }

    override suspend fun setCurrency(currency: LocalCurrency) {
        store.edit { it[CURRENCY_KEY] = currency.code }
    }

    private companion object {
        val ROLE_KEY = stringPreferencesKey("role")
        val CURRENCY_KEY = stringPreferencesKey("currency")
        val ONBOARDING_SEEN_KEY = booleanPreferencesKey("onboarding_seen")
    }
}
