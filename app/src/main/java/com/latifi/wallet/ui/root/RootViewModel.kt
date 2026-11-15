package com.latifi.wallet.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves the start destination once, from what is stored, and keeps the role fresh for the
 * tab container. The NavHost waits for [startDestination] to be non null, so it is built with
 * the right first screen and never flashes another one.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessions: SessionStore,
    private val prefs: PrefsStore,
) : ViewModel() {
    private val resolved = MutableStateFlow<String?>(null)

    /** Null until the stored values are read, then the route to open first. */
    val startDestination: StateFlow<String?> = resolved.asStateFlow()

    /** The stored role. Borrower until a role is chosen, so the tabs always have a value. */
    val role: StateFlow<AppRole> = prefs.role
        .map { it ?: AppRole.Borrower }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppRole.Borrower)

    init {
        viewModelScope.launch {
            resolved.value = StartDestinationResolver.resolve(
                hasSession = sessions.current() != null,
                role = prefs.role.first(),
                onboardingSeen = prefs.onboardingSeen.first(),
            )
        }
    }
}
