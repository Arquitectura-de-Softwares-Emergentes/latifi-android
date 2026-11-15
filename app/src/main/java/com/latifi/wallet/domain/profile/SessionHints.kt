package com.latifi.wallet.domain.profile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small notes that one screen leaves for another, kept in memory only. After a returning person
 * signs in, the greeting name waits here until Home shows "Hello again, {name}" and consumes it,
 * so the greeting appears once and never again on a later visit to Home.
 */
@Singleton
class SessionHints @Inject constructor() {
    private val greeting = MutableStateFlow<String?>(null)

    /** The name to greet, or null when there is nothing to say. */
    val greetingName: StateFlow<String?> = greeting.asStateFlow()

    fun queueGreeting(name: String) {
        greeting.value = name
    }

    /** Returns the queued name and clears it, so a second call returns null. */
    fun consume(): String? {
        val name = greeting.value
        greeting.value = null
        return name
    }
}
