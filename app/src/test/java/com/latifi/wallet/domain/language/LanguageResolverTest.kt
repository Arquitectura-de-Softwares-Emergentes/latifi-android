package com.latifi.wallet.domain.language

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageResolverTest {
    private fun resolve(vararg tags: String) = LanguageResolver.resolve(tags.toList())

    @Test
    fun everySpanishTagMapsToLatinAmericanSpanish() {
        listOf("es", "es-PE", "es-MX", "es-419", "es-ES", "es_AR", "ES-co").forEach {
            assertEquals(it, AppLanguage.Spanish, resolve(it))
        }
    }

    @Test
    fun everythingElseMapsToEnglish() {
        listOf("en-GB", "en", "pt-BR", "fr", "de-DE", "zh-Hans").forEach {
            assertEquals(it, AppLanguage.English, resolve(it))
        }
    }

    @Test
    fun anEmptyListFallsBackToEnglish() {
        assertEquals(AppLanguage.English, LanguageResolver.resolve(emptyList()))
        assertEquals(AppLanguage.English, resolve("", " "))
    }

    @Test
    fun theFirstTagDecides() {
        assertEquals(AppLanguage.English, resolve("en-GB", "es-PE"))
        assertEquals(AppLanguage.Spanish, resolve("es-PE", "en-GB"))
    }

    @Test
    fun theTagsHandedToThePlatformAreTheTwoSupportedOnes() {
        assertEquals("en-US", AppLanguage.English.tag)
        assertEquals("es-419", AppLanguage.Spanish.tag)
    }
}
