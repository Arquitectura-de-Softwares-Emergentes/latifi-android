package com.latifi.wallet.domain.tx

import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.Erc20Abi
import com.latifi.wallet.data.chain.LoanBookAbi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class TxPlansTest {
    private val loanId = BigInteger.valueOf(11)
    private val amountDue = BigInteger.valueOf(350_000_000)

    @Test
    fun mintIsOneStepToTheTokenForOneThousandTokens() {
        val step = TxPlans.mint(balanceBefore = BigInteger.valueOf(5))

        assertEquals(TxKind.Mint, step.kind)
        assertEquals(Deployment.STABLECOIN_ADDRESS, step.to)
        assertEquals(Erc20Abi.mint(BigInteger.valueOf(1_000_000_000)), step.data)
        assertEquals(BigInteger.valueOf(5), step.baseline)
        assertEquals("0x0", step.value)
    }

    @Test
    fun requestLoanIsOneStepToTheLoanBookWithNoApproval() {
        val calldata = LoanBookAbi.requestLoan(BigInteger.valueOf(100_000_000), 1000, 30, 2)

        val step = TxPlans.requestLoan(calldata)

        assertEquals(TxKind.RequestLoan, step.kind)
        assertEquals(Deployment.LOANBOOK_ADDRESS, step.to)
        assertEquals(calldata, step.data)
        assertTrue(calldata.startsWith("0x430a09d0"))
    }

    @Test
    fun payWithEnoughAllowanceIsASingleRepay() {
        val steps = TxPlans.pay(loanId, amountDue, allowance = amountDue, amountRepaid = BigInteger.ZERO)

        assertEquals(listOf(TxKind.Repay), steps.map { it.kind })
        assertEquals(LoanBookAbi.repay(loanId, amountDue), steps.single().data)
    }

    @Test
    fun payWithAShortAllowanceApprovesExactlyTheAmountThenRepays() {
        val steps = TxPlans.pay(loanId, amountDue, allowance = amountDue.subtract(BigInteger.ONE), amountRepaid = BigInteger.TEN)

        assertEquals(listOf(TxKind.Approve, TxKind.Repay), steps.map { it.kind })
        val approve = steps[0]
        assertEquals(Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, amountDue), approve.data)
        assertEquals(amountDue, approve.amount)
        assertEquals(amountDue.subtract(BigInteger.ONE), approve.baseline)
        val repay = steps[1]
        assertEquals(amountDue, repay.amount)
        assertEquals(BigInteger.TEN, repay.baseline)
        assertEquals(loanId, repay.loanId)
    }

    @Test
    fun theRepayAmountIsTheUnpaidAmountOfTheCurrentInstallment() {
        val unpaid = BigInteger.valueOf(123_456)

        val repay = TxPlans.pay(loanId, unpaid, allowance = unpaid, amountRepaid = BigInteger.ZERO).single()

        assertEquals(LoanBookAbi.repay(loanId, unpaid), repay.data)
    }

    @Test
    fun fundWithEnoughAllowanceIsASingleFund() {
        val principal = BigInteger.valueOf(200_000_000)

        val steps = TxPlans.fund(loanId, principal, allowance = principal.add(BigInteger.ONE))

        assertEquals(listOf(TxKind.Fund), steps.map { it.kind })
        assertEquals(LoanBookAbi.fund(loanId), steps.single().data)
    }

    @Test
    fun fundWithAShortAllowanceApprovesExactlyThePrincipalThenFunds() {
        val principal = BigInteger.valueOf(200_000_000)

        val steps = TxPlans.fund(loanId, principal, allowance = BigInteger.ZERO)

        assertEquals(listOf(TxKind.Approve, TxKind.Fund), steps.map { it.kind })
        assertEquals(Erc20Abi.approve(Deployment.LOANBOOK_ADDRESS, principal), steps[0].data)
        assertEquals(principal, steps[0].amount)
    }

    @Test
    fun everyTargetIsAConstantAndTheSpenderIsTheLoanBook() {
        val all = TxPlans.pay(loanId, amountDue, BigInteger.ZERO, BigInteger.ZERO) +
            TxPlans.fund(loanId, amountDue, BigInteger.ZERO) +
            TxPlans.mint(BigInteger.ZERO) +
            TxPlans.requestLoan(LoanBookAbi.requestLoan(amountDue, 1000, 30, 1))

        for (step in all) {
            val expected = when (step.kind) {
                TxKind.Mint, TxKind.Approve -> Deployment.STABLECOIN_ADDRESS
                else -> Deployment.LOANBOOK_ADDRESS
            }
            assertEquals(step.kind.name, expected, step.to)
        }
        val spender = Deployment.LOANBOOK_ADDRESS.removePrefix("0x")
        all.filter { it.kind == TxKind.Approve }.forEach { assertTrue(it.data.contains(spender)) }
    }

    @Test
    fun anApprovalAboveWhatTheContractAcceptsIsRefused() {
        val tooBig = BigInteger.ONE.shiftLeft(128)

        val failure = runCatching { TxPlans.fund(loanId, tooBig, BigInteger.ZERO) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertNull(TxPlans.pay(loanId, amountDue, amountDue, BigInteger.ZERO).firstOrNull { it.kind == TxKind.Approve })
    }
}
