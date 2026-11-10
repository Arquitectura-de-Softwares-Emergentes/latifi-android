package com.latifi.wallet.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.ReputationEvent
import com.latifi.wallet.domain.model.ReputationEventType
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.common.rememberToday
import com.latifi.wallet.ui.reputation.ReputationFormat
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

@StringRes
private fun ReputationEventType.eventLabelRes(): Int = when (this) {
    ReputationEventType.PROFILE_COMPLETED -> R.string.rep_event_profile
    ReputationEventType.REPAID_ON_TIME -> R.string.rep_event_ontime
    ReputationEventType.REPAID_LATE -> R.string.rep_event_late
    ReputationEventType.DEFAULTED -> R.string.rep_event_defaulted
}

/**
 * One row of a reputation history: date, what happened, the loan number when there is one, the
 * signed points change and the score after it. The row is one merged node and reads, for example,
 * "Paid on time, Loan 7, +20 points, Score after: 620, January 5". The pieces wrap at large text
 * sizes. The Reputation screen (S11) and the request details (S13) share it, so both read the same.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReputationEventRow(event: ReputationEvent, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val today = rememberToday()
    val label = stringResource(event.type.eventLabelRes())
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
        modifier = modifier.fillMaxWidth(),
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
