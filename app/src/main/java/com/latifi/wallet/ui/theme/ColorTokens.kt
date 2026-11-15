package com.latifi.wallet.ui.theme

/**
 * Brand color tokens as plain ARGB integers, with no Compose types, so JVM unit tests read
 * the very same values the theme uses. ThemeContrastTest holds every pair of the UI-SPEC
 * Contrast Table against these constants, so editing a value here without updating the
 * table turns the build red.
 */
object ColorTokens {
    /** Coral 500. Decorative fill only in light theme (2.82:1 on white), usable as an icon in dark. */
    const val Coral500: Int = 0xFFFF6B4A.toInt()

    /** Lime 400, the brand pulse. Fill and icon on navy, never body text on white. */
    const val Lime400: Int = 0xFFC6FF3D.toInt()

    /** Lime 300, the soft brand fill. Navy text on it passes 14:1. */
    const val Lime300: Int = 0xFFD8FF7A.toInt()

    object Light {
        const val background: Int = 0xFFFFFFFF.toInt()
        const val surface: Int = 0xFFF5F7FA.toInt()
        const val onSurface: Int = 0xFF101828.toInt()
        const val onSurfaceVariant: Int = 0xFF566174.toInt()

        /** Navy 900. Fill of the single primary button. */
        const val primary: Int = 0xFF0B1F3A.toInt()
        const val onPrimary: Int = 0xFFFFFFFF.toInt()

        /** Navy 700. Links, info text, selected chip check. */
        const val link: Int = 0xFF14407A.toInt()
        const val info: Int = link
        const val infoContainer: Int = 0xFFE8EFF9.toInt()
        const val focusRing: Int = link

        /** Control borders, 3:1 against the background. */
        const val outline: Int = 0xFF7B8698.toInt()

        /** Card border and dividers. Decorative only. */
        const val outlineDecor: Int = 0xFFDDE3EA.toInt()
        const val secondaryContainer: Int = 0xFFDCE6F5.toInt()

        const val success: Int = 0xFF0A7D5A.toInt()
        const val successContainer: Int = 0xFFE6F4EF.toInt()
        const val warning: Int = 0xFFB45309.toInt()
        const val warningContainer: Int = 0xFFFEF3E2.toInt()
        const val error: Int = 0xFFC62828.toInt()
        const val errorContainer: Int = 0xFFFDECEC.toInt()

        /** Reputation level NEW chip. */
        const val neutralContainer: Int = 0xFFF5F7FA.toInt()
        const val neutralText: Int = 0xFF101828.toInt()
        const val neutralBorder: Int = 0xFF7B8698.toInt()

        /** Hero surface: navy to navy 700 gradient, white text, lime accent. */
        const val heroStart: Int = 0xFF0B1F3A.toInt()
        const val heroEnd: Int = 0xFF14407A.toInt()
        const val onHero: Int = 0xFFFFFFFF.toInt()
        const val onHeroVariant: Int = 0xFFC9D6EA.toInt()
        const val heroAccent: Int = Lime400

        /** Quiet chip that sits on the hero. */
        const val heroChip: Int = 0xFF1F3F6B.toInt()

        /** Soft lime fill (selected tab pill, highlights) and its text. */
        const val limeContainer: Int = Lime300
        const val onLimeContainer: Int = 0xFF0B1F3A.toInt()

        /** Faint lime wash for a highlighted card. */
        const val limeTint: Int = 0xFFF3FFD1.toInt()
        const val onLimeTint: Int = 0xFF101828.toInt()

        /** Reputation ring and gauge colors per level, 3:1 or better on surface. */
        const val repNew: Int = 0xFF7B8698.toInt()
        const val repBuilding: Int = 0xFF14407A.toInt()
        const val repTrusted: Int = 0xFF0A7D5A.toInt()
        const val repExcellent: Int = 0xFF0A7D5A.toInt()
    }

    object Dark {
        const val background: Int = 0xFF0B0F14.toInt()
        const val surface: Int = 0xFF171C24.toInt()
        const val onSurface: Int = 0xFFF5F7FA.toInt()
        const val onSurfaceVariant: Int = 0xFFA9B4C4.toInt()

        /** Lime 400. Fill of the single primary button. */
        const val primary: Int = 0xFFC6FF3D.toInt()
        const val onPrimary: Int = 0xFF0B0F14.toInt()

        const val link: Int = 0xFF8FB8FF.toInt()
        const val info: Int = link
        const val infoContainer: Int = 0xFF1B2A44.toInt()
        const val focusRing: Int = 0xFFC6FF3D.toInt()

        const val outline: Int = 0xFF7C8799.toInt()
        const val outlineDecor: Int = 0xFF2A3340.toInt()
        const val secondaryContainer: Int = 0xFF2A3340.toInt()

        const val success: Int = 0xFF4FD1A5.toInt()
        const val successContainer: Int = 0xFF12332B.toInt()
        const val warning: Int = 0xFFF5A524.toInt()
        const val warningContainer: Int = 0xFF3A2A10.toInt()
        const val error: Int = 0xFFFF8A80.toInt()
        const val errorContainer: Int = 0xFF3A1D1D.toInt()

        const val neutralContainer: Int = 0xFF2A3340.toInt()
        const val neutralText: Int = 0xFFF5F7FA.toInt()
        const val neutralBorder: Int = 0xFF7C8799.toInt()

        const val heroStart: Int = 0xFF163C6E.toInt()
        const val heroEnd: Int = 0xFF0B1F3A.toInt()
        const val onHero: Int = 0xFFFFFFFF.toInt()
        const val onHeroVariant: Int = 0xFFC9D6EA.toInt()
        const val heroAccent: Int = Lime400
        const val heroChip: Int = 0xFF1F4278.toInt()

        const val limeContainer: Int = 0xFF26330F.toInt()
        const val onLimeContainer: Int = 0xFFF5F7FA.toInt()
        const val limeTint: Int = 0xFF26330F.toInt()
        const val onLimeTint: Int = 0xFFF5F7FA.toInt()

        const val repNew: Int = 0xFF7C8799.toInt()
        const val repBuilding: Int = 0xFF8FB8FF.toInt()
        const val repTrusted: Int = 0xFF4FD1A5.toInt()
        const val repExcellent: Int = Lime400
    }
}
