package com.latifi.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.latifi.wallet.R
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.currentLocale

/**
 * The second line of a detail row for an amount: the local estimate ("≈ S/ 937.50"), the
 * unavailable note, or null while the rates load so the row shows the token amount alone.
 */
@Composable
fun localEstimateText(value: LocalValue): String? = when (value) {
    is LocalValue.Value -> stringResource(R.string.local_estimate, value.currency.format(value.amount, currentLocale()))
    LocalValue.Unavailable -> stringResource(R.string.local_unavailable)
    LocalValue.Loading -> null
}
