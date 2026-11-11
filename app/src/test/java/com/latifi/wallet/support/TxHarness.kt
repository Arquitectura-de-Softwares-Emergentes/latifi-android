package com.latifi.wallet.support

import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.fake.FakeRpcClient
import com.latifi.wallet.data.fake.FakeWalletGateway
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.domain.chain.ReceiptPoller
import com.latifi.wallet.domain.tx.Preflight
import com.latifi.wallet.domain.tx.Reconciler
import com.latifi.wallet.domain.tx.TransactionCoordinator
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxKind
import com.latifi.wallet.domain.tx.TxStep
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler
import java.math.BigInteger

/**
 * A coordinator wired to fakes on a test scope. Pass `backgroundScope` so the long lived
 * collectors of the coordinator and the fake wallet do not hold the test open, and the scheduler
 * of the test so every clock in the system is the same virtual one.
 */
class TxHarness(
    scope: CoroutineScope,
    val scheduler: TestCoroutineScheduler,
    reconciler: Reconciler? = null,
) {
    val wallet = FakeWalletGateway(
        scope = scope,
        initialState = WalletState.Connected(ADDRESS),
        script = FakeWalletScript.NeverAnswer,
        // The wallet connects with another address after a reconnect, as a person who picked another account would.
        demoAddress = RECONNECT_ADDRESS,
        txHashOf = { id -> hashOf(id) },
    )
    val rpc = FakeRpcClient()
    val recovery = InMemoryRecoveryStore()
    val overlays = OverlayController()
    val coordinator = TransactionCoordinator(
        scope = scope,
        wallet = wallet,
        preflight = Preflight(rpc),
        poller = ReceiptPoller(rpc),
        recovery = recovery,
        overlays = overlays,
        nowMillis = { scheduler.currentTime },
        reconciler = reconciler,
    )

    fun begin(
        action: TxAction = TxAction.Payment,
        step: TxStep = repayStep(),
        context: TxContext = TxContext(action),
    ): Boolean = coordinator.begin(context, step, ADDRESS)

    companion object {
        const val ADDRESS = "0x52908400098527886e0f7030069857d2e4169ee7"
        const val RECONNECT_ADDRESS = "0x1111111111111111111111111111111111111111"

        /** A valid 66 character reference whose last digits are the request id, so each request gets its own. */
        fun hashOf(id: Long): String = "0x" + id.toString(16).padStart(64, '0')

        fun repayStep(loanId: BigInteger = BigInteger.ONE) = TxStep(
            kind = TxKind.Repay,
            to = Deployment.LOANBOOK_ADDRESS,
            data = "0xabcdef01",
            loanId = loanId,
            amount = BigInteger.valueOf(100_000_000),
            baseline = BigInteger.ZERO,
        )

        fun requestStep() = TxStep(
            kind = TxKind.RequestLoan,
            to = Deployment.LOANBOOK_ADDRESS,
            data = "0x12345678",
        )
    }
}
