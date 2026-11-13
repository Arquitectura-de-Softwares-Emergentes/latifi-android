package com.latifi.wallet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.ErrorKind
import com.latifi.wallet.ui.theme.Spacing

private val PanelIconSize = 48.dp

/**
 * The state of a data screen that has nothing to show because the load failed: an error icon, the
 * copy for [kind] and a Reload button. When stale data exists the screen uses a snackbar instead.
 */
@Composable
fun ErrorPanel(kind: ErrorKind, onReload: () -> Unit, modifier: Modifier = Modifier) {
    val text = ErrorCopy.textOf(kind)
    StatePanel(
        iconRes = R.drawable.ic_error,
        iconTint = MaterialTheme.colorScheme.error,
        title = text.title?.let { stringResource(it) },
        body = stringResource(text.body),
        actionLabel = stringResource(R.string.retry_reload),
        onAction = onReload,
        modifier = modifier,
    )
}

/**
 * An empty list: a title, one sentence on what to do and a button that does it. Used for "no
 * data yet" and for "no data under this filter", each with its own copy and action. An empty
 * state with nothing to do leaves [actionLabel] and [onAction] out and shows no button. The action
 * is secondary unless [primaryAction] is set, for the screen whose one main action it is.
 */
@Composable
fun EmptyPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    primaryAction: Boolean = false,
    illustration: Illustration = Illustration.Pulse,
) {
    StatePanel(
        illustration = illustration,
        iconRes = R.drawable.ic_info,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        title = title,
        body = body,
        actionLabel = actionLabel,
        onAction = onAction,
        modifier = modifier,
        primaryAction = primaryAction,
    )
}

@Composable
private fun StatePanel(
    iconRes: Int,
    iconTint: androidx.compose.ui.graphics.Color,
    title: String?,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier,
    primaryAction: Boolean = false,
    illustration: Illustration? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (illustration != null) {
            EmptyIllustration(illustration)
        } else {
            IconChip(icon = iconRes, tone = Tone.Danger, size = PanelIconSize)
        }
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
        }
        Text(text = body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            if (primaryAction) {
                PrimaryButton(text = actionLabel, onClick = onAction, fillMaxWidth = false)
            } else {
                SecondaryButton(text = actionLabel, onClick = onAction, fillMaxWidth = false)
            }
        }
    }
}
