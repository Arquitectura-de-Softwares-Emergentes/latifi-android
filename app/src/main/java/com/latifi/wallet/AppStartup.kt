package com.latifi.wallet

import com.latifi.wallet.core.AppConfig
import com.latifi.wallet.core.AppMode
import com.latifi.wallet.data.wallet.AppKitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Provider

/** Starts the wallet SDK. A fake in tests. */
fun interface WalletBootstrap {
    fun initialize()
}

/** Picks the stored in-flight transaction up again (the transaction resumer). */
fun interface PendingResume {
    fun resume()
}

/**
 * The fixed start-up order, called from `Application.onCreate`:
 *
 * 1. the wallet SDK starts (Live mode only, so the placeholder project id never reaches it);
 * 2. the consumers of wallet answers are built (the gateway and the coordinator subscribe to the flows);
 * 3. the SDK delegate is registered, so every answer finds a listener;
 * 4. the wallet session is extended;
 * 5. the transaction resumer runs.
 *
 * The order is research Pitfall 10: Android may kill the app while the person is in the wallet,
 * and the answer then arrives while the process is starting. A delegate or a collector that comes
 * later than the resumer would drop that answer, and the person would be asked to send it twice.
 */
class AppStartup(
    private val config: AppConfig,
    private val bootstrap: WalletBootstrap,
    private val client: AppKitClient,
    private val resumer: Provider<PendingResume>,
    private val scope: CoroutineScope,
) {
    fun run() {
        val live = config.mode == AppMode.Live
        if (live) bootstrap.initialize()
        val resume = resumer.get()
        if (live) client.attachDelegate()
        scope.launch {
            if (live) client.extendSession()
            resume.resume()
        }
    }
}
