package com.latifi.wallet.data.wallet

import android.util.Log
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.client.models.request.Request
import com.reown.appkit.client.models.request.SentRequestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * The AppKit 1.6.17 implementation of [AppKitClient]. Every call below is a shape that compiled
 * against the 1.6.17 artifacts (the public documentation snippets are stale, research Pitfall 4).
 * It never logs an address, a payload, a hash or an error message from the wallet, only class names.
 *
 * Instrumented tests cannot start AppKit without a real project id, so this class is covered by the
 * adapter tests of the gateway and by the device checklist.
 */
class RealAppKitClient : AppKitClient {
    private val mutableEvents = MutableSharedFlow<AppKitEvent>(extraBufferCapacity = EVENT_BUFFER)

    override val events: SharedFlow<AppKitEvent> = mutableEvents.asSharedFlow()

    private val delegate = object : AppKit.ModalDelegate {
        override fun onSessionApproved(approvedSession: Modal.Model.ApprovedSession) {
            // The account list holds CAIP-10 ids such as eip155:80002:0x..., the address is the last part.
            val address = (approvedSession as? Modal.Model.ApprovedSession.WalletConnectSession)
                ?.accounts?.firstOrNull()?.substringAfterLast(':')
            mutableEvents.tryEmit(AppKitEvent.SessionApproved(address))
        }

        override fun onSessionRejected(rejectedSession: Modal.Model.RejectedSession) {
            mutableEvents.tryEmit(AppKitEvent.SessionRejected)
        }

        override fun onSessionUpdate(updatedSession: Modal.Model.UpdatedSession) = Unit

        override fun onSessionEvent(sessionEvent: Modal.Model.SessionEvent) = Unit

        override fun onSessionExtend(session: Modal.Model.Session) = Unit

        override fun onSessionDelete(deletedSession: Modal.Model.DeletedSession) {
            mutableEvents.tryEmit(AppKitEvent.SessionDeleted)
        }

        override fun onSessionRequestResponse(response: Modal.Model.SessionRequestResponse) {
            when (val result = response.result) {
                is Modal.Model.JsonRpcResponse.JsonRpcResult ->
                    mutableEvents.tryEmit(AppKitEvent.ResponseResult(result.id, result.result as? String ?: ""))
                is Modal.Model.JsonRpcResponse.JsonRpcError ->
                    mutableEvents.tryEmit(AppKitEvent.ResponseError(result.id, result.code, result.message))
            }
        }

        override fun onProposalExpired(proposal: Modal.Model.ExpiredProposal) {
            mutableEvents.tryEmit(AppKitEvent.ConnectionError)
        }

        override fun onRequestExpired(request: Modal.Model.ExpiredRequest) {
            mutableEvents.tryEmit(AppKitEvent.RequestExpired(request.id))
        }

        override fun onConnectionStateChange(state: Modal.Model.ConnectionState) = Unit

        override fun onError(error: Modal.Model.Error) {
            Log.w(TAG, "AppKit reported an error: ${error.throwable.javaClass.simpleName}")
            mutableEvents.tryEmit(AppKitEvent.ConnectionError)
        }
    }

    override fun attachDelegate() {
        try {
            AppKit.setDelegate(delegate)
        } catch (e: IllegalStateException) {
            // The SDK is not ready yet. The bootstrap calls this again when initialization succeeds.
            Log.i(TAG, "AppKit delegate not attached yet: ${e.javaClass.simpleName}")
        }
    }

    override fun accountAddress(): String? =
        try {
            AppKit.getAccount()?.address
        } catch (e: IllegalStateException) {
            null
        }

    override fun request(
        method: String,
        paramsJson: String,
        chainId: String,
        onSent: (Long) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        try {
            AppKit.request(
                Request(method = method, params = paramsJson, chainId = chainId),
                onSuccess = { sent: SentRequestResult ->
                    if (sent is SentRequestResult.WalletConnect) {
                        onSent(sent.requestId)
                    } else {
                        onError(IllegalStateException("The wallet type is not supported"))
                    }
                },
                onError = onError,
            )
        } catch (e: IllegalStateException) {
            onError(e)
        }
    }

    override fun activeSessionRedirect(): String? =
        try {
            AppKit.getActiveSession()?.redirect
        } catch (e: IllegalStateException) {
            null
        }

    override fun disconnect(onDone: () -> Unit, onError: (Throwable) -> Unit) {
        try {
            AppKit.disconnect(onSuccess = onDone, onError = onError)
        } catch (e: IllegalStateException) {
            onError(e)
        }
    }

    override suspend fun extendSession() {
        // The blocking lookups run off the main thread. The renewal itself reports through callbacks.
        val hasSession = withContext(Dispatchers.IO) { accountAddress() != null }
        if (!hasSession) return
        try {
            AppKit.extend(
                onSuccess = { Log.i(TAG, "Wallet session extended") },
                onError = { error -> Log.w(TAG, "Wallet session not extended: ${error.throwable.javaClass.simpleName}") },
            )
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Wallet session not extended: ${e.javaClass.simpleName}")
        }
    }

    private companion object {
        const val TAG = "RealAppKitClient"
        const val EVENT_BUFFER = 64
    }
}
