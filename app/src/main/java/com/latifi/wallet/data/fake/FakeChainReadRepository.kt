package com.latifi.wallet.data.fake

import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.chain.NextInstallment
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.InstallmentState
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import java.math.BigInteger

/**
 * Scriptable chain reads for the demo build and for tests. A test sets a balance per address with
 * [setTokenBalance] and [setPolBalance], changes an allowance with [setAllowance] (the fakes of
 * plan 05-12 update it on an approval), and queues failures with [failNext]. [loans] is a lookup
 * so `nextInstallment` follows the same loans the fake API serves, through
 * `InstallmentSchedule.remainingOnCurrent`. [reads] lists every address a balance read received,
 * so a test can prove that Home asks for the signed-in address and no other.
 */
class FakeChainReadRepository(
    private val loans: () -> List<Loan> = { emptyList() },
) : ChainReadRepository {
    private val tokens = mutableMapOf<String, TokenAmount>()
    private val pol = mutableMapOf<String, BigInteger>()
    private val allowances = mutableMapOf<Pair<String, String>, BigInteger>()
    private val failures = mutableMapOf<Read, AppError>()

    /** The kinds of read a failure can be queued for. */
    enum class Read { TokenBalance, PolBalance, Allowance, NextInstallment }

    /** `Read` and the address (or loan id) of each call, in order. */
    val reads = mutableListOf<Pair<Read, String>>()

    init {
        // The demo wallet starts with 1,000 tUSD and 0.5 POL, enough for the whole demo path.
        tokens[key(FakeData.DEMO_ADDRESS)] = TokenAmount.ofTokens(DEMO_TOKENS)
        pol[key(FakeData.DEMO_ADDRESS)] = DEMO_POL_WEI
    }

    fun setTokenBalance(address: String, amount: TokenAmount) {
        tokens[key(address)] = amount
    }

    fun setPolBalance(address: String, wei: BigInteger) {
        pol[key(address)] = wei
    }

    fun setAllowance(owner: String, spender: String, amount: BigInteger) {
        allowances[key(owner) to key(spender)] = amount
    }

    /** The next call of [read] fails with [error], once. */
    fun failNext(read: Read, error: AppError) {
        failures[read] = error
    }

    override suspend fun tokenBalance(address: String): Result<TokenAmount> {
        reads += Read.TokenBalance to key(address)
        takeFailure(Read.TokenBalance)?.let { return it }
        return Result.success(tokens[key(address)] ?: TokenAmount(BigInteger.ZERO))
    }

    override suspend fun polBalance(address: String): Result<BigInteger> {
        reads += Read.PolBalance to key(address)
        takeFailure(Read.PolBalance)?.let { return it }
        return Result.success(pol[key(address)] ?: BigInteger.ZERO)
    }

    override suspend fun allowance(owner: String, spender: String): Result<BigInteger> {
        reads += Read.Allowance to key(owner)
        takeFailure(Read.Allowance)?.let { return it }
        return Result.success(allowances[key(owner) to key(spender)] ?: BigInteger.ZERO)
    }

    override suspend fun nextInstallment(loanId: BigInteger): Result<NextInstallment> {
        reads += Read.NextInstallment to loanId.toString()
        takeFailure(Read.NextInstallment)?.let { return it }
        val loan = loans().firstOrNull { it.loanId == loanId }
        if (loan == null || loan.status != LoanStatus.ACTIVE) return Result.failure(AppException(AppError.NotFound))
        val current = InstallmentSchedule.build(loan).firstOrNull { it.state == InstallmentState.DUE }
        val dueAt = current?.dueAt
        if (current == null || dueAt == null) return Result.failure(AppException(AppError.NotFound))
        return Result.success(
            NextInstallment(
                index = current.index,
                dueAt = dueAt,
                unpaid = TokenAmount(InstallmentSchedule.remainingOnCurrent(loan)),
            ),
        )
    }

    private fun takeFailure(read: Read): Result<Nothing>? =
        failures.remove(read)?.let { Result.failure(AppException(it)) }

    private fun key(address: String) = address.lowercase()

    companion object {
        const val DEMO_TOKENS = 1_000L

        /** 0.5 POL, far above the low fee threshold. */
        val DEMO_POL_WEI: BigInteger = BigInteger("500000000000000000")
    }
}
