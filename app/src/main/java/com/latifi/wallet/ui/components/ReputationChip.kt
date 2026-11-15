package com.latifi.wallet.ui.components

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.domain.model.ReputationLevel
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiTheme
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.Spacing

private val ChipIconSize = 16.dp
private val NeutralBorder = 1.dp
private val ExcellentBorder = 1.5.dp

/** The word of a reputation level, shared by the chip, the score block and the levels list. */
@StringRes
fun ReputationLevel.labelRes(): Int = when (this) {
    ReputationLevel.NEW -> R.string.rep_level_new
    ReputationLevel.BUILDING -> R.string.rep_level_building
    ReputationLevel.TRUSTED -> R.string.rep_level_trusted
    ReputationLevel.EXCELLENT -> R.string.rep_level_excellent
}

private data class LevelLook(
    val container: Color,
    val content: Color,
    @DrawableRes val icon: Int,
    val border: BorderStroke?,
)

/**
 * The reputation level as the UI-SPEC level table draws it: an icon, the level word, the score and
 * a color pair, together. Excellent carries a 1.5dp success border so it differs from Trusted
 * without color, and New is neutral with its own outline. The chip is one node that reads "Borrower
 * reputation: Trusted, 620 of 1000". The score uses tabular figures.
 */
@Composable
fun ReputationChip(level: ReputationLevel, score: Int, modifier: Modifier = Modifier) {
    val colors = LatiFiTheme.colors
    val look = when (level) {
        ReputationLevel.NEW -> LevelLook(
            colors.neutralContainer,
            colors.neutralText,
            R.drawable.ic_fiber_new,
            BorderStroke(NeutralBorder, colors.neutralBorder),
        )
        ReputationLevel.BUILDING -> LevelLook(colors.infoContainer, colors.link, R.drawable.ic_trending_up, null)
        ReputationLevel.TRUSTED -> LevelLook(colors.successContainer, colors.success, R.drawable.ic_verified_user, null)
        ReputationLevel.EXCELLENT -> LevelLook(
            colors.successContainer,
            colors.success,
            R.drawable.ic_workspace_premium,
            BorderStroke(ExcellentBorder, colors.success),
        )
    }
    val name = stringResource(level.labelRes())
    val spoken = stringResource(R.string.rep_chip_a11y, name, score)
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        shape = LatiFiShapes.chip,
        color = look.container,
        border = look.border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(look.icon),
                contentDescription = null,
                tint = look.content,
                modifier = Modifier.size(ChipIconSize),
            )
            Text(text = name, style = MaterialTheme.typography.labelMedium, color = look.content)
            Text(text = score.toString(), style = LatiFiType.Amount.BodyM, color = look.content)
        }
    }
}
