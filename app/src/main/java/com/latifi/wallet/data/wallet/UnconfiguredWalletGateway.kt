package com.latifi.wallet.data.wallet

import com.latifi.wallet.domain.wallet.RequestHandle
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletResponse
import com.latifi.wallet.domain.wallet.WalletState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Raised when a request is made without a wallet project id. The Connect screen explains it before this can happen. */
class WalletNotConfiguredException : IllegalStateException("The wallet connection is not configured")

/** The wallet when the build has no real project id. It never connects and never sends. */
class UnconfiguredWalletGateway : WalletGateway {
    private val mutableState = MutableStateFlow<WalletState>(WalletState.NotConfigured)
    private val mutableResponses = MutableSharedFlow<WalletResponse>()

    override val state: StateFlow<WalletState> = mutableState.asStateFlow()
    override val responses: SharedFlow<WalletResponse> = mutableResponses.asSharedFlow()

    override fun openPicker() = Unit

    override suspend fun sendRequest(request: WalletRequest): Result<RequestHandle> =
        Result.failure(WalletNotConfiguredException())

    override fun openWalletApp() = Unit

    override suspend fun disconnect() = Unit
}
