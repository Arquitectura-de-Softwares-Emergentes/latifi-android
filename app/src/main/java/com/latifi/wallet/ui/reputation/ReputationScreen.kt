package com.latifi.wallet.ui.reputation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.domain.model.ReputationSummary
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.components.ChipOption
import com.latifi.wallet.ui.components.EmptyPanel
import com.latifi.wallet.ui.components.ErrorPanel
import com.latifi.wallet.ui.components.LiveCounter
import com.latifi.wallet.ui.components.LoadMoreButton
import com.latifi.wallet.ui.components.ReputationChip
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.SkeletonCard
import com.latifi.wallet.ui.components.SkeletonList
import com.latifi.wallet.ui.components.StatusFilterChips
import com.latifi.wallet.ui.components.labelRes
import com.latifi.wallet.ui.components.latifiFocusRing
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/** The list starts to load the next page when the last visible row is this close to the end. */
private const val AUTO_LOAD_DISTANCE = 3

private val DisclosureIconSize = 24.dp
private val CounterTileMinWidth = 104.dp

/**
 * S11 Reputation, stateless. Top to bottom: the score block (the score as text, the level chip, a
 * determinate bar that only repeats the number, the time of the last update), the three counters,
 * the Levels disclosure, then the history with its four filter chips, the polite events counter
 * and the newest-first rows. A person with no details yet sees the add-your-details state in place
 * of all of it. Each part has its own loading, error and empty state.
 */
@Composable
fun ReputationScreen(
    state: ReputationUiState,
    onEvent: (ReputationEvents) -> Unit,
    onAddDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val failedText = stringResource(R.string.refresh_failed)
    val reloadText = stringResource(R.string.retry_reload)

    LaunchedEffect(state.refreshFailed) {
        if (state.refreshFailed) {
            val result = snackbar.showSnackbar(message = failedText, actionLabel = reloadText)
            onEvent(ReputationEvents.RefreshFailedShown)
            if (result == SnackbarResult.ActionPerformed) onEvent(ReputationEvents.ReloadHistory)
        }
    }

    val items = state.history as? HistoryContent.Items
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - AUTO_LOAD_DISTANCE
        }
    }
    LaunchedEffect(nearEnd, items?.hasMore, items?.events?.size) {
        if (nearEnd && items?.hasMore == true && !state.isLoadingMore) onEvent(ReputationEvents.LoadMore)
    }

    ScreenScaffold(
        title = stringResource(R.string.rep_title),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            when (val score = state.score) {
                ScoreContent.Loading -> item(key = "score-loading") { SkeletonCard() }
                is ScoreContent.Error -> item(key = "score-error") {
                    ErrorPanel(kind = score.kind, onReload = { onEvent(ReputationEvents.Reload) })
                }
                ScoreContent.NoProfile -> item(key = "no-profile") {
                    EmptyPanel(
                        title = stringResource(R.string.rep_title),
                        body = stringResource(R.string.rep_no_profile),
                        actionLabel = stringResource(R.string.rep_add_details_cta),
                        onAction = onAddDetails,
                    )
                }
                is ScoreContent.Ready -> {
                    item(key = "score") { ScoreBlock(score.summary) }
                    item(key = "counters") { Counters(score.summary) }
                    item(key = "levels") { LevelsDisclosure() }
                    item(key = "history-header") { HistoryHeader(state, onEvent) }
                    historyItems(state, onEvent)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.historyItems(
    state: ReputationUiState,
    onEvent: (ReputationEvents) -> Unit,
) {
    when (val history = state.history) {
        HistoryContent.Loading -> item(key = "history-loading") {
            SkeletonList(count = 2, description = stringResource(R.string.loading_generic))
        }
        is HistoryContent.Error -> item(key = "history-error") {
            ErrorPanel(kind = history.kind, onReload = { onEvent(ReputationEvents.ReloadHistory) })
        }
        HistoryContent.Empty -> item(key = "history-empty") {
            EmptyPanel(
                title = stringResource(R.string.rep_history_empty_title),
                body = stringResource(R.string.rep_history_empty_body),
            )
        }
        HistoryContent.FilteredEmpty -> item(key = "history-filtered-empty") {
            EmptyPanel(
                title = stringResource(R.string.rep_history_empty_title),
                body = stringResource(R.string.rep_history_empty_body),
                actionLabel = stringResource(R.string.loans_show_all),
                onAction = { onEvent(ReputationEvents.ShowAll) },
            )
        }
        is HistoryContent.Items -> {
            items(history.events, key = { it.id }) { event -> HistoryRow(event) }
            if (history.hasMore) {
                item(key = "more") {
                    LoadMoreButton(onClick = { onEvent(ReputationEvents.LoadMore) }, loading = state.isLoadingMore)
                }
            }
        }
    }
}

@Composable
private fun ScoreBlock(summary: ReputationSummary) {
    val locale = currentLocale()
    val today = rememberToday()
    val levelName = stringResource(summary.level.labelRes())
    val spoken = stringResource(R.string.rep_score_a11y, levelName, summary.score)
    val updated = ReputationFormat.updated(summary.updatedAt, locale, today)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LatiFiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // One phrase for the whole block: "Reputation 620 of 1000, level Trusted".
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(text = stringResource(R.string.rep_score, summary.score), style = LatiFiType.Amount.Display)
                ReputationChip(level = summary.level, score = summary.score)
            }
            // The number above is the source of truth. The bar repeats it for sighted people.
            LinearProgressIndicator(
                progress = { ReputationFormat.barFraction(summary.score) },
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            )
            Text(
                text = stringResource(R.string.rep_asof, updated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Counters(summary: ReputationSummary) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        CounterTile(stringResource(R.string.rep_counter_ontime), summary.onTimeCount)
        CounterTile(stringResource(R.string.rep_counter_late), summary.lateCount)
        CounterTile(stringResource(R.string.rep_counter_defaulted), summary.defaultCount)
    }
}

@Composable
private fun CounterTile(label: String, value: Int) {
    val spoken = "$label, $value"
    Surface(
        modifier = Modifier.widthIn(min = CounterTileMinWidth),
        shape = LatiFiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.lg)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = value.toString(), style = LatiFiType.Amount.Headline)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The Levels disclosure: a 48dp button that shows or hides the four ranges. The state reaches
 * assistive technology as the expand or collapse action the node offers, and sighted people see
 * the chevron turn and the text appear.
 */
@Composable
private fun LevelsDisclosure() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.rep_levels_title)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LatiFiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MinTouchTarget)
                    .latifiFocusRing(LatiFiShapes.card)
                    .clickable(role = Role.Button, onClickLabel = title, onClick = { expanded = !expanded })
                    .semantics {
                        if (expanded) {
                            collapse { expanded = false; true }
                        } else {
                            expand { expanded = true; true }
                        }
                    }
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Icon(
                    painter = painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(DisclosureIconSize),
                )
            }
            if (expanded) {
                Text(
                    text = stringResource(R.string.rep_levels_body),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg),
                )
            }
        }
    }
}

@Composable
private fun HistoryHeader(state: ReputationUiState, onEvent: (ReputationEvents) -> Unit) {
    val options = HistoryFilter.entries.map { ChipOption(it, stringResource(it.labelRes())) }
    val counterText = state.counter?.let { count ->
        val quantity = count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.counter_events, quantity, quantity)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusFilterChips(
            label = stringResource(R.string.rep_history_title),
            options = options,
            selected = state.filter,
            onSelect = { onEvent(ReputationEvents.SelectFilter(it)) },
        )
        LiveCounter(text = counterText, loadingText = stringResource(R.string.loading_generic))
    }
}

private fun HistoryFilter.labelRes(): Int = when (this) {
    HistoryFilter.All -> R.string.rep_filter_all
    HistoryFilter.OnTime -> R.string.rep_filter_ontime
    HistoryFilter.Late -> R.string.rep_filter_late
    HistoryFilter.Defaulted -> R.string.rep_filter_defaulted
}

private fun ReputationEventType.labelRes(): Int = when (this) {
    ReputationEventType.PROFILE_COMPLETED -> R.string.rep_event_profile
    ReputationEventType.REPAID_ON_TIME -> R.string.rep_event_ontime
    ReputationEventType.REPAID_LATE -> R.string.rep_event_late
    ReputationEventType.DEFAULTED -> R.string.rep_event_defaulted
}

/**
 * One history row: date, what happened, the loan number when there is one, the signed points
 * change and the score after it. The row is one merged node and reads, for example, "Paid on
 * time, Loan 7, +20 points, Score after: 620, January 5". The pieces wrap at large text sizes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryRow(event: ReputationEvent) {
    val locale = currentLocale()
    val today = rememberToday()
    val label = stringResource(event.type.labelRes())
    val loan = event.loanId?.let { stringResource(R.string.rep_row_loan, it) }
    val points = ReputationFormat.points(event.delta)
    val pointsText = pluralStringResource(R.plurals.rep_event_points, points.magnitude, points.sign, points.magnitude)
    val afterText = stringResource(R.string.rep_row_after, event.scoreAfter)
    val dateText = ReputationFormat.date(event.occurredAt, locale, today)
    val spoken = stringResource(R.string.rep_row_a11y, listOfNotNull(label, loan).joinToString(", "), pointsText, afterText, dateText)
    val pointsColor: Color = when {
        event.delta > 0 -> LatiFiTheme.colors.success
        event.delta < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LatiFiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
    ) {
        Column(
            modifier = Modifier
                .padding(Spacing.lg)
                .semantics(mergeDescendants = true) { contentDescription = spoken },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = label, style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (loan != null) Text(text = loan, style = MaterialTheme.typography.bodyMedium)
                Text(text = pointsText, style = LatiFiType.Amount.BodyL, color = pointsColor)
                Text(text = afterText, style = LatiFiType.Amount.BodyM)
            }
        }
    }
}
