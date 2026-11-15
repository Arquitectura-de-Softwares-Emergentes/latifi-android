package com.latifi.wallet.domain.loan

import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.Page
import java.math.BigInteger

/**
 * The loan reads of the API. Failures travel in `Result` as `AppException`, so a screen can map
 * them to copy without knowing about HTTP.
 */
interface LoanRepository {
    /** One page of `GET /loans` for [query]. */
    suspend fun list(query: LoanQuery): Result<Page<Loan>>

    /** One loan by id. An unknown id fails with `AppError.NotFound`. */
    suspend fun get(loanId: BigInteger): Result<Loan>
}
