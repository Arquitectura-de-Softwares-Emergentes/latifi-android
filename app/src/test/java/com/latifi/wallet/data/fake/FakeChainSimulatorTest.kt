package com.latifi.wallet.data.fake

import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.Erc20Abi
import com.latifi.wallet.data.chain.LoanBookAbi
import com.latifi.wallet.data.chain.ReceiptLogParser
import com.latifi.wallet.domain.model.LoanStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.ZoneOffset

class FakeChainSimulatorTest {
    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val loans = FakeLoanRepository(clock)
    private val chain = FakeChainReadRepository { loans.loans }
    private val sim = FakeChainSimulator(chain, loans, clock)

    private val borrower = FakeData.DEMO_ADDRESS
    private val lender = FakeData.LENDER_A
    private val loanBook = Deployment.LOANBOOK_ADDRESS
    private val token = Deployment.STABLECOIN_ADDRESS

    private fun tokens(n: Long) = BigInteger.valueOf(n).multiply(TokenAmount.ONE_TOKEN)

    private fun send(from: String, to: String, data: String) = sim.apply(from, to, data)

    @Test
    fun mintRaisesTheBalanceUpToTheCaps() {
        val before = chain.tokenUnitsOf(lender)

        val ok = send(lender, token, Erc20Abi.mint(tokens(1_000)))

        assertTrue(ok.succeeded)
        assertEquals(before.add(tokens(1_000)), chain.tokenUnitsOf(lender))
        assertFalse(send(lender, token, Erc20Abi.mint(tokens(1_001))).succeeded)
        repeat(9) { assertTrue(send(lender, token, Erc20Abi.mint(tokens(1_000))).succeeded) }
        assertFalse("the 10,000 cap per wallet", send(lender, token, Erc20Abi.mint(tokens(1))).succeeded)
    }

    @Test
    fun approveSetsTheAllowanceOfTheSenderForTheSpender() {
        val result = send(borrower, token, Erc20Abi.approve(loanBook, BigInteger.valueOf(42)))

        assertTrue(result.succeeded)
        assertEquals(BigInteger.valueOf(42), chain.allowanceUnitsOf(borrower, loanBook))
        assertEquals(BigInteger.ZERO, chain.allowanceUnitsOf(lender, loanBook))
    }

    @Test
    fun requestLoanAddsAnOpenLoanWithTheNextIdAndALog() {
        val nextId = loans.loans.maxOf { it.loanId }.add(BigInteger.ONE)

        val result = send(borrower, loanBook, LoanBookAbi.requestLoan(tokens(100), 1000, 30, 2))

        assertTrue(result.succeeded)
        assertEquals(nextId, ReceiptLogParser.loanIdFromRequestReceipt(result.logs))
        val loan = loans.loans.single { it.loanId == nextId }
        assertEquals(LoanStatus.OPEN, loan.status)
        assertEquals(borrower, loan.borrower)
        assertEquals(tokens(100), loan.principal)
        assertEquals(1000, loan.interestRateBps)
        assertEquals(2, loan.installments)
    }

    @Test
    fun requestLoanRefusesTermsOutsideTheContractLimits() {
        assertFalse(send(borrower, loanBook, LoanBookAbi.requestLoan(tokens(100), 5001, 30, 1)).succeeded)
        assertFalse(send(borrower, loanBook, LoanBookAbi.requestLoan(tokens(100), 1000, 366, 1)).succeeded)
        assertFalse(send(borrower, loanBook, LoanBookAbi.requestLoan(tokens(100), 1000, 30, 7)).succeeded)
    }

    @Test
    fun fundMakesTheLoanActiveMovesTheTokensAndUsesTheAllowance() {
        val id = open(100)
        send(lender, token, Erc20Abi.mint(tokens(500)))
        val lenderBefore = chain.tokenUnitsOf(lender)
        val borrowerBefore = chain.tokenUnitsOf(borrower)
        send(lender, token, Erc20Abi.approve(loanBook, tokens(100)))

        val result = send(lender, loanBook, LoanBookAbi.fund(id))

        assertTrue(result.succeeded)
        val loan = loans.loans.single { it.loanId == id }
        assertEquals(LoanStatus.ACTIVE, loan.status)
        assertEquals(lender, loan.lender)
        assertEquals(FakeData.EPOCH, loan.fundedAt)
        assertEquals(lenderBefore.subtract(tokens(100)), chain.tokenUnitsOf(lender))
        assertEquals(borrowerBefore.add(tokens(100)), chain.tokenUnitsOf(borrower))
        assertEquals(BigInteger.ZERO, chain.allowanceUnitsOf(lender, loanBook))
    }

    @Test
    fun fundRevertsForAnOwnRequestAnAbsentAllowanceAndAClosedLoan() {
        val id = open(100)
        send(borrower, token, Erc20Abi.approve(loanBook, tokens(100)))
        assertFalse("own request", send(borrower, loanBook, LoanBookAbi.fund(id)).succeeded)
        send(lender, token, Erc20Abi.mint(tokens(500)))
        assertFalse("no allowance", send(lender, loanBook, LoanBookAbi.fund(id)).succeeded)
        send(lender, token, Erc20Abi.approve(loanBook, tokens(100)))
        assertTrue(send(lender, loanBook, LoanBookAbi.fund(id)).succeeded)
        assertFalse("already funded", send(lender, loanBook, LoanBookAbi.fund(id)).succeeded)
    }

    @Test
    fun repayAddsToTheRepaidAmountAndAdvancesTheInstallments() {
        val id = fundedLoan(principal = 100, installments = 2)
        val first = LoanMath.installmentAmounts(loans.loans.single { it.loanId == id }.totalDue, 2)[0]
        send(borrower, token, Erc20Abi.approve(loanBook, first))

        val result = send(borrower, loanBook, LoanBookAbi.repay(id, first))

        assertTrue(result.succeeded)
        val loan = loans.loans.single { it.loanId == id }
        assertEquals(first, loan.amountRepaid)
        assertEquals(1, loan.installmentsPaid)
        assertEquals(LoanStatus.ACTIVE, loan.status)
        assertNotNull(loan.nextDueDate)
    }

    @Test
    fun repayRevertsOnAnOverpaymentAndForAnotherCaller() {
        val id = fundedLoan(principal = 100, installments = 2)
        val loan = loans.loans.single { it.loanId == id }
        val first = LoanMath.installmentAmounts(loan.totalDue, 2)[0]
        send(borrower, token, Erc20Abi.mint(tokens(1_000)))
        send(borrower, token, Erc20Abi.approve(loanBook, loan.totalDue))

        assertFalse(send(borrower, loanBook, LoanBookAbi.repay(id, first.add(BigInteger.ONE))).succeeded)
        assertFalse(send(lender, loanBook, LoanBookAbi.repay(id, first)).succeeded)
        assertEquals(BigInteger.ZERO, loans.loans.single { it.loanId == id }.amountRepaid)
    }

    @Test
    fun aFullCycleEndsRepaidAtTheExactTotalDue() {
        val id = open(100, rateBps = 1000, termDays = 30, installments = 2)
        send(lender, token, Erc20Abi.mint(tokens(500)))
        send(lender, token, Erc20Abi.approve(loanBook, tokens(100)))
        assertTrue(send(lender, loanBook, LoanBookAbi.fund(id)).succeeded)
        val totalDue = LoanMath.totalDue(tokens(100), 1000, 30)
        // The borrower needs the interest on top of the principal it received.
        send(borrower, token, Erc20Abi.mint(tokens(10)))
        val (a, b) = LoanMath.installmentAmounts(totalDue, 2)

        send(borrower, token, Erc20Abi.approve(loanBook, a))
        assertTrue(send(borrower, loanBook, LoanBookAbi.repay(id, a)).succeeded)
        send(borrower, token, Erc20Abi.approve(loanBook, b))
        assertTrue(send(borrower, loanBook, LoanBookAbi.repay(id, b)).succeeded)

        val loan = loans.loans.single { it.loanId == id }
        assertEquals(LoanStatus.REPAID, loan.status)
        assertEquals(totalDue, loan.amountRepaid)
        assertEquals(2, loan.installmentsPaid)
        assertEquals(null, loan.nextDueDate)
    }

    @Test
    fun anUnknownCallOrTargetReverts() {
        assertFalse(send(borrower, loanBook, "0xdeadbeef").succeeded)
        assertFalse(send(borrower, token, LoanBookAbi.fund(BigInteger.ONE)).succeeded)
        assertFalse(send(borrower, loanBook, "0x").succeeded)
    }

    private fun open(
        principal: Long,
        rateBps: Int = 1000,
        termDays: Int = 30,
        installments: Int = 1,
    ): BigInteger {
        val before = loans.loans.maxOf { it.loanId }
        send(borrower, loanBook, LoanBookAbi.requestLoan(tokens(principal), rateBps, termDays, installments))
        return before.add(BigInteger.ONE)
    }

    private fun fundedLoan(principal: Long, installments: Int): BigInteger {
        val id = open(principal, installments = installments)
        send(lender, token, Erc20Abi.mint(tokens(500)))
        send(lender, token, Erc20Abi.approve(loanBook, tokens(principal)))
        assertTrue(send(lender, loanBook, LoanBookAbi.fund(id)).succeeded)
        return id
    }
}
