package com.latifi.wallet.ui.coins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.R
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.tx.BalanceRefresh
import com.latifi.wallet.domain.tx.TransactionCoordinator
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxPlans
import com.latifi.wallet.domain.tx.TxPrimary
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/** [needsWallet] is true when Get test coins was asked while no wallet is connected. */
data class CoinsUiState(val needsWallet: Boolean = false)

/**
 * S18. "Get 1,000 tUSD" starts one `mint` through [TransactionCoordinator]. The coins sheet steps
 * aside and the status sheet takes over. On Confirmed its button, "View my balance", asks every
 * balance reader to refresh once and closes. A second tap while a transaction runs starts nothing:
 * the coordinator refuses a second `begin`.
 */
@HiltViewModel
class CoinsViewModel @Inject constructor(
    private val coordinator: TransactionCoordinator,
    private val wallet: WalletGateway,
    private val chain: ChainReadRepository,
    private val overlays: OverlayController,
    private val balances: BalanceRefresh,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CoinsUiState())
    val state: StateFlow<CoinsUiState> = mutableState.asStateFlow()

    private var starting = false

    fun confirm() {
        if (starting) return
        val from = (wallet.state.value as? WalletState.Connected)?.address
        if (from == null) {
            mutableState.value = CoinsUiState(needsWallet = true)
            return
        }
        starting = true
        viewModelScope.launch {
            try {
                val before = chain.tokenBalance(from).getOrNull()?.baseUnits ?: BigInteger.ZERO
                val context = TxContext(
                    action = TxAction.Coins,
                    confirmedPrimary = TxPrimary(R.string.coins_view_balance) { balances.refresh() },
                )
                if (coordinator.begin(context, TxPlans.mint(before), from)) {
                    // The status sheet is on top now. Closing this one means a retry or a cancel lands on the screen below.
                    overlays.dismiss(Overlays.COINS)
                }
            } finally {
                starting = false
            }
        }
    }

    fun close() {
        overlays.dismiss(Overlays.COINS)
    }
}
