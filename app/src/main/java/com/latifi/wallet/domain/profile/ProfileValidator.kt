package com.latifi.wallet.domain.profile

import com.latifi.wallet.domain.model.ContactType

enum class NameError { Required, TooLong }

/** One code for both contact types: the screen picks the email or the phone sentence. */
enum class ContactError { Invalid }

/** Per-field result. A null field means that field is valid. */
data class ProfileErrors(val name: NameError?, val contact: ContactError?) {
    val count: Int get() = listOfNotNull(name, contact).size
    val isValid: Boolean get() = count == 0
}

/**
 * Pure input rules, applied before any call (the API checks again). The name is trimmed and has 1
 * to 80 characters. The contact value is trimmed and has 3 to 254 characters, and matches a simple
 * email shape, or a phone with a leading plus and 7 to 15 digits (spaces, dashes and parentheses
 * are allowed between them).
 */
object ProfileValidator {
    const val NAME_MAX = 80
    const val CONTACT_MIN = 3
    const val CONTACT_MAX = 254
    const val PHONE_MIN_DIGITS = 7
    const val PHONE_MAX_DIGITS = 15

    private val EMAIL = Regex("""^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$""")
    private val PHONE = Regex("""^\+[0-9 ()\-]+$""")

    fun validateName(raw: String): NameError? {
        val name = raw.trim()
        return when {
            name.isEmpty() -> NameError.Required
            name.length > NAME_MAX -> NameError.TooLong
            else -> null
        }
    }

    fun validateContact(type: ContactType, raw: String): ContactError? {
        val value = raw.trim()
        if (value.length < CONTACT_MIN || value.length > CONTACT_MAX) return ContactError.Invalid
        val valid = when (type) {
            ContactType.EMAIL -> EMAIL.matches(value)
            ContactType.PHONE -> PHONE.matches(value) && value.count { it.isDigit() } in PHONE_MIN_DIGITS..PHONE_MAX_DIGITS
        }
        return if (valid) null else ContactError.Invalid
    }

    fun validate(name: String, type: ContactType, contact: String): ProfileErrors =
        ProfileErrors(validateName(name), validateContact(type, contact))
}
