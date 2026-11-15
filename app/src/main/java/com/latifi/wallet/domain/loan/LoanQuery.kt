package com.latifi.wallet.domain.loan

import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import java.math.BigInteger

/** The sort orders of `GET /loans`. [wire] is the exact value of the `sort` query parameter. */
enum class LoanSort(val wire: String) {
    Reputation("reputation"),
    Amount("amount"),
    Term("term"),
    Rate("rate"),
    Recent("recent"),
    NextDue("nextDue"),
}

/**
 * The filters, the sort and the page of `GET /loans` (OpenAPI 0.2.0). Amounts are base units of
 * the token. [page] starts at 0 and [size] goes from 1 to 100. Addresses compare without case.
 */
data class LoanQuery(
    val status: LoanStatus? = null,
    val minAmount: BigInteger? = null,
    val maxAmount: BigInteger? = null,
    val maxTermDays: Int? = null,
    val minLevel: ReputationLevel? = null,
    val borrower: String? = null,
    val lender: String? = null,
    val sort: LoanSort = LoanSort.Reputation,
    val page: Int = 0,
    val size: Int = DEFAULT_SIZE,
) {
    companion object {
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }
}
