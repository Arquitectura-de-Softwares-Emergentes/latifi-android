package com.latifi.wallet.domain.feed

import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.model.ReputationLevel
import java.math.BigInteger

/**
 * Turns the chips of the Invest feed into the exact `GET /loans` parameters of the UI-SPEC S12
 * table. Every value comes from a closed enum and a fixed constant, so no free text reaches the
 * query. The amount bounds are base units of the token (6 decimals) as digit strings on the wire:
 *
 *  - Under 100 tUSD: `maxAmount=99999999`
 *  - 100 to 500 tUSD: `minAmount=100000000` and `maxAmount=500000000`
 *  - Over 500 tUSD: `minAmount=500000001`
 *
 * The feed only asks for open requests. No risk chip means no `minLevel`, so every level counts,
 * New included.
 */
object FeedQueryMapper {
    val UNDER_100_MAX: BigInteger = BigInteger("99999999")
    val FROM_100_MIN: BigInteger = BigInteger("100000000")
    val TO_500_MAX: BigInteger = BigInteger("500000000")
    val OVER_500_MIN: BigInteger = BigInteger("500000001")

    fun toQuery(filters: FeedFilters, page: Int = 0, size: Int = LoanQuery.DEFAULT_SIZE): LoanQuery =
        LoanQuery(
            status = LoanStatus.OPEN,
            minAmount = when (filters.amount) {
                AmountRange.From100To500 -> FROM_100_MIN
                AmountRange.Over500 -> OVER_500_MIN
                AmountRange.Under100, null -> null
            },
            maxAmount = when (filters.amount) {
                AmountRange.Under100 -> UNDER_100_MAX
                AmountRange.From100To500 -> TO_500_MAX
                AmountRange.Over500, null -> null
            },
            maxTermDays = filters.term?.days,
            minLevel = when (filters.risk) {
                Risk.Lowest -> ReputationLevel.EXCELLENT
                Risk.Low -> ReputationLevel.TRUSTED
                Risk.Medium -> ReputationLevel.BUILDING
                null -> null
            },
            sort = sortOf(filters.sort),
            page = page,
            size = size,
        )

    private fun sortOf(sort: FeedSort): LoanSort = when (sort) {
        FeedSort.Reputation -> LoanSort.Reputation
        FeedSort.Rate -> LoanSort.Rate
        FeedSort.Term -> LoanSort.Term
        FeedSort.Recent -> LoanSort.Recent
    }
}
