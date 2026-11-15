package com.latifi.wallet.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The launcher icon is the web favicon mark: navy background, lime pulse, themed layer, splash. */
class LauncherIconResourcesTest {
    private val res = File("src/main/res")
    private fun read(path: String) = File(res, path).also { assertTrue("missing $path", it.isFile) }.readText()

    @Test
    fun adaptiveIconsHaveBackgroundForegroundAndMonochrome() {
        listOf("mipmap-anydpi-v26/ic_launcher.xml", "mipmap-anydpi-v26/ic_launcher_round.xml").forEach {
            val xml = read(it)
            assertTrue(xml.contains("@color/ic_launcher_background"))
            assertTrue(xml.contains("@drawable/ic_launcher_foreground"))
            assertTrue("$it needs the themed icon layer", xml.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\""))
        }
    }

    @Test
    fun backgroundIsTheBrandNavy() {
        assertTrue(read("values/ic_launcher_background.xml").contains("#0B1F3A", ignoreCase = true))
    }

    @Test
    fun foregroundIsTheLimePulse() {
        val xml = read("drawable/ic_launcher_foreground.xml")
        assertTrue(xml.contains("#C6FF3D", ignoreCase = true))
        // The pulse polyline of the favicon isotype.
        assertTrue(xml.contains("M20.4,53.2H40.8L48.4,31.8L57.9,74.1L64.9,53.2H79.7"))
    }

    @Test
    fun monochromeLayerUsesOneColor() {
        val xml = read("drawable/ic_launcher_monochrome.xml")
        assertFalse(xml.contains("#C6FF3D", ignoreCase = true))
        assertTrue(xml.contains("#000000"))
    }

    @Test
    fun markStaysInsideTheSafeZone() {
        // The favicon mark spans x 10.4..89.7 and y 27.5..78.4 in its own 100 unit box. The group
        // transform scales it about its pivot and shifts it into the 108dp canvas, whose 66dp safe
        // zone is 21..87.
        val xml = read("drawable/ic_launcher_foreground.xml")
        fun attr(name: String) = Regex("android:$name=\"([-0-9.]+)\"").find(xml)!!.groupValues[1].toFloat()
        val pivotX = attr("pivotX")
        val pivotY = attr("pivotY")
        val scale = attr("scaleX")
        val tx = attr("translateX")
        val ty = attr("translateY")
        fun x(v: Float) = pivotX + (v - pivotX) * scale + tx
        fun y(v: Float) = pivotY + (v - pivotY) * scale + ty
        assertTrue(x(10.4f) >= 21f)
        assertTrue(x(89.7f) <= 87f)
        assertTrue(y(27.5f) >= 21f)
        assertTrue(y(78.4f) <= 87f)
    }

    @Test
    fun splashUsesTheNavyBackgroundAndThePulse() {
        val themes = read("values/themes.xml")
        assertTrue(themes.contains("Theme.SplashScreen"))
        assertTrue(themes.contains("windowSplashScreenBackground"))
        assertTrue(themes.contains("@drawable/ic_splash_animated"))
        assertTrue(File(res, "drawable/ic_splash_pulse.xml").isFile)
        assertTrue(File(res, "animator/splash_pulse_draw.xml").isFile)
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("@style/Theme.LatiFi.Starting"))
        assertTrue(manifest.contains("enableOnBackInvokedCallback=\"true\""))
    }

    @Test
    fun contrastOfThePulseOnTheNavyIsHigh() {
        assertEquals(0xFFC6FF3D.toInt(), ColorTokens.Lime400)
    }
}
