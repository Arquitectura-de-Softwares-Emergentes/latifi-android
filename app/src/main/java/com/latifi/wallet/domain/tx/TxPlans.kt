package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.Erc20Abi
import com.latifi.wallet.data.chain.LoanBookAbi
import java.math.BigInteger

/**
 * The transactions each flow asks the wallet to sign, built in one place. Every `to` and every
 * spender is a constant of [Deployment], never a value an API returned. An approval is for the
 * exact amount the next step moves and for nothing more: [pay] and [fund] return the approve step
 * only when the allowance is short, and that step carries the needed amount to the unit.
 *
 * Each step carries `amount` and `baseline` so a restart can reconcile it with the chain: the
 * balance before a mint, the allowance before an approval, and the repaid amount before a repay.
 */
object TxPlans {
    /** One test coin request: `mint(1000000000)` on the stablecoin. [balanceBefore] is the reconcile baseline. */
    fun mint(balanceBefore: BigInteger): TxStep {
        val amount = BigInteger.valueOf(Deployment.MINT_PER_CALL_BASE_UNITS)
        return TxStep(
            kind = TxKind.Mint,
            to = Deployment.STABLECOIN_ADDRESS,
            data = Erc20Abi.mint(amount),
            amount = amount,
            baseline = balanceBefore,
        )
    }

    /** One `requestLoan` step with the calldata the calculator built. It moves no tokens, so there is no approval. */
    fun requestLoan(calldata: String): TxStep = TxStep(
        kind = TxKind.RequestLoan,
        to = Deployment.LOANBOOK_ADDRESS,
        data = calldata,
    )

    /**
     * Pays the current installment: [amountDue] is the unpaid amount `nextInstallment` reported,
     * never more, because `repay` reverts on an overpayment. [amountRepaid] is the baseline.
     */
    fun pay(loanId: BigInteger, amountDue: BigInteger, allowance: BigInteger, amountRepaid: BigInteger): List<TxStep> {
        val repay = TxStep(
            kind = TxKind.Repay,
            to = Deployment.LOANBOOK_ADDRESS,
            data = LoanBookAbi.repay(loanId, amountDue),
            loanId = loanId,
            amount = amountDue,
            baseline = amountRepaid,
        )
        return withApproval(amountDue, allowance, repay)
    }

    /** Funds a request: the approval is for exactly the [principal]. */
    fun fund(loanId: BigInteger, principal: BigInteger, allowance: BigInteger): List<TxStep> {
        val fund = TxStep(
            kind = TxKind.Fund,
            to = Deployment.LOANBOOK_ADDRESS,
            data = LoanBookAbi.fund(loanId),
            loanId = loanId,
            amount = principal,
        )
        return withApproval(principal, allowance, fund)
    }

    private fun withApproval(needed: BigInteger, allowance: BigInteger, action: TxStep): List<TxStep> {
        if (allowance >= needed) return listOf(action)
        val approve = TxStep(
            kind = TxKind.Approve,
            to = Deployment.STABLECOIN_ADDRESS,
            data = Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, needed),
            loanId = action.loanId,
            amount = needed,
            baseline = allowance,
        )
        return listOf(approve, action)
    }
}
