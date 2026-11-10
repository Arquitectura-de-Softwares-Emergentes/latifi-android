package com.latifi.wallet.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.MinTouchTarget

/**
 * A two choice dialog where the safe choice holds the default focus. A person who opens it
 * and presses enter, or whose screen reader starts on the first action, never confirms by
 * accident. Both labels name their object ("Disconnect wallet", "Keep wallet connected").
 *
 * The dialog is a pane: it announces [title] as its pane title. Closing it, by either action
 * or by dismissing, hands focus back to [triggerFocus] when the caller passes the requester
 * of the element that opened it.
 *
 * With [destructive] the confirm label uses the error color. The label is still text, so the
 * color is never the only signal.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    safeLabel: String,
    onSafe: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    triggerFocus: FocusRequester? = null,
    onDismiss: () -> Unit = onSafe,
) {
    val safeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { safeFocus.requestFocus() }
    DisposableEffect(triggerFocus) {
        onDispose { runCatching { triggerFocus?.requestFocus() } }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text = body, style = MaterialTheme.typography.bodyLarge) },
        dismissButton = {
            TextButton(
                onClick = onSafe,
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .focusRequester(safeFocus)
                    .latifiFocusRing(),
            ) {
                Text(text = safeLabel, style = MaterialTheme.typography.labelLarge, color = LatiFiTheme.colors.link)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = MinTouchTarget).latifiFocusRing(),
            ) {
                Text(
                    text = confirmLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (destructive) MaterialTheme.colorScheme.error else LatiFiTheme.colors.link,
                )
            }
        },
    )
}
