package com.latifi.wallet.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.latifi.wallet.R

/** The footer of a paged list. It shows a spinner while the next page loads and ignores taps then. */
@Composable
fun LoadMoreButton(onClick: () -> Unit, modifier: Modifier = Modifier, loading: Boolean = false) {
    TertiaryButton(
        text = stringResource(R.string.load_more),
        onClick = onClick,
        modifier = modifier,
        loading = loading,
        fillMaxWidth = true,
    )
}
