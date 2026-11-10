package com.latifi.wallet.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.Spacing

/**
 * Card-shaped placeholders while a list loads. The group is one node described as [description]
 * (for example "Loading loans") and the cards inside are silent. With the system animations off
 * the cards are static, with no shimmer.
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    count: Int = 3,
    description: String = stringResource(R.string.loading_loans),
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        repeat(count) { SkeletonCard() }
    }
}

/** Test tag of the content of each skeleton card, so a UI test can count the cards. */
const val SKELETON_CARD_TAG = "skeleton_card"

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val alpha = if (animate) {
        val transition = rememberInfiniteTransition()
        transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        ).value
    } else {
        1f
    }
    Surface(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
        shape = LatiFiShapes.card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LatiFiTheme.colors.outlineDecor),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg).alpha(alpha).testTag(SKELETON_CARD_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Bar(width = 140.dp, height = 28.dp)
            Bar(width = 96.dp, height = 16.dp)
            Bar(width = 200.dp, height = 16.dp)
            Bar(width = null, height = 6.dp)
        }
    }
}

@Composable
private fun Bar(width: Dp?, height: Dp) {
    Box(
        modifier = Modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .background(MaterialTheme.colorScheme.outlineVariant, LatiFiShapes.chip),
    )
}
