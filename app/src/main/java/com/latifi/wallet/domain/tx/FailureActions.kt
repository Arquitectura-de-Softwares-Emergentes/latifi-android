package com.latifi.wallet.domain.tx

/** The main action of the Failed state. Some reasons swap Retry for the action that fixes them. */
enum class FailurePrimary { Retry, ReconnectWallet, CopyWalletAddress, GetTestCoins, BackToRequests }

object FailureActions {
    fun primaryFor(reason: FailReason): FailurePrimary = when (reason) {
        FailReason.Declined -> FailurePrimary.Retry
        FailReason.Session, FailReason.Timeout -> FailurePrimary.ReconnectWallet
        FailReason.Fee -> FailurePrimary.CopyWalletAddress
        FailReason.Tokens -> FailurePrimary.GetTestCoins
        FailReason.Reverted -> FailurePrimary.Retry
        FailReason.NotOpen -> FailurePrimary.BackToRequests
        FailReason.Network -> FailurePrimary.Retry
    }
}
