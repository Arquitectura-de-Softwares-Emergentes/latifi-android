package com.latifi.wallet.domain.chain

import java.math.BigInteger

/**
 * When the wallet is too low on network fee funds to be safe. The banner on Home reads this and
 * nothing else decides it.
 */
object FeePolicy {
    /**
     * 0.02 POL in wei, about five transactions at 100,000 gas (research finding F9, observed gas
     * price near 38.8 gwei on 2026-11-09). The value is an assumption of the research, so it lives
     * in this one constant and changes here only.
     */
    val LOW_FEE_THRESHOLD_WEI: BigInteger = BigInteger("20000000000000000")

    /** True only when [balanceWei] is strictly below the threshold. A balance equal to it is not low. */
    fun isLow(balanceWei: BigInteger): Boolean = balanceWei < LOW_FEE_THRESHOLD_WEI
}
