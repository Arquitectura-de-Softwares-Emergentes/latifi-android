package com.latifi.wallet.domain.tx

import org.junit.Assert.assertEquals
import org.junit.Test

class FailureActionsTest {
    @Test
    fun declinedInTheWalletOffersRetry() = assertEquals(FailurePrimary.Retry, FailureActions.primaryFor(FailReason.Declined))

    @Test
    fun aSignatureTimeoutOffersReconnectWallet() =
        assertEquals(FailurePrimary.ReconnectWallet, FailureActions.primaryFor(FailReason.Timeout))

    @Test
    fun aLostWalletSessionOffersReconnectWallet() =
        assertEquals(FailurePrimary.ReconnectWallet, FailureActions.primaryFor(FailReason.Session))

    @Test
    fun aLowNetworkFeeBalanceOffersCopyWalletAddress() =
        assertEquals(FailurePrimary.CopyWalletAddress, FailureActions.primaryFor(FailReason.Fee))

    @Test
    fun notEnoughTusdOffersGetTestCoins() = assertEquals(FailurePrimary.GetTestCoins, FailureActions.primaryFor(FailReason.Tokens))

    @Test
    fun aRevertOnChainOffersRetry() = assertEquals(FailurePrimary.Retry, FailureActions.primaryFor(FailReason.Reverted))

    @Test
    fun aRequestThatIsNoLongerOpenOffersBackToRequests() =
        assertEquals(FailurePrimary.BackToRequests, FailureActions.primaryFor(FailReason.NotOpen))

    @Test
    fun anUnreachableNetworkOffersRetry() = assertEquals(FailurePrimary.Retry, FailureActions.primaryFor(FailReason.Network))

    @Test
    fun everyReasonHasAPrimary() {
        FailReason.entries.forEach { FailureActions.primaryFor(it) }
    }
}
