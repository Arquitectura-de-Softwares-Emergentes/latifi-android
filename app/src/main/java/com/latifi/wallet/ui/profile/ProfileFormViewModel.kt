package com.latifi.wallet.ui.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ContactError
import com.latifi.wallet.domain.profile.NameError
import com.latifi.wallet.domain.profile.ProfileRepository
import com.latifi.wallet.domain.profile.ProfileValidator
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.ui.nav.RouteArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfileField { Name, Contact }

/** Ask the screen to move focus to [field]. [nonce] changes on every ask, so a repeat still fires. */
data class FocusRequest(val field: ProfileField, val nonce: Int)

data class ProfileFormState(
    val name: String = "",
    val contactType: ContactType = ContactType.EMAIL,
    val contact: String = "",
    val nameError: NameError? = null,
    val contactError: ContactError? = null,
    /** How many fields the last submit rejected. Zero hides the summary. */
    val errorCount: Int = 0,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    /** True when a blocked action opened the form, which shows the gate title and body. */
    val fromGate: Boolean = false,
    val role: AppRole? = null,
    val focusRequest: FocusRequest? = null,
)

sealed interface ProfileFormEvent {
    data class NameChanged(val value: String) : ProfileFormEvent

    data class TypeChanged(val type: ContactType) : ProfileFormEvent

    data class ContactChanged(val value: String) : ProfileFormEvent

    /** A field lost focus after the person had been in it: validate that field. */
    data class Blurred(val field: ProfileField) : ProfileFormEvent

    data object Submit : ProfileFormEvent

    data object FocusHandled : ProfileFormEvent

    data object SaveErrorShown : ProfileFormEvent

    data object OpenTerms : ProfileFormEvent

    data object OpenPrivacy : ProfileFormEvent
}

sealed interface ProfileFormEffect {
    /**
     * The profile is saved. The route shows "Details saved" and pops back. When [resultKey] is
     * not null the route first writes `true` under that key (`profile_gate_result`) in the
     * previous back stack entry, so the blocked action continues with its own values.
     */
    data class Saved(val resultKey: String?) : ProfileFormEffect

    data class OpenLink(val url: String) : ProfileFormEffect
}

/**
 * S05. The values live in the [SavedStateHandle], so rotation and process death keep what the
 * person typed. The address always comes from the session, never from a route argument. An
 * existing profile fills the form when the person edits their details from the Profile tab.
 */
@HiltViewModel
class ProfileFormViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val profiles: ProfileRepository,
    private val sessions: SessionStore,
    private val prefs: PrefsStore,
    private val config: AppConfig,
) : ViewModel() {
    private val fromGate = savedState.get<Boolean>(RouteArgs.GATE) ?: false
    private var nonce = 0

    private val mutableState = MutableStateFlow(
        ProfileFormState(
            name = savedState.get<String>(KEY_NAME).orEmpty(),
            contactType = savedState.get<String>(KEY_TYPE)?.let { n -> ContactType.entries.firstOrNull { it.name == n } }
                ?: ContactType.EMAIL,
            contact = savedState.get<String>(KEY_CONTACT).orEmpty(),
            fromGate = fromGate,
        ),
    )
    private val effects = Channel<ProfileFormEffect>(Channel.BUFFERED)

    val state: StateFlow<ProfileFormState> = mutableState.asStateFlow()
    val effect = effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val role = prefs.role.first()
            mutableState.value = mutableState.value.copy(role = role)
        }
        // Nothing typed yet: load the saved profile, if the person has one (an edit).
        if (!savedState.contains(KEY_NAME) && !savedState.contains(KEY_CONTACT)) {
            viewModelScope.launch {
                val address = sessions.current()?.address ?: return@launch
                val profile = profiles.get(address).getOrNull() ?: return@launch
                if (savedState.contains(KEY_NAME) || savedState.contains(KEY_CONTACT)) return@launch
                val type = profile.contact?.type ?: ContactType.EMAIL
                savedState[KEY_NAME] = profile.displayName
                savedState[KEY_TYPE] = type.name
                savedState[KEY_CONTACT] = profile.contact?.value.orEmpty()
                mutableState.value = mutableState.value.copy(
                    name = profile.displayName,
                    contactType = type,
                    contact = profile.contact?.value.orEmpty(),
                )
            }
        }
    }

    fun onEvent(event: ProfileFormEvent) {
        when (event) {
            is ProfileFormEvent.NameChanged -> {
                savedState[KEY_NAME] = event.value
                update { copy(name = event.value, nameError = nameError?.let { ProfileValidator.validateName(event.value) }) }
            }
            is ProfileFormEvent.TypeChanged -> {
                savedState[KEY_TYPE] = event.type.name
                // The old error was about the old type, so it clears and the value is checked again on blur.
                update { copy(contactType = event.type, contactError = null) }
            }
            is ProfileFormEvent.ContactChanged -> {
                savedState[KEY_CONTACT] = event.value
                update {
                    copy(
                        contact = event.value,
                        contactError = contactError?.let { ProfileValidator.validateContact(contactType, event.value) },
                    )
                }
            }
            is ProfileFormEvent.Blurred -> update {
                when (event.field) {
                    ProfileField.Name -> copy(nameError = ProfileValidator.validateName(name))
                    ProfileField.Contact -> copy(contactError = ProfileValidator.validateContact(contactType, contact))
                }
            }
            ProfileFormEvent.Submit -> submit()
            ProfileFormEvent.FocusHandled -> update { copy(focusRequest = null) }
            ProfileFormEvent.SaveErrorShown -> update { copy(saveFailed = false) }
            ProfileFormEvent.OpenTerms -> effects.trySend(ProfileFormEffect.OpenLink(config.termsUrl))
            ProfileFormEvent.OpenPrivacy -> effects.trySend(ProfileFormEffect.OpenLink(config.privacyUrl))
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.saving) return
        val errors = ProfileValidator.validate(current.name, current.contactType, current.contact)
        if (!errors.isValid) {
            val first = if (errors.name != null) ProfileField.Name else ProfileField.Contact
            update {
                copy(
                    nameError = errors.name,
                    contactError = errors.contact,
                    errorCount = errors.count,
                    focusRequest = FocusRequest(first, ++nonce),
                )
            }
            return
        }
        update { copy(nameError = null, contactError = null, errorCount = 0, saving = true) }
        viewModelScope.launch {
            val address = sessions.current()?.address
            val saved = if (address == null) {
                false
            } else {
                profiles.upsert(
                    address = address,
                    displayName = current.name.trim(),
                    contact = Contact(current.contactType, current.contact.trim()),
                ).isSuccess
            }
            if (saved) {
                update { copy(saving = false) }
                effects.send(ProfileFormEffect.Saved(resultKey = if (fromGate) PROFILE_GATE_RESULT else null))
            } else {
                // The values stay, so the person can try again without typing anything.
                update { copy(saving = false, saveFailed = true) }
            }
        }
    }

    private inline fun update(change: ProfileFormState.() -> ProfileFormState) {
        mutableState.value = mutableState.value.change()
    }

    private companion object {
        const val KEY_NAME = "form_name"
        const val KEY_TYPE = "form_contact_type"
        const val KEY_CONTACT = "form_contact"
    }
}
