package com.latifi.wallet.domain.language

/** The two languages of the app. [tag] is the BCP 47 tag handed to the platform. */
enum class AppLanguage(val tag: String) {
    English("en-US"),
    Spanish("es-419"),
}

/** Reads and changes the per-app language. */
interface LanguageController {
    /** The language in use: the saved choice, or the device language mapped by [LanguageResolver]. */
    fun current(): AppLanguage

    /** Saves the choice. The platform recreates the activity so every string reloads. */
    fun set(language: AppLanguage)
}

/**
 * Maps locale tags to one of the two languages. Any Spanish tag (`es`, `es-PE`, `es-419`, `es_MX`,
 * `es-ES`) gives Spanish, everything else gives English. The first tag decides, because it is the
 * language the person ranked highest. An empty list gives English.
 */
object LanguageResolver {
    fun resolve(localeTags: List<String>): AppLanguage {
        val first = localeTags.firstOrNull { it.isNotBlank() } ?: return AppLanguage.English
        val language = first.trim().substringBefore('-').substringBefore('_').lowercase()
        return if (language == "es") AppLanguage.Spanish else AppLanguage.English
    }
}
