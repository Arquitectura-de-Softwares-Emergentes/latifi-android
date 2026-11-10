package com.latifi.wallet.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.domain.prefs.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ONBOARDING_PAGE_COUNT = 4

/** [showConnect] is false when the pager was reopened from Profile, where the last page leaves the flow. */
data class OnboardingUiState(val page: Int = 0, val showConnect: Boolean = true) {
    val isFirst: Boolean get() = page == 0
    val isLast: Boolean get() = page == ONBOARDING_PAGE_COUNT - 1
}

sealed interface OnboardingEvent {
    data object Next : OnboardingEvent

    data object Back : OnboardingEvent

    /** The pager settled on [page] after a swipe. */
    data class PageSettled(val page: Int) : OnboardingEvent

    data object OpenTerms : OnboardingEvent

    data object OpenPrivacy : OnboardingEvent
}

sealed interface OnboardingEffect {
    /** Page 4 was confirmed with the Connect step on. */
    data object GoToConnect : OnboardingEffect

    /** Page 4 was confirmed without the Connect step. */
    data object Leave : OnboardingEffect

    data class OpenLink(val url: String) : OnboardingEffect
}

/**
 * Holds the page number in the saved state, so a rotation or a process restart returns to the
 * same page. Finishing the first run marks onboarding as seen.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val prefs: PrefsStore,
    private val config: AppConfig,
) : ViewModel() {
    private val showConnect: Boolean = savedState.get<Boolean>(KEY_SHOW_CONNECT) ?: true
    private val mutableState = MutableStateFlow(
        OnboardingUiState(page = (savedState.get<Int>(KEY_PAGE) ?: 0).coerceIn(0, ONBOARDING_PAGE_COUNT - 1), showConnect = showConnect),
    )
    private val effects = Channel<OnboardingEffect>(Channel.BUFFERED)

    val state: StateFlow<OnboardingUiState> = mutableState.asStateFlow()
    val effect = effects.receiveAsFlow()

    fun onEvent(event: OnboardingEvent) {
        when (event) {
            OnboardingEvent.Next -> next()
            OnboardingEvent.Back -> goTo(mutableState.value.page - 1)
            is OnboardingEvent.PageSettled -> goTo(event.page)
            OnboardingEvent.OpenTerms -> effects.trySend(OnboardingEffect.OpenLink(config.termsUrl))
            OnboardingEvent.OpenPrivacy -> effects.trySend(OnboardingEffect.OpenLink(config.privacyUrl))
        }
    }

    private fun next() {
        val current = mutableState.value
        if (!current.isLast) {
            goTo(current.page + 1)
            return
        }
        if (current.showConnect) {
            viewModelScope.launch {
                prefs.setOnboardingSeen(true)
                effects.send(OnboardingEffect.GoToConnect)
            }
        } else {
            effects.trySend(OnboardingEffect.Leave)
        }
    }

    private fun goTo(page: Int) {
        val safe = page.coerceIn(0, ONBOARDING_PAGE_COUNT - 1)
        savedState[KEY_PAGE] = safe
        mutableState.value = mutableState.value.copy(page = safe)
    }

    companion object {
        const val KEY_PAGE = "page"

        /** Set by the HOW_IT_WORKS route through its nav arguments default. */
        const val KEY_SHOW_CONNECT = "showConnect"
    }
}
