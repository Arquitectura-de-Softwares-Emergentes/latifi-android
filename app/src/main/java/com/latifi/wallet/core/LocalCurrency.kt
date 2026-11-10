package com.latifi.wallet.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * The six local currencies of the exchange-rate endpoint. Symbols come from this table and not
 * from ICU, because the platform symbol of a currency changes with the locale and the device
 * (research finding F6). The stablecoin counts as one US dollar.
 */
enum class LocalCurrency(val code: String, val symbol: String, val fractionDigits: Int) {
    PEN("PEN", "S/", 2),
    COP("COP", "$", 2),
    MXN("MXN", "$", 2),
    ARS("ARS", "$", 2),
    BRL("BRL", "R$", 2),
    CLP("CLP", "$", 0),
    ;

    /**
     * Whole tokens times [rate] (units of local currency per US dollar), rounded half-even to
     * the fraction digits of the currency. Pure BigDecimal, so it matches the server rounding.
     */
    fun convert(amount: TokenAmount, rate: BigDecimal): BigDecimal =
        amount.toDecimal().multiply(rate).setScale(fractionDigits, RoundingMode.HALF_EVEN)

    /** Symbol, one space, then the number with the grouping and decimal symbols of [locale]. */
    fun format(value: BigDecimal, locale: Locale): String {
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.isGroupingUsed = true
        format.roundingMode = RoundingMode.HALF_EVEN
        format.minimumFractionDigits = fractionDigits
        format.maximumFractionDigits = fractionDigits
        return "$symbol ${format.format(value)}"
    }

    /**
     * The bare local text for the `local_estimate` string (`S/ 937.50`). The resource adds the
     * approximate sign, so it is not part of this text.
     */
    fun formatEstimate(amount: TokenAmount, rate: BigDecimal, locale: Locale): String =
        format(convert(amount, rate), locale)

    companion object {
        /** The currency for an ISO code of the API, or null when the code is not supported. */
        fun fromCode(code: String): LocalCurrency? = entries.firstOrNull { it.code == code }
    }
}
