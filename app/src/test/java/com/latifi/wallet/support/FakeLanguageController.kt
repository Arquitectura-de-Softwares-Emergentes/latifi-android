package com.latifi.wallet.support

import com.latifi.wallet.domain.language.AppLanguage
import com.latifi.wallet.domain.language.LanguageController

/** Remembers the language and counts the calls, so a test sees what the screen asked for. */
class FakeLanguageController(private var language: AppLanguage = AppLanguage.English) : LanguageController {
    val requested = mutableListOf<AppLanguage>()

    override fun current(): AppLanguage = language

    override fun set(language: AppLanguage) {
        requested += language
        this.language = language
    }
}
