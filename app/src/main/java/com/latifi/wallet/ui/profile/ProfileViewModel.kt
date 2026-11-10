package com.latifi.wallet.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.BuildConfig
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.language.LanguageController
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.session.SignOutUseCase
import com.latifi.wallet.ui.components.RATES_PROVIDER_URL
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A short message the screen shows in a snackbar, which a screen reader announces politely. */
enum class ProfileMessage { AddressCopied, LanguageEnglish, LanguageSpanish }

data class ProfileUiState(
    val displayName: String? = null,
    val contact: Contact? = null,
    /** The first 6 and last 4 characters of the address, for example `0x12ab...9f3c`. */
    val shortAddress: String? = null,
    val role: AppRole = AppRole.Borrower,
    val language: AppLanguage = AppLanguage.English,
    val currency: LocalCurrency = LocalCurrency.PEN,
    val versionName: String = "",
    val mode: AppMode = AppMode.Demo,
    val disconnectDialogOpen: Boolean = false,
    val message: ProfileMessage? = null,
)

sealed interface ProfileEvent {
    data object EditDetails : ProfileEvent

    data object CopyAddress : ProfileEvent

    data object SwitchRole : ProfileEvent

    data class SelectLanguage(val language: AppLanguage) : ProfileEvent

    data class SelectCurrency(val currency: LocalCurrency) : ProfileEvent

    data object OpenHowItWorks : ProfileEvent

    data object OpenTerms : ProfileEvent

    data object OpenPrivacy : ProfileEvent

    data object OpenRatesProvider : ProfileEvent

    data object AskDisconnect : ProfileEvent

    data object KeepConnected : ProfileEvent

    data object ConfirmDisconnect : ProfileEvent

    data object MessageShown : ProfileEvent
}

sealed interface ProfileEffect {
    data object OpenForm : ProfileEffect

    data class CopyToClipboard(val address: String) : ProfileEffect

    data object OpenHowItWorks : ProfileEffect

    data class OpenLink(val url: String) : ProfileEffect

    /** The person disconnected: the route opens Connect wallet with an empty back stack. */
    data object SignedOut : ProfileEffect
}

/**
 * S17. Shows what is known about the person, keeps the role, language and currency choices, and
 * runs the disconnect. A language message survives the activity recreation that the language
 * change causes, because the pending message sits in the [SavedStateHandle] and the view model
 * itself outlives the activity.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val sessions: SessionStore,
    private val prefs: PrefsStore,
    private val languages: LanguageController,
    private val signOut: SignOutUseCase,
    private val config: AppConfig,
) : ViewModel() {
    private var address: String? = null

    private val mutableState = MutableStateFlow(
        ProfileUiState(
            language = languages.current(),
            versionName = BuildConfig.VERSION_NAME,
            mode = config.mode,
            message = savedState.get<String>(KEY_MESSAGE)?.let { n -> ProfileMessage.entries.firstOrNull { it.name == n } },
        ),
    )
    private val effects = Channel<ProfileEffect>(Channel.BUFFERED)

    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()
    val effect = effects.receiveAsFlow()

    init {
        viewModelScope.launch { prefs.role.collect { role -> update { copy(role = role ?: AppRole.Borrower) } } }
        viewModelScope.launch { prefs.currency.collect { currency -> update { copy(currency = currency) } } }
        viewModelScope.launch { sessions.session.collect { session -> onSession(session?.address) } }
    }

    private suspend fun onSession(current: String?) {
        address = current
        update { copy(shortAddress = current?.let(::shorten)) }
        if (current == null) return
        profiles.get(current).onSuccess { profile ->
            update { copy(displayName = profile.displayName, contact = profile.contact) }
        }
    }

    /** Reloads the profile, called when the tab comes back after the form saved a change. */
    fun refresh() {
        val current = address ?: return
        viewModelScope.launch { onSession(current) }
    }

    fun onEvent(event: ProfileEvent) {
        when (event) {
            ProfileEvent.EditDetails -> effects.trySend(ProfileEffect.OpenForm)
            ProfileEvent.CopyAddress -> address?.let { full ->
                effects.trySend(ProfileEffect.CopyToClipboard(full))
                announce(ProfileMessage.AddressCopied)
            }
            ProfileEvent.SwitchRole -> viewModelScope.launch {
                prefs.setRole(if (mutableState.value.role == AppRole.Borrower) AppRole.Lender else AppRole.Borrower)
            }
            is ProfileEvent.SelectLanguage -> selectLanguage(event.language)
            is ProfileEvent.SelectCurrency -> viewModelScope.launch { prefs.setCurrency(event.currency) }
            ProfileEvent.OpenHowItWorks -> effects.trySend(ProfileEffect.OpenHowItWorks)
            ProfileEvent.OpenTerms -> effects.trySend(ProfileEffect.OpenLink(config.termsUrl))
            ProfileEvent.OpenPrivacy -> effects.trySend(ProfileEffect.OpenLink(config.privacyUrl))
            ProfileEvent.OpenRatesProvider -> effects.trySend(ProfileEffect.OpenLink(RATES_PROVIDER_URL))
            ProfileEvent.AskDisconnect -> update { copy(disconnectDialogOpen = true) }
            ProfileEvent.KeepConnected -> update { copy(disconnectDialogOpen = false) }
            ProfileEvent.ConfirmDisconnect -> confirmDisconnect()
            ProfileEvent.MessageShown -> {
                savedState.remove<String>(KEY_MESSAGE)
                update { copy(message = null) }
            }
        }
    }

    private fun selectLanguage(language: AppLanguage) {
        if (language == mutableState.value.language) return
        // The message is stored first: the platform recreates the activity right after `set`, and
        // the new activity shows it in the language that was just chosen.
        announce(if (language == AppLanguage.Spanish) ProfileMessage.LanguageSpanish else ProfileMessage.LanguageEnglish)
        update { copy(language = language) }
        languages.set(language)
    }

    private fun confirmDisconnect() {
        update { copy(disconnectDialogOpen = false) }
        viewModelScope.launch {
            signOut()
            effects.send(ProfileEffect.SignedOut)
        }
    }

    private fun announce(message: ProfileMessage) {
        savedState[KEY_MESSAGE] = message.name
        update { copy(message = message) }
    }

    private inline fun update(change: ProfileUiState.() -> ProfileUiState) {
        mutableState.value = mutableState.value.change()
    }

    companion object {
        private const val KEY_MESSAGE = "profile_message"

        /** `0x12ab...9f3c`: the first 6 and the last 4 characters. Short values stay whole. */
        fun shorten(address: String): String =
            if (address.length <= 12) address else address.take(6) + "..." + address.takeLast(4)
    }
}
