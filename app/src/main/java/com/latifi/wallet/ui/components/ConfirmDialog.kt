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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.MinTouchTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
            // The effect lives here, inside the dialog window, on purpose. The dialog composes
            // its content in its own window a moment after the caller, so a request made from
            // the caller would run before this button exists and be lost.
            FocusSafeActionOnShow(safeFocus)
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

/**
 * Asks for focus on the safe action as soon as the dialog window shows it. The dialog window
 * takes view focus a frame or two after it opens, so one request can be refused. It first waits
 * for the dialog window to hold the window focus (at most [WindowFocusWaitMillis], so a window
 * that never reports focus cannot block it), then tries on the next frames, at most
 * [MaxAttempts] times, and stops as soon as the focus is held.
 */
@Composable
private fun FocusSafeActionOnShow(safeFocus: FocusRequester) {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(safeFocus) {
        // A request made while the dialog window is not focused is accepted by Compose but the
        // focus state stays inactive on the emulator, so the request waits for the window first.
        withTimeoutOrNull(WindowFocusWaitMillis) {
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
        }
        repeat(MaxAttempts) {
            if (runCatching { safeFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
}

private const val MaxAttempts = 5
private const val WindowFocusWaitMillis = 1_000L
