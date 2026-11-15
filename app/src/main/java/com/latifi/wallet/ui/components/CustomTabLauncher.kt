package com.latifi.wallet.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URI
import java.net.URISyntaxException

/**
 * Opens a web page in a Custom Tab, or in the default browser when the device has no Custom
 * Tabs provider. Only https links open. Links come from BuildConfig constants and a fixed
 * explorer base, never from text the API returns, and this check is the second lock on that.
 */
object CustomTabLauncher {
    private const val TAG = "CustomTabLauncher"

    /** True for an absolute https URL with a host and no user info (`https://a.com@evil.com`). */
    fun isAllowed(url: String): Boolean {
        val uri = try {
            URI(url)
        } catch (_: URISyntaxException) {
            return false
        }
        return uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrEmpty() &&
            uri.userInfo == null
    }

    fun open(context: Context, url: String) {
        if (!isAllowed(url)) {
            // The URL is left out of the log on purpose.
            Log.w(TAG, "Ignored a link that is not an https address")
            return
        }
        val uri = Uri.parse(url)
        val tab = CustomTabsIntent.Builder().build()
        if (context !is Activity) tab.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            tab.launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            if (context !is Activity) view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(view)
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "No app can open web links on this device")
            }
        }
    }
}
