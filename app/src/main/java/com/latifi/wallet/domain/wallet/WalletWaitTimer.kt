package com.latifi.wallet.domain.wallet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The 15 second wait for a wallet answer. It never discards anything: when it runs out it only
 * flips to [State.TimedOut] so the screen can offer "Keep waiting" and "Reconnect wallet".
 * [keepWaiting] starts the wait again and can be used as many times as the person wants.
 *
 * The timer runs on the delay of [scope], so a test with virtual time controls it exactly.
 */
class WalletWaitTimer(
    private val scope: CoroutineScope,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    enum class State { Idle, Waiting, TimedOut }

    private val mutableState = MutableStateFlow(State.Idle)
    private var job: Job? = null

    val state: StateFlow<State> = mutableState.asStateFlow()

    /** Starts counting. Call it when the wallet has just been asked. */
    fun start() = restart()

    /** The person chose to wait longer. There is no limit on how often. */
    fun keepWaiting() = restart()

    fun stop() {
        job?.cancel()
        job = null
        mutableState.value = State.Idle
    }

    private fun restart() {
        job?.cancel()
        mutableState.value = State.Waiting
        job = scope.launch {
            delay(timeoutMillis)
            mutableState.value = State.TimedOut
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000L
    }
}
