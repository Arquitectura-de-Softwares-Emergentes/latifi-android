package com.latifi.wallet.data.api

import com.latifi.wallet.data.api.dto.toDomain
import com.latifi.wallet.data.api.dto.toDomainOrNull
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.Page
import java.math.BigInteger

/**
 * `GET /loans` and `GET /loans/{loanId}` through Retrofit. The query goes out exactly as given:
 * the sort by its wire name, amounts as digit strings, addresses in lowercase, and a filter that
 * is null is not sent at all. [LoanQuery.page] and [LoanQuery.size] are always sent.
 */
class ApiLoanRepository(private val api: LatiFiApi) : LoanRepository {
    override suspend fun list(query: LoanQuery): Result<Page<Loan>> =
        apiResult(
            call = {
                api.listLoans(
                    status = query.status?.name,
                    minAmount = query.minAmount?.toString(),
                    maxAmount = query.maxAmount?.toString(),
                    maxTermDays = query.maxTermDays,
                    minLevel = query.minLevel?.name,
                    borrower = query.borrower?.lowercase(),
                    lender = query.lender?.lowercase(),
                    sort = query.sort.wire,
                    page = query.page,
                    size = query.size,
                )
            },
            transform = { it.toDomain() },
        )

    override suspend fun get(loanId: BigInteger): Result<Loan> =
        apiResult(call = { api.getLoan(loanId.toString()) }, transform = { it.toDomainOrNull() })
            .mapCatching { it ?: throw AppException(AppError.Server) }
}
