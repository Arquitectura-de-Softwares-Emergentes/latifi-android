package com.latifi.wallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

/**
 * One label and value pair. [secondary] is an optional line under the value, used for the local
 * estimate of an amount. A screen reader reads the whole pair as one phrase.
 */
data class DetailRowData(val label: String, val value: String, val secondary: String? = null)

/**
 * A table of label and value pairs. Each row is one merged node that reads "Interest rate, 10
 * percent a year". The label and the value share a wrapping row, so at 200 percent text the value
 * drops under its label instead of being cut.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailRows(rows: List<DetailRowData>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            DetailRow(row)
            if (index < rows.lastIndex) HorizontalDivider(color = LatiFiTheme.colors.outlineDecor)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailRow(row: DetailRowData) {
    val spoken = listOfNotNull(row.label, row.value, row.secondary).joinToString(", ")
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Spacing.lg),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(text = row.value, style = LatiFiType.Amount.BodyL, textAlign = TextAlign.End)
            if (row.secondary != null) {
                Text(
                    text = row.secondary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
