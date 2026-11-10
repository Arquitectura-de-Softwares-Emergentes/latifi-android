package com.latifi.wallet.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme

/** The ring is 2dp, the width WCAG 2.4.13 asks for, and it stays 3:1 against the background. */
private val FocusRingWidth = 2.dp

/**
 * Draws a 2dp ring in the theme focusRing color while the element or one of its children has
 * focus. Focus comes from a keyboard, a D-pad or switch access, so touch use never shows it.
 * The default Material focus indication is left alone: this ring is added on top of it.
 */
fun Modifier.latifiFocusRing(shape: Shape = LatiFiShapes.button): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val ring = LatiFiTheme.colors.focusRing
    this
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(FocusRingWidth, ring, shape) else Modifier)
}
