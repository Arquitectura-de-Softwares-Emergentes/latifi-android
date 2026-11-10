package com.latifi.wallet.data.language

import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.language.LanguageController
import com.latifi.wallet.domain.language.LanguageResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The per-app language through AppCompat. `setApplicationLocales` works on API 33 and above with
 * the platform and below it through the AppCompat backport, which keeps the choice in the
 * `autoStoreLocales` service declared in the manifest. An empty application locale list means
 * the person never chose, so the app follows the device through [LanguageResolver].
 */
@Singleton
class AppCompatLanguageController @Inject constructor() : LanguageController {
    override fun current(): AppLanguage {
        val chosen = AppCompatDelegate.getApplicationLocales()
        val tags = if (!chosen.isEmpty) {
            chosen.toLanguageTags().split(',')
        } else {
            val device = Resources.getSystem().configuration.locales
            (0 until device.size()).map { device[it].toLanguageTag() }
        }
        return LanguageResolver.resolve(tags)
    }

    override fun set(language: AppLanguage) {
        val apply = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag)) }
        // The backport recreates the activity, so the call belongs on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) apply() else Handler(Looper.getMainLooper()).post { apply() }
    }
}
