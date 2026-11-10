package com.latifi.wallet.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.latifi.wallet.R
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger

/** S07, first slice: the cards of the borrower's loans. Filters and every state arrive in the next commit. */
@Composable
fun LoansScreen(
    state: LoansUiState,
    onOpenLoan: (BigInteger) -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.loans_title), modifier = modifier) { padding ->
        when (val content = state.content) {
            is Loadable.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                items(content.data, key = { it.loanId.toString() }) { card ->
                    LoanCard(
                        model = card,
                        localValue = LocalValueProvider.valueFrom(state.rates, card.amount, state.currency),
                        onClick = { onOpenLoan(card.loanId) },
                    )
                }
            }
            else -> Text(
                text = stringResource(R.string.loading_loans),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(padding).padding(Spacing.lg),
            )
        }
    }
}
