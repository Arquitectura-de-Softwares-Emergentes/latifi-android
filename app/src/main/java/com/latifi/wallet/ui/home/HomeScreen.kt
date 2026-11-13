package com.latifi.wallet.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.DateFormat
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.components.AmountBlock
import com.latifi.wallet.ui.components.BalanceHero
import com.latifi.wallet.ui.components.IconChip
import com.latifi.wallet.ui.components.LatiFiCard
import com.latifi.wallet.ui.components.ReputationRing
import com.latifi.wallet.ui.components.Tone
import com.latifi.wallet.ui.components.staggerIn
import com.latifi.wallet.ui.components.AttributionRow
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ReputationChip
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SecondaryButton
import com.latifi.wallet.ui.components.SkeletonCard
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing
import java.math.BigInteger

private val BannerIconSize = 24.dp

/**
 * S06 Home, stateless. Both roles show, in this order: the greeting (once), the low network fee
 * banner (when it applies), the balance block, a role block, the next installment or next payment
 * card, the one primary button, the Get test coins action and the rates attribution.
 *
 *  - Borrower: the reputation chip with the score, the Next installment card with Pay installment,
 *    and the primary Request a loan.
 *  - Lender: the Open requests count, the Next payment you receive card and the primary Browse
 *    requests.
 *
 * Every section has its own skeleton, error panel with Reload and content, so one failure never
 * hides another. The Pay installment, Request a loan and Browse requests actions are callbacks that
 * the route wires.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onEvent: (HomeEvent) -> Unit,
    onPay: (BigInteger) -> Unit,
    onRequestLoan: () -> Unit,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScreenScaffold(title = stringResource(R.string.nav_home), modifier = modifier) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            state.greeting?.let { Greeting(it) }
            if (state.lowFee) LowFeeBanner()
            BalanceSection(state, Modifier.staggerIn(0)) { onEvent(HomeEvent.Reload(HomeSection.Balance)) }
            when (state.role) {
                AppRole.Borrower -> BorrowerSections(state, onEvent, onPay)
                AppRole.Lender -> LenderSections(state, onEvent)
            }
            PrimaryButton(
                text = stringResource(if (state.role == AppRole.Borrower) R.string.request_title else R.string.home_browse),
                onClick = if (state.role == AppRole.Borrower) onRequestLoan else onBrowse,
                modifier = Modifier.staggerIn(3),
            )
            TertiaryButton(
                text = stringResource(R.string.coins_cta),
                onClick = { onEvent(HomeEvent.GetCoins) },
            )
            AttributionRow()
        }
    }
}

/** "Hello again, Ana". It is visible and also a polite announcement, so it is heard once without moving focus. */
@Composable
private fun Greeting(name: String) {
    Text(
        text = stringResource(R.string.welcome_back, name),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Warning container, icon and text. There is no button because no one-tap fix exists (ADR-0005). */
@Composable
private fun LowFeeBanner() {
    val colors = LatiFiTheme.colors
    LatiFiCard(tone = Tone.Warning) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_warning),
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(BannerIconSize),
            )
            Text(
                text = stringResource(R.string.home_fee_low),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.warning,
            )
        }
    }
}

@Composable
private fun HomeCard(modifier: Modifier = Modifier, tone: Tone = Tone.Neutral, content: @Composable () -> Unit) {
    LatiFiCard(modifier = modifier, tone = tone, content = content)
}

@Composable
private fun BalanceSection(state: HomeUiState, modifier: Modifier, onReload: () -> Unit) {
    when (val balance = state.balance) {
        Loadable.Loading -> SkeletonCard()
        is Loadable.Error -> ErrorPanel(kind = balance.kind, onReload = onReload)
        is Loadable.Success -> BalanceHero(modifier = modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AmountBlock(
                    label = stringResource(R.string.home_balance),
                    amount = balance.data,
                    local = LocalValueProvider.valueFrom(state.rates, balance.data, state.currency),
                    onHero = true,
                    countUp = true,
                )
                // A wallet with nothing in it says why and points at the Get test coins action below.
                if (balance.data.baseUnits.signum() == 0) {
                    Text(
                        text = stringResource(R.string.home_zero_balance),
                        style = MaterialTheme.typography.bodyLarge,
                        color = LatiFiTheme.colors.onHero,
                    )
                }
            }
        }
    }
}

@Composable
private fun BorrowerSections(state: HomeUiState, onEvent: (HomeEvent) -> Unit, onPay: (BigInteger) -> Unit) {
    when (val reputation = state.reputation) {
        Loadable.Loading -> SkeletonCard()
        is Loadable.Error -> ErrorPanel(
            kind = reputation.kind,
            onReload = { onEvent(HomeEvent.Reload(HomeSection.Reputation)) },
        )
        // No reputation yet: no chip. The Reputation tab explains how to start one.
        is Loadable.Success -> reputation.data?.let { rep ->
            HomeCard(modifier = Modifier.staggerIn(1)) {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReputationRing(score = rep.score, level = rep.level, size = 84.dp, stroke = 9.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(text = stringResource(R.string.rep_title), style = MaterialTheme.typography.titleMedium)
                        ReputationChip(level = rep.level, score = rep.score)
                    }
                }
            }
        }
    }
    NextDueSection(
        state = state,
        section = state.nextInstallment,
        title = stringResource(R.string.home_next_installment),
        emptyText = stringResource(R.string.home_no_installment),
        onReload = { onEvent(HomeEvent.Reload(HomeSection.NextInstallment)) },
        onPay = onPay,
    )
}

@Composable
private fun LenderSections(state: HomeUiState, onEvent: (HomeEvent) -> Unit) {
    when (val open = state.openRequests) {
        Loadable.Loading -> SkeletonCard()
        is Loadable.Error -> ErrorPanel(
            kind = open.kind,
            onReload = { onEvent(HomeEvent.Reload(HomeSection.OpenRequests)) },
        )
        is Loadable.Success -> {
            val label = stringResource(R.string.home_open_requests)
            val spoken = "$label, ${open.data}"
            HomeCard(modifier = Modifier.staggerIn(1), tone = Tone.Info) {
                Row(
                    modifier = Modifier
                        .padding(Spacing.lg)
                        .semantics(mergeDescendants = true) { contentDescription = spoken },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconChip(icon = R.drawable.ic_trending_up, tone = Tone.Info, size = 48.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(text = open.data.toString(), style = LatiFiType.Amount.Headline)
                    }
                }
            }
        }
    }
    NextDueSection(
        state = state,
        section = state.nextPayment,
        title = stringResource(R.string.home_next_payment_in),
        emptyText = stringResource(R.string.home_no_payment_in),
        onReload = { onEvent(HomeEvent.Reload(HomeSection.NextPayment)) },
        onPay = null,
    )
}

/**
 * The Next installment card of the borrower and the Next payment you receive card of the lender.
 * The summary is one merged node. The Pay installment button, when [onPay] is set, is the only
 * other target of the card and is a secondary button, because the screen has one primary action.
 */
@Composable
private fun NextDueSection(
    state: HomeUiState,
    section: Loadable<NextDueCard?>,
    title: String,
    emptyText: String,
    onReload: () -> Unit,
    onPay: ((BigInteger) -> Unit)?,
) {
    when (section) {
        Loadable.Loading -> SkeletonCard()
        is Loadable.Error -> ErrorPanel(kind = section.kind, onReload = onReload)
        is Loadable.Success -> HomeCard(modifier = Modifier.staggerIn(2), tone = Tone.Accent) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                val card = section.data
                if (card == null) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(text = emptyText, style = MaterialTheme.typography.bodyLarge)
                } else {
                    NextDueSummary(title, card, LocalValueProvider.valueFrom(state.rates, card.amount, state.currency))
                    if (onPay != null) {
                        SecondaryButton(
                            text = stringResource(R.string.pay_cta),
                            onClick = { onPay(card.loanId) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextDueSummary(title: String, card: NextDueCard, local: LocalValue) {
    val locale = currentLocale()
    val today = rememberToday()
    val amountText = card.amount.format(locale)
    val dueText = card.dueAt?.let { DateFormat.short(it, locale, today) }
    val dueLine = dueText?.let { stringResource(R.string.loan_next_due, it) }
    val localSpoken = when (local) {
        is LocalValue.Value -> stringResource(R.string.local_about_a11y, local.currency.format(local.amount, locale))
        LocalValue.Unavailable -> stringResource(R.string.local_unavailable)
        LocalValue.Loading -> null
    }
    val spoken = listOfNotNull(title, amountText, localSpoken, dueLine).joinToString(", ")
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        IconChip(icon = R.drawable.ic_schedule, tone = Tone.Success, size = 48.dp)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = amountText, style = LatiFiType.Amount.Headline)
            LocalValueLine(local)
            if (dueLine != null) Text(text = dueLine, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
