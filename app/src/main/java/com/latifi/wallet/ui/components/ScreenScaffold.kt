package com.latifi.wallet.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/**
 * Opens the navigation drawer in large text mode. Null everywhere else. AdaptiveNavScaffold
 * provides it, and ScreenScaffold shows a Menu button when it is set.
 */
val LocalMenuAction = staticCompositionLocalOf<(() -> Unit)?> { null }

/** The top bar is never shorter than this and grows with the text. */
private val TopBarMinHeight = 64.dp

/**
 * The frame of every screen: a top bar whose Headline title is a heading and the first thing
 * a screen reader reaches, a 48dp back arrow when [onBack] is set (or a 48dp Menu button in
 * large text mode), content that respects the system insets, and an optional [bottomBar] that
 * holds the one primary action and sits above the keyboard.
 */
@Composable
fun ScreenScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = { ScreenTopBar(title = title, onBack = onBack) },
        snackbarHost = snackbarHost,
        bottomBar = { if (bottomBar != null) PinnedActionBar(bottomBar) },
        content = content,
    )
}

@Composable
private fun ScreenTopBar(title: String, onBack: (() -> Unit)?) {
    val menuAction = LocalMenuAction.current
    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .heightIn(min = TopBarMinHeight)
                .padding(horizontal = Spacing.xs)
                // The title carries traversalIndex -1 inside this group, so a screen reader
                // reaches it before the back or menu button that sits at its start.
                .semantics { isTraversalGroup = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                onBack != null -> IconButton(onClick = onBack, modifier = Modifier.size(MinTouchTarget).latifiFocusRing()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.action_back),
                    )
                }
                menuAction != null -> IconButton(onClick = menuAction, modifier = Modifier.size(MinTouchTarget).latifiFocusRing()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_menu),
                        contentDescription = stringResource(R.string.menu_open),
                    )
                }
                else -> Spacer(Modifier.width(Spacing.sm))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.sm, vertical = Spacing.sm)
                    .semantics {
                        heading()
                        traversalIndex = -1f
                    },
            )
        }
    }
}

@Composable
private fun PinnedActionBar(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(Spacing.lg),
        ) {
            content()
        }
    }
}
