package com.latifi.wallet.domain.feed

import com.latifi.wallet.domain.profile.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Looks up the public display name of the borrowers on screen, lazily and at most once per address.
 * The feed shows the card at once and the name fills in when the profile answers, so a slow or
 * missing profile never blocks a card. Until a name is known [names] has no entry for the address
 * and the card says "Name not shared yet".
 *
 * Addresses compare without case. Concurrent calls to [resolve] for the same address share one
 * request. A lookup that found nothing, or failed, keeps the fallback and is not repeated until
 * [retryMissing] runs (pull to refresh calls it). Only `publicName` is asked: no contact field is
 * ever requested for a feed card.
 */
class BorrowerNameResolver(
    private val scope: CoroutineScope,
    private val profiles: ProfileRepository,
) {
    private val lock = Any()
    private val asked = mutableSetOf<String>()
    private val missing = mutableSetOf<String>()
    private val mutableNames = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Known names by lowercase address. */
    val names: StateFlow<Map<String, String>> = mutableNames.asStateFlow()

    /** Starts the lookup of every address that has not been asked yet. */
    fun resolve(addresses: Collection<String>) {
        val fresh = synchronized(lock) { addresses.map { it.lowercase() }.filter { asked.add(it) } }
        fresh.forEach { address ->
            scope.launch {
                val name = try {
                    profiles.publicName(address)?.takeIf { it.isNotBlank() }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    null
                }
                if (name != null) {
                    mutableNames.update { it + (address to name) }
                } else {
                    synchronized(lock) { missing += address }
                }
            }
        }
    }

    /** Lets the addresses that had no name be asked again on the next [resolve]. */
    fun retryMissing() {
        synchronized(lock) {
            asked.removeAll(missing)
            missing.clear()
        }
    }
}
