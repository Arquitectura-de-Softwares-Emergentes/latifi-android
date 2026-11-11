package com.latifi.wallet.data.wallet

import com.latifi.wallet.domain.wallet.RequestHandle
import com.latifi.wallet.domain.wallet.WalletFailure
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletOutcome
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.domain.wallet.WalletResponse
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.support.FakeAppKitClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTH-01, AUTH-02, AUTH-04: the gateway over a fake SDK adapter, on virtual time. It proves the
 * restore after a process kill, the state events, the request ids, the classification of answers
 * and that the wallet app is opened without a second request.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppKitWalletGatewayTest {
    private val mixedCase = "0x52908400098527886E0F7030069857D2E4169EE7"
    private val lower = mixedCase.lowercase()
    private val signature = "0x" + "ab".repeat(65)
    private val txHash = "0x" + "cd".repeat(32)

    private class Rig(scope: TestScope, account: String? = null, grace: Long = 1_500L) {
        val client = FakeAppKitClient(account)
        val bridge = AppKitPickerBridge()
        val opened = mutableListOf<String>()
        val gateway: WalletGateway = AppKitWalletGateway(
            client = client,
            bridge = bridge,
            scope = scope.backgroundScope,
            io = StandardTestDispatcher(scope.testScheduler),
            launcher = UriLauncher { opened += it },
            pickerCloseGraceMillis = grace,
        )
    }

    @Test
    fun aSessionFoundAtConstructionRestoresConnectedWithALowercaseAddress() = runTest {
        val rig = Rig(this, account = mixedCase)

        runCurrent()

        assertEquals(WalletState.Connected(lower), rig.gateway.state.value)
    }

    @Test
    fun noStoredSessionStartsDisconnected() = runTest {
        val rig = Rig(this, account = null)

        runCurrent()

        assertEquals(WalletState.Disconnected, rig.gateway.state.value)
    }

    @Test
    fun anApprovedSessionConnectsAndADeletedSessionDisconnects() = runTest {
        val rig = Rig(this)
        runCurrent()

        rig.client.emit(AppKitEvent.SessionApproved(mixedCase))
        runCurrent()
        assertEquals(WalletState.Connected(lower), rig.gateway.state.value)

        rig.client.emit(AppKitEvent.SessionDeleted)
        runCurrent()
        assertEquals(WalletState.Disconnected, rig.gateway.state.value)
    }

    @Test
    fun anApprovalWithoutAnAddressReadsTheStoredAccount() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.client.account = mixedCase

        rig.client.emit(AppKitEvent.SessionApproved(null))
        runCurrent()

        assertEquals(WalletState.Connected(lower), rig.gateway.state.value)
    }

    @Test
    fun anApprovalWithNoReadableAddressFails() = runTest {
        val rig = Rig(this)
        runCurrent()

        rig.client.emit(AppKitEvent.SessionApproved("not an address"))
        runCurrent()

        assertEquals(WalletState.Failed(WalletFailure.Unknown), rig.gateway.state.value)
    }

    @Test
    fun openingThePickerSetsConnectingAndAsksTheHost() = runTest {
        val rig = Rig(this)
        runCurrent()
        var requests = 0
        backgroundScope.launch { rig.bridge.requests.collect { requests += 1 } }
        runCurrent()

        rig.gateway.openPicker()
        runCurrent()

        assertEquals(WalletState.Connecting, rig.gateway.state.value)
        assertEquals(1, requests)
    }

    @Test
    fun aConnectionErrorOrARejectionFailsOnlyWhileConnecting() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.gateway.openPicker()
        rig.client.emit(AppKitEvent.ConnectionError)
        runCurrent()
        assertEquals(WalletState.Failed(WalletFailure.Unknown), rig.gateway.state.value)

        rig.gateway.openPicker()
        rig.client.emit(AppKitEvent.SessionRejected)
        runCurrent()
        assertEquals(WalletState.Failed(WalletFailure.Declined), rig.gateway.state.value)

        rig.client.emit(AppKitEvent.SessionApproved(lower))
        runCurrent()
        rig.client.emit(AppKitEvent.ConnectionError)
        runCurrent()
        assertEquals(WalletState.Connected(lower), rig.gateway.state.value)
    }

    @Test
    fun aPickerThatFailsToOpenFailsTheConnection() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.gateway.openPicker()

        rig.bridge.openFailed()
        runCurrent()

        assertEquals(WalletState.Failed(WalletFailure.Unknown), rig.gateway.state.value)
    }

    @Test
    fun leavingThePickerWithoutAWalletStopsTheWaitingAfterAShortGrace() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.gateway.openPicker()

        rig.bridge.pickerClosed()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(WalletState.Connecting, rig.gateway.state.value)

        advanceTimeBy(600)
        runCurrent()
        assertEquals(WalletState.Disconnected, rig.gateway.state.value)
    }

    @Test
    fun anApprovalThatLandsJustAfterThePickerClosesStaysConnected() = runTest {
        val rig = Rig(this)
        runCurrent()
        rig.gateway.openPicker()
        rig.bridge.pickerClosed()
        runCurrent()

        rig.client.emit(AppKitEvent.SessionApproved(lower))
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(WalletState.Connected(lower), rig.gateway.state.value)
    }

    @Test
    fun sendRequestReturnsTheRequestIdThatTheClientHandedOver() = runTest {
        val rig = Rig(this, account = lower)
        runCurrent()

        val first = rig.gateway.sendRequest(WalletRequest.PersonalSign("hello", lower))
        val second = rig.gateway.sendRequest(WalletRequest.PersonalSign("again", lower))

        assertEquals(RequestHandle(41), first.getOrNull())
        assertEquals(RequestHandle(42), second.getOrNull())
    }

    @Test
    fun sendRequestUsesTheMethodsAndTheAmoyChain() = runTest {
        val rig = Rig(this, account = lower)
        runCurrent()

        rig.gateway.sendRequest(WalletRequest.PersonalSign("hello", lower))
        rig.gateway.sendRequest(WalletRequest.SendTransaction(lower, "0x" + "11".repeat(20), "0xabcdef01", "0x0"))

        assertEquals(listOf("personal_sign", "eth_sendTransaction"), rig.client.sent.map { it.method })
        assertTrue(rig.client.sent.all { it.chainId == "eip155:80002" })
    }

    @Test
    fun aFailedSendIsAFailureResultNotACrash() = runTest {
        val rig = Rig(this, account = lower)
        rig.client.failRequestWith = IllegalStateException("no session")
        runCurrent()

        val result = rig.gateway.sendRequest(WalletRequest.PersonalSign("hello", lower))

        assertTrue(result.isFailure)
    }

    private fun TestScope.collectResponses(rig: Rig): MutableList<WalletResponse> {
        val seen = mutableListOf<WalletResponse>()
        backgroundScope.launch { rig.gateway.responses.collect { seen += it } }
        runCurrent()
        return seen
    }

    @Test
    fun aResultOf132CharactersIsASignatureAndOneOf66IsATransactionHash() = runTest {
        val rig = Rig(this)
        val seen = collectResponses(rig)

        rig.client.emit(AppKitEvent.ResponseResult(7, signature))
        rig.client.emit(AppKitEvent.ResponseResult(8, txHash))
        runCurrent()

        assertEquals(132, signature.length)
        assertEquals(66, txHash.length)
        assertEquals(
            listOf(
                WalletResponse(7, WalletOutcome.Signature(signature)),
                WalletResponse(8, WalletOutcome.TransactionHash(txHash)),
            ),
            seen,
        )
    }

    @Test
    fun aResultOfAnyOtherShapeIsAFailure() = runTest {
        val rig = Rig(this)
        val seen = collectResponses(rig)

        rig.client.emit(AppKitEvent.ResponseResult(7, "0x1234"))
        rig.client.emit(AppKitEvent.ResponseResult(8, ""))
        rig.client.emit(AppKitEvent.ResponseResult(9, "0x" + "zz".repeat(65)))
        runCurrent()

        assertEquals(3, seen.size)
        assertTrue(seen.all { it.outcome is WalletOutcome.Failed })
        assertEquals(listOf(7L, 8L, 9L), seen.map { it.requestId })
    }

    @Test
    fun codes4001And5000AreRejected() = runTest {
        val rig = Rig(this)
        val seen = collectResponses(rig)

        rig.client.emit(AppKitEvent.ResponseError(1, 4001, "User rejected"))
        rig.client.emit(AppKitEvent.ResponseError(2, 5000, "User rejected"))
        runCurrent()

        assertEquals(listOf(WalletOutcome.Rejected, WalletOutcome.Rejected), seen.map { it.outcome })
        assertEquals(listOf(1L, 2L), seen.map { it.requestId })
    }

    @Test
    fun aSessionErrorIsSessionLostAndAnyOtherCodeKeepsItsCodeAndMessage() = runTest {
        val rig = Rig(this)
        val seen = collectResponses(rig)

        rig.client.emit(AppKitEvent.ResponseError(1, 6000, "disconnected"))
        rig.client.emit(AppKitEvent.ResponseError(2, 8000, "expired"))
        rig.client.emit(AppKitEvent.ResponseError(3, -32000, "insufficient funds"))
        runCurrent()

        assertEquals(WalletOutcome.SessionLost, seen[0].outcome)
        assertEquals(WalletOutcome.SessionLost, seen[1].outcome)
        assertEquals(WalletOutcome.Failed(-32000, "insufficient funds"), seen[2].outcome)
    }

    @Test
    fun anExpiredRequestIsAFailureForThatRequestId() = runTest {
        val rig = Rig(this)
        val seen = collectResponses(rig)

        rig.client.emit(AppKitEvent.RequestExpired(12))
        runCurrent()

        assertEquals(12L, seen.single().requestId)
        assertTrue(seen.single().outcome is WalletOutcome.Failed)
    }

    @Test
    fun aLateAnswerReachesEveryCollectorThatExistsWhenItArrives() = runTest {
        val rig = Rig(this)
        val coordinator = collectResponses(rig)
        val second = collectResponses(rig)

        // The answer arrives long after the request was made, as it does after a restart.
        advanceTimeBy(4 * 60_000)
        rig.client.emit(AppKitEvent.ResponseResult(7, txHash))
        runCurrent()

        assertEquals(1, coordinator.size)
        assertEquals(coordinator, second)
    }

    @Test
    fun openingTheWalletAppFollowsTheSessionLinkAndNeverSendsANewRequest() = runTest {
        val rig = Rig(this, account = lower)
        rig.client.redirect = "metamask://wc"
        runCurrent()

        rig.gateway.openWalletApp()
        runCurrent()

        assertEquals(listOf("metamask://wc"), rig.opened)
        assertFalse(rig.client.calls.contains("request"))
    }

    @Test
    fun openingTheWalletAppWithoutALinkDoesNothing() = runTest {
        val rig = Rig(this, account = lower)
        runCurrent()

        rig.gateway.openWalletApp()
        runCurrent()

        assertTrue(rig.opened.isEmpty())
        assertFalse(rig.client.calls.contains("request"))
    }

    @Test
    fun aLinkThatCouldStartAnotherComponentIsNeverOpened() = runTest {
        val rig = Rig(this, account = lower)
        runCurrent()

        for (link in listOf("intent://x#Intent;end", "file:///data/x", "content://x/y", "javascript:alert(1)", "no scheme", "")) {
            rig.client.redirect = link
            rig.gateway.openWalletApp()
            runCurrent()
        }

        assertTrue(rig.opened.isEmpty())
    }

    @Test
    fun disconnectCallsTheClientAndSetsDisconnected() = runTest {
        val rig = Rig(this, account = lower)
        runCurrent()
        assertEquals(WalletState.Connected(lower), rig.gateway.state.value)

        rig.gateway.disconnect()

        assertTrue(rig.client.calls.contains("disconnect"))
        assertEquals(WalletState.Disconnected, rig.gateway.state.value)
    }

    @Test
    fun theGatewayInterfaceHasNoMethodThatCouldHoldOrExportAKey() {
        val names = WalletGateway::class.java.methods.map { it.name.lowercase() }

        assertTrue(names.none { "key" in it || "mnemonic" in it || "seed" in it || "secret" in it })
    }

    @Test
    fun theUnconfiguredGatewayNeverConnectsOrSends() = runTest {
        val gateway = UnconfiguredWalletGateway()

        gateway.openPicker()
        val result = gateway.sendRequest(WalletRequest.PersonalSign("hello", lower))

        assertEquals(WalletState.NotConfigured, gateway.state.value)
        assertTrue(result.exceptionOrNull() is WalletNotConfiguredException)
    }
}
