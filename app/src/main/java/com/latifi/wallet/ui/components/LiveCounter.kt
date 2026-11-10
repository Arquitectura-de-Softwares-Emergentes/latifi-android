package com.latifi.wallet.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/**
 * The count line above a list ("12 loans"). While [text] is null (the list is loading) the line
 * shows [loadingText] as plain text, so nothing is announced for a number that is not there yet.
 * Once the count exists it becomes the one polite live region of the screen, so a screen reader
 * says the new count after a filter changes, without moving focus.
 */
@Composable
fun LiveCounter(text: String?, loadingText: String, modifier: Modifier = Modifier) {
    if (text == null) {
        Text(
            text = loadingText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
