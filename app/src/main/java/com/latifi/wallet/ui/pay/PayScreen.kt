package com.latifi.wallet.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.latifi.wallet.R
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.ui.components.AmountBlock
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.theme.Spacing

/**
 * S10 for one installment: the amount to pay (the unpaid amount of the current installment, never
 * more), the due date, the plain line about the wallet, the late note when it applies, the fee
 * note and one primary. A DEFAULTED loan shows the blocked copy and no primary.
 */
@Composable
fun PayScreen(
    state: PayUiState,
    onConfirm: () -> Unit,
    onReload: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = (state.content as? Loadable.Success)?.data
    val title = if (data != null && !data.blocked) {
        stringResource(R.string.pay_title, data.index, data.loan.installments)
    } else {
        stringResource(R.string.pay_cta)
    }
    ScreenScaffold(
        title = title,
        modifier = modifier,
        onBack = onBack,
        bottomBar = data?.takeIf { !it.blocked }?.let {
            {
                PrimaryButton(
                    text = stringResource(R.string.pay_confirm),
                    onClick = onConfirm,
                    loading = state.waitingForIndexer,
                )
            }
        },
    ) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Column(Modifier.padding(padding).padding(Spacing.lg)) {
                SkeletonList(count = 1, description = stringResource(R.string.loading_generic))
            }
            is Loadable.Error -> Column(Modifier.padding(padding).fillMaxSize()) {
                ErrorPanel(kind = content.kind, onReload = onReload)
            }
            is Loadable.Success -> PayBody(content.data, state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PayBody(data: PayData, state: PayUiState, modifier: Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        if (data.blocked) {
            Text(text = stringResource(R.string.pay_blocked), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        val locale = currentLocale()
        AmountBlock(
            label = stringResource(R.string.pay_amount),
            amount = data.unpaid,
            local = state.amountLocal.takeIf { it !is LocalValue.Loading } ?: LocalValue.Loading,
        )
        Text(
            text = stringResource(R.string.pay_due, DateFormat.short(data.dueAt, locale, rememberToday())),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (data.late) {
            Text(
                text = stringResource(R.string.pay_late),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(text = stringResource(R.string.pay_line), style = MaterialTheme.typography.bodyLarge)
        Text(
            text = stringResource(R.string.network_fee_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.needsWallet) {
            Text(text = stringResource(R.string.tx_fail_session), color = MaterialTheme.colorScheme.error)
        }
        if (state.waitingForIndexer) {
            Text(
                text = stringResource(R.string.loading_loans),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
