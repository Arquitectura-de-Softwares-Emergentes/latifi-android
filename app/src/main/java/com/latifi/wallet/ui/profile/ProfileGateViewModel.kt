package com.latifi.wallet.ui.profile

import androidx.lifecycle.ViewModel
import com.latifi.wallet.domain.profile.ProfileGate
import com.latifi.wallet.domain.session.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Hands the profile gate and the session to a composable that guards an entry point, such as the
 * Request a loan button of Home. The check itself lives in [rememberRequireProfile].
 */
@HiltViewModel
class ProfileGateViewModel @Inject constructor(
    val gate: ProfileGate,
    val sessions: SessionStore,
) : ViewModel()
