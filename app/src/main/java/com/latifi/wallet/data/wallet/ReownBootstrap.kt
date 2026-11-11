package com.latifi.wallet.data.wallet

import android.app.Application
import android.util.Log
import com.latifi.wallet.core.AppConfig
import com.reown.android.Core
import com.reown.android.CoreClient
import com.reown.android.relay.ConnectionType
import com.reown.appkit.client.AppKit
import com.reown.appkit.client.Modal
import com.reown.appkit.utils.EthUtils

/**
 * Starts the Reown core and AppKit, and only when the project id is a real one.
 * With the placeholder id the SDK is never touched, so the app cannot crash at startup.
 * Nothing here logs the project id, an address or a payload. The AppKit delegate is registered
 * by [com.latifi.wallet.AppStartup], in the same `Application.onCreate`, so a late wallet answer finds it.
 */
object ReownBootstrap {
    private const val TAG = "ReownBootstrap"
    const val REDIRECT = "latifi-wallet://request"

    /**
     * [onReady] runs once AppKit reports a successful initialization. The caller passes the
     * delegate registration, so it is tried again in case the SDK was not ready at the first try.
     */
    fun init(application: Application, config: AppConfig, onReady: () -> Unit = {}) {
        if (!config.isWalletConfigured) {
            Log.i(TAG, "Wallet connection is not configured, skipping AppKit initialization")
            return
        }

        val meta = Core.Model.AppMetaData(
            name = "LatiFi Wallet",
            description = "Peer to peer loans on Polygon Amoy",
            url = config.landingUrl,
            icons = listOf(config.appIconUrl),
            redirect = REDIRECT,
        )
        CoreClient.initialize(
            projectId = config.reownProjectId,
            connectionType = ConnectionType.AUTOMATIC,
            application = application,
            metaData = meta,
            onError = { error -> Log.w(TAG, "Core initialization failed: ${error.throwable.javaClass.simpleName}") },
        )
        AppKit.initialize(
            init = Modal.Params.Init(
                core = CoreClient,
                coinbaseEnabled = false,
                enableAnalytics = false,
            ),
            onSuccess = {
                AppKit.setChains(listOf(amoyChain(config)))
                onReady()
            },
            onError = { error -> Log.w(TAG, "AppKit initialization failed: ${error.throwable.javaClass.simpleName}") },
        )
        // The delegate is registered by AppStartup right after this call, through RealAppKitClient.
    }

    private fun amoyChain(config: AppConfig) = Modal.Model.Chain(
        chainName = "Polygon Amoy",
        chainNamespace = "eip155",
        chainReference = "80002",
        requiredMethods = EthUtils.ethRequiredMethods,
        optionalMethods = EthUtils.ethOptionalMethods,
        events = EthUtils.ethEvents,
        token = Modal.Model.Token("POL", "POL", 18),
        rpcUrl = config.amoyRpcUrl,
        blockExplorerUrl = "https://amoy.polygonscan.com",
    )
}
