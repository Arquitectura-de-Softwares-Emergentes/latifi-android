package com.latifi.wallet.domain.model

import java.time.Instant

enum class ContactType { EMAIL, PHONE }

data class Contact(val type: ContactType, val value: String)

/**
 * Off-chain profile (OpenAPI 0.2.0 `ProfileResponse`). The API sends [contact] only to the
 * owner of the address, so it is null when someone else looks.
 */
data class Profile(
    val address: String,
    val displayName: String,
    val contact: Contact?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
