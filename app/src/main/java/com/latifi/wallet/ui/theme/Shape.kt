package com.latifi.wallet.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object LatiFiShapes {
    /** Text fields and menus. */
    val field = RoundedCornerShape(8.dp)

    /** Filter and status chips. */
    val chip = RoundedCornerShape(12.dp)

    /** Cards. */
    val card = RoundedCornerShape(16.dp)

    /** Bottom sheets, top corners only. */
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    /** Buttons are fully rounded. */
    val button = CircleShape
}

val LatiFiMaterialShapes = Shapes(
    extraSmall = LatiFiShapes.field,
    small = LatiFiShapes.field,
    medium = LatiFiShapes.chip,
    large = LatiFiShapes.card,
    extraLarge = RoundedCornerShape(28.dp),
)
