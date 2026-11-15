package com.latifi.wallet.data.wallet

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries "open the wallet picker" from the gateway to the host composable, and "the picker is
 * closed" or "it could not open" back. The picker is a navigation destination, so only the
 * composable that owns the NavController can open it. No replay: a request made while nobody is
 * listening is dropped, which is right, because the Connect screen is on screen when it asks.
 */
@Singleton
class AppKitPickerBridge @Inject constructor() {
    private val mutableRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutableClosed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val mutableFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val requests: SharedFlow<Unit> = mutableRequests.asSharedFlow()

    /** The person left the picker, with or without a connection. */
    val closed: SharedFlow<Unit> = mutableClosed.asSharedFlow()

    /** The picker could not be opened. */
    val failed: SharedFlow<Unit> = mutableFailed.asSharedFlow()

    fun request() {
        mutableRequests.tryEmit(Unit)
    }

    fun pickerClosed() {
        mutableClosed.tryEmit(Unit)
    }

    fun openFailed() {
        mutableFailed.tryEmit(Unit)
    }
}
