package com.latifi.wallet.core

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/** Formats a rate in basis points as a percent number without a trailing zero: 1000 gives `10`, 1250 gives `12.5`. */
object PercentFormat {
    fun fromBasisPoints(bps: Int, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.isGroupingUsed = false
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 2
        return format.format(BigDecimal(bps).movePointLeft(2))
    }
}
