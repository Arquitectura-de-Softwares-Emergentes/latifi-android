package com.latifi.wallet.domain.tx

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A one line signal: "the token balance changed, read it again". The test coin sheet sends it
 * after a confirmed mint, and Home reloads its balance section when it hears it.
 */
@Singleton
class BalanceRefresh @Inject constructor() {
    private val mutableTicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val ticks: SharedFlow<Unit> = mutableTicks.asSharedFlow()

    fun refresh() {
        mutableTicks.tryEmit(Unit)
    }
}
