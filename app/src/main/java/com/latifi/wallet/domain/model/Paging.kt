package com.latifi.wallet.domain.model

/** One page of a list. [page] starts at 0, as in the API. */
data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
) {
    /** True when more items exist after this page. */
    val hasMore: Boolean get() = (page.toLong() + 1L) * size.toLong() < totalItems
}
