package com.latifi.wallet.domain.feed

/** The risk chips of the Invest feed. Each one is a minimum reputation level of the borrower. */
enum class Risk { Lowest, Low, Medium }

/** The term chips: an upper bound on the loan term, in days. */
enum class TermLimit(val days: Int) {
    UpTo30(30),
    UpTo45(45),
    UpTo60(60),
}

/** The amount chips, in tUSD. */
enum class AmountRange { Under100, From100To500, Over500 }

/** The four sort orders of the Sort menu. Reputation is the default. */
enum class FeedSort { Reputation, Rate, Term, Recent }

/**
 * What the lender chose on the Invest feed. Each of [risk], [term] and [amount] holds one value or
 * none, and a chip toggles off when it is chosen again ([toggleRisk], [toggleTerm], [toggleAmount]).
 * The [sort] is not a filter: it never counts as active and [cleared] keeps it.
 */
data class FeedFilters(
    val risk: Risk? = null,
    val term: TermLimit? = null,
    val amount: AmountRange? = null,
    val sort: FeedSort = FeedSort.Reputation,
) {
    /** How many of the three groups have a chip selected. The sort never counts. */
    val activeCount: Int get() = listOfNotNull(risk, term, amount).size

    val hasActive: Boolean get() = activeCount > 0

    fun toggleRisk(chip: Risk): FeedFilters = copy(risk = if (risk == chip) null else chip)

    fun toggleTerm(chip: TermLimit): FeedFilters = copy(term = if (term == chip) null else chip)

    fun toggleAmount(chip: AmountRange): FeedFilters = copy(amount = if (amount == chip) null else chip)

    /** Clear the three groups and keep the sort. */
    fun cleared(): FeedFilters = copy(risk = null, term = null, amount = null)
}
