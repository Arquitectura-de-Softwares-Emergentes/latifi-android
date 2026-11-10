package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.chain.ChainFailure
import com.latifi.wallet.data.chain.RevertDecoder
import com.latifi.wallet.domain.chain.CallOutcome
import com.latifi.wallet.domain.chain.RpcClient

sealed interface PreflightResult {
    /** Nothing in the way. The wallet can be asked. */
    data object Ok : PreflightResult

    /** The allowance is too low. This is not a failure: the flow runs the approve step first. */
    data object NeedsAllowance : PreflightResult

    /** [refreshInstallment] asks the flow to reload the next installment before a retry. */
    data class Fail(val reason: FailReason, val refreshInstallment: Boolean = false) : PreflightResult
}

/**
 * Runs the step as an `eth_call` from the sender before the wallet opens, so a known revert shows
 * its own message instead of a wallet prompt that is certain to fail. The contract and the wallet
 * still enforce every rule: this only saves the person a useless request.
 */
class Preflight(private val rpc: RpcClient) {
    suspend fun check(step: TxStep, from: String): PreflightResult {
        val outcome = rpc.call(step.to, step.data, from).getOrElse { return PreflightResult.Fail(FailReason.Network) }
        when (outcome) {
            is CallOutcome.Reverted -> return map(RevertDecoder.decode(outcome.data))
            is CallOutcome.Success -> Unit
        }
        val balance = rpc.getBalance(from).getOrElse { return PreflightResult.Fail(FailReason.Network) }
        // No native coin means no network fee, and the wallet would refuse to send.
        return if (balance.signum() <= 0) PreflightResult.Fail(FailReason.Fee) else PreflightResult.Ok
    }

    private fun map(failure: ChainFailure): PreflightResult = when (failure) {
        ChainFailure.NotOpen -> PreflightResult.Fail(FailReason.NotOpen)
        is ChainFailure.InsufficientBalance -> PreflightResult.Fail(FailReason.Tokens)
        is ChainFailure.InsufficientAllowance -> PreflightResult.NeedsAllowance
        is ChainFailure.Overpayment -> PreflightResult.Fail(FailReason.Reverted, refreshInstallment = true)
        ChainFailure.SelfFunding,
        ChainFailure.NotActive,
        ChainFailure.NotBorrower,
        ChainFailure.NotFound,
        is ChainFailure.Unknown,
        -> PreflightResult.Fail(FailReason.Reverted)
    }
}
