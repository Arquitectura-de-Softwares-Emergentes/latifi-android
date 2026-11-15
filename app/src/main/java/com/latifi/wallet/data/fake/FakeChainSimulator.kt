package com.latifi.wallet.data.fake

import com.latifi.wallet.core.LoanMath
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.data.chain.Deployment
import com.latifi.wallet.data.chain.LogEntry
import com.latifi.wallet.data.chain.ReceiptLogParser
import com.latifi.wallet.domain.model.BorrowerReputation
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import org.web3j.crypto.Hash
import java.math.BigInteger
import java.time.Clock

/** What the simulated chain did with one transaction. [status] is `0x1` or `0x0` like a receipt. */
data class SimulatedResult(val status: String, val logs: List<LogEntry> = emptyList()) {
    val succeeded: Boolean get() = status == "0x1"
}

/**
 * The Demo mode chain. It reads the calldata of the five calls the app sends (`mint`, `approve`,
 * `requestLoan`, `fund` and `repay`) and applies the same effects the contract and the token have
 * on the fakes: balances, allowances and loans. A call the real contract would revert gives a
 * receipt with status `0x0` and changes nothing. With it the whole loan cycle works with no network.
 *
 * Known limits: it has one block and no time of its own (the clock decides the funding time), a
 * failed transaction costs nothing, and the revert rules are the ones that matter to the flows
 * (open loan, own loan, balance, allowance, overpayment, mint caps, term limits). It exists only
 * for `AppMode.Demo`: the Live branch of `DataModule` never builds it.
 */
class FakeChainSimulator(
    private val chain: FakeChainReadRepository,
    private val loansRepo: FakeLoanRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val minted = mutableMapOf<String, BigInteger>()

    /** Applies [data] sent by [from] to [to] and returns the receipt content. */
    fun apply(from: String, to: String, data: String): SimulatedResult {
        val sender = from.lowercase()
        val target = to.lowercase()
        val body = data.lowercase().removePrefix("0x")
        if (body.length < SELECTOR_CHARS) return REVERT
        val selector = body.substring(0, SELECTOR_CHARS)
        val args = body.substring(SELECTOR_CHARS).chunked(WORD_CHARS)
        return when {
            target == Deployment.STABLECOIN_ADDRESS && selector == MINT -> mint(sender, args)
            target == Deployment.STABLECOIN_ADDRESS && selector == APPROVE -> approve(sender, args)
            target == Deployment.LOANBOOK_ADDRESS && selector == REQUEST_LOAN -> requestLoan(sender, args)
            target == Deployment.LOANBOOK_ADDRESS && selector == FUND -> fund(sender, args)
            target == Deployment.LOANBOOK_ADDRESS && selector == REPAY -> repay(sender, args)
            else -> REVERT
        }
    }

    private fun mint(sender: String, args: List<String>): SimulatedResult {
        val amount = word(args, 0) ?: return REVERT
        val total = (minted[sender] ?: BigInteger.ZERO).add(amount)
        if (amount.signum() <= 0 ||
            amount > BigInteger.valueOf(Deployment.MINT_PER_CALL_BASE_UNITS) ||
            total > BigInteger.valueOf(Deployment.MINT_PER_WALLET_BASE_UNITS)
        ) {
            return REVERT
        }
        minted[sender] = total
        chain.setTokenBalance(sender, TokenAmount(chain.tokenUnitsOf(sender).add(amount)))
        return OK
    }

    private fun approve(sender: String, args: List<String>): SimulatedResult {
        val spender = address(args, 0) ?: return REVERT
        val amount = word(args, 1) ?: return REVERT
        chain.setAllowance(sender, spender, amount)
        return OK
    }

    private fun requestLoan(sender: String, args: List<String>): SimulatedResult {
        val amount = word(args, 0) ?: return REVERT
        val bps = word(args, 1)?.toInt() ?: return REVERT
        val termDays = word(args, 2)?.toInt() ?: return REVERT
        val installments = word(args, 3)?.toInt() ?: return REVERT
        if (amount.signum() <= 0 || bps !in 0..Deployment.MAX_RATE_BPS ||
            termDays !in 1..Deployment.MAX_TERM_DAYS || installments !in 1..Deployment.MAX_INSTALLMENTS
        ) {
            return REVERT
        }
        val current = loansRepo.loans
        val loanId = (current.maxOfOrNull { it.loanId } ?: BigInteger.ZERO).add(BigInteger.ONE)
        val known = current.firstOrNull { it.borrower.equals(sender, ignoreCase = true) }?.borrowerReputation
        val loan = Loan(
            loanId = loanId,
            borrower = sender,
            lender = null,
            principal = amount,
            interestRateBps = bps,
            termDays = termDays,
            installments = installments,
            status = LoanStatus.OPEN,
            amountRepaid = BigInteger.ZERO,
            totalDue = BigInteger.ZERO,
            installmentsPaid = 0,
            requestedAt = clock.instant(),
            fundedAt = null,
            finalDueDate = null,
            nextDueDate = null,
            borrowerReputation = known ?: BorrowerReputation(NEW_SCORE, ReputationLevel.fromScore(NEW_SCORE)),
        )
        loansRepo.loans = current + loan
        val topic = "0x" + loanId.toString(16).padStart(WORD_CHARS, '0')
        return SimulatedResult(
            "0x1",
            listOf(LogEntry(Deployment.LOANBOOK_ADDRESS, listOf(ReceiptLogParser.LOAN_REQUESTED_TOPIC, topic), "0x")),
        )
    }

    private fun fund(sender: String, args: List<String>): SimulatedResult {
        val loan = loanOf(args) ?: return REVERT
        if (loan.status != LoanStatus.OPEN || loan.borrower.equals(sender, ignoreCase = true)) return REVERT
        if (chain.tokenUnitsOf(sender) < loan.principal) return REVERT
        if (chain.allowanceUnitsOf(sender, Deployment.LOANBOOK_ADDRESS) < loan.principal) return REVERT
        val now = clock.instant()
        val totalDue = LoanMath.totalDue(loan.principal, loan.interestRateBps, loan.termDays)
        move(sender, loan.borrower, loan.principal)
        spend(sender, loan.principal)
        replace(
            loan.copy(
                status = LoanStatus.ACTIVE,
                lender = sender,
                fundedAt = now,
                totalDue = totalDue,
                finalDueDate = LoanMath.dueAt(now, loan.termDays, loan.installments, loan.installments),
                nextDueDate = LoanMath.dueAt(now, loan.termDays, 1, loan.installments),
            ),
        )
        return OK
    }

    private fun repay(sender: String, args: List<String>): SimulatedResult {
        val loan = loanOf(args) ?: return REVERT
        val amount = word(args, 1) ?: return REVERT
        val lender = loan.lender ?: return REVERT
        if (loan.status != LoanStatus.ACTIVE || !loan.borrower.equals(sender, ignoreCase = true)) return REVERT
        // The contract reverts on an overpayment of the current installment.
        if (amount.signum() <= 0 || amount > InstallmentSchedule.remainingOnCurrent(loan)) return REVERT
        if (chain.tokenUnitsOf(sender) < amount) return REVERT
        if (chain.allowanceUnitsOf(sender, Deployment.LOANBOOK_ADDRESS) < amount) return REVERT
        move(sender, lender, amount)
        spend(sender, amount)
        val repaid = loan.amountRepaid.add(amount)
        val paid = LoanMath.installmentAmounts(loan.totalDue, loan.installments)
            .runningFold(BigInteger.ZERO, BigInteger::add).drop(1).count { repaid >= it }
        val done = repaid >= loan.totalDue
        val funded = loan.fundedAt
        replace(
            loan.copy(
                amountRepaid = repaid,
                installmentsPaid = paid,
                status = if (done) LoanStatus.REPAID else LoanStatus.ACTIVE,
                nextDueDate = if (done || funded == null) null else LoanMath.dueAt(funded, loan.termDays, paid + 1, loan.installments),
            ),
        )
        return OK
    }

    private fun loanOf(args: List<String>): Loan? {
        val id = word(args, 0) ?: return null
        return loansRepo.loans.firstOrNull { it.loanId == id }
    }

    private fun replace(updated: Loan) {
        loansRepo.loans = loansRepo.loans.map { if (it.loanId == updated.loanId) updated else it }
    }

    private fun move(from: String, to: String, amount: BigInteger) {
        chain.setTokenBalance(from, TokenAmount(chain.tokenUnitsOf(from).subtract(amount)))
        chain.setTokenBalance(to, TokenAmount(chain.tokenUnitsOf(to).add(amount)))
    }

    private fun spend(owner: String, amount: BigInteger) {
        val left = chain.allowanceUnitsOf(owner, Deployment.LOANBOOK_ADDRESS).subtract(amount)
        chain.setAllowance(owner, Deployment.LOANBOOK_ADDRESS, left)
    }

    private fun word(args: List<String>, index: Int): BigInteger? =
        args.getOrNull(index)?.takeIf { it.length == WORD_CHARS }?.let { BigInteger(it, 16) }

    private fun address(args: List<String>, index: Int): String? =
        args.getOrNull(index)?.takeIf { it.length == WORD_CHARS }?.let { "0x" + it.takeLast(ADDRESS_CHARS) }

    companion object {
        private const val SELECTOR_CHARS = 8
        private const val WORD_CHARS = 64
        private const val ADDRESS_CHARS = 40
        private const val NEW_SCORE = 300
        private val OK = SimulatedResult("0x1")
        private val REVERT = SimulatedResult("0x0")

        private fun selectorOf(signature: String): String = Hash.sha3String(signature).removePrefix("0x").take(SELECTOR_CHARS).lowercase()

        private val MINT = selectorOf("mint(uint256)")
        private val APPROVE = selectorOf("approve(address,uint256)")
        private val REQUEST_LOAN = selectorOf("requestLoan(uint256,uint16,uint32,uint8)")
        private val FUND = selectorOf("fund(uint256)")
        private val REPAY = selectorOf("repay(uint256,uint256)")
    }
}
