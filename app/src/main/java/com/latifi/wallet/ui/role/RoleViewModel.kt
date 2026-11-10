package com.latifi.wallet.ui.role

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.prefs.PrefsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoleUiState(val selected: AppRole? = null)

sealed interface RoleEvent {
    data class Select(val role: AppRole) : RoleEvent

    data object Confirm : RoleEvent
}

sealed interface RoleEffect {
    data object GoToMain : RoleEffect
}

/** S04. The choice survives rotation through the saved state. Confirming stores the role. */
@HiltViewModel
class RoleViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val prefs: PrefsStore,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        RoleUiState(selected = savedState.get<String>(KEY_SELECTED)?.let { name -> AppRole.entries.firstOrNull { it.name == name } }),
    )
    private val effects = Channel<RoleEffect>(Channel.BUFFERED)

    val state: StateFlow<RoleUiState> = mutableState.asStateFlow()
    val effect = effects.receiveAsFlow()

    fun onEvent(event: RoleEvent) {
        when (event) {
            is RoleEvent.Select -> {
                savedState[KEY_SELECTED] = event.role.name
                mutableState.value = RoleUiState(selected = event.role)
            }
            RoleEvent.Confirm -> {
                val role = mutableState.value.selected ?: return
                viewModelScope.launch {
                    prefs.setRole(role)
                    effects.send(RoleEffect.GoToMain)
                }
            }
        }
    }

    private companion object {
        const val KEY_SELECTED = "selected"
    }
}
