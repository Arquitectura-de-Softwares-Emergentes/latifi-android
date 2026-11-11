package com.latifi.wallet.support

import com.latifi.wallet.data.wallet.AppKitClient
import com.latifi.wallet.data.wallet.AppKitEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** The SDK adapter of the wallet gateway tests. It records every call and answers from fields. */
class FakeAppKitClient(var account: String? = null) : AppKitClient {
    private val mutableEvents = MutableSharedFlow<AppKitEvent>(extraBufferCapacity = 64)

    override val events: SharedFlow<AppKitEvent> = mutableEvents.asSharedFlow()

    val calls = mutableListOf<String>()
    var nextRequestId = 41L
    var failRequestWith: Throwable? = null
    var redirect: String? = null

    data class Sent(val method: String, val paramsJson: String, val chainId: String)

    val sent = mutableListOf<Sent>()

    fun emit(event: AppKitEvent) {
        check(mutableEvents.tryEmit(event)) { "The event buffer is full" }
    }

    override fun attachDelegate() {
        calls += "delegate"
    }

    override fun accountAddress(): String? = account

    override fun request(
        method: String,
        paramsJson: String,
        chainId: String,
        onSent: (Long) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        calls += "request"
        sent += Sent(method, paramsJson, chainId)
        val failure = failRequestWith
        if (failure != null) onError(failure) else onSent(nextRequestId++)
    }

    override fun activeSessionRedirect(): String? = redirect

    override fun disconnect(onDone: () -> Unit, onError: (Throwable) -> Unit) {
        calls += "disconnect"
        account = null
        onDone()
    }

    override suspend fun extendSession() {
        calls += "extend"
    }
}
