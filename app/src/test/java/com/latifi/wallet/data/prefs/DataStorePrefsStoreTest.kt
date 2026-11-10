package com.latifi.wallet.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.domain.model.AppRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStorePrefsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    /** A new store on the same file, like a new process reading what the last one wrote. */
    private fun store(file: File): DataStorePrefsStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += scope
        return DataStorePrefsStore(PreferenceDataStoreFactory.create(scope = scope) { file })
    }

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    @Test
    fun startsWithNoRoleAndOnboardingNotSeen() = runBlocking {
        val prefs = store(File(tmp.root, "a.preferences_pb"))
        assertNull(prefs.role.first())
        assertFalse(prefs.onboardingSeen.first())
    }

    @Test
    fun roleAndOnboardingSeenPersistAcrossANewStoreOnTheSameFile() = runBlocking {
        val file = File(tmp.root, "b.preferences_pb")
        val first = store(file)
        first.setRole(AppRole.Lender)
        first.setOnboardingSeen(true)
        scopes.first().cancel()

        val second = store(file)
        assertEquals(AppRole.Lender, second.role.first())
        assertTrue(second.onboardingSeen.first())
    }

    @Test
    fun clearRoleRemovesOnlyTheRole() = runBlocking {
        val prefs = store(File(tmp.root, "c.preferences_pb"))
        prefs.setRole(AppRole.Borrower)
        prefs.setOnboardingSeen(true)

        prefs.clearRole()

        assertNull(prefs.role.first())
        assertTrue(prefs.onboardingSeen.first())
    }

    @Test
    fun theRoleCanBeReplaced() = runBlocking {
        val file = File(tmp.root, "d.preferences_pb")
        val prefs = store(file)
        prefs.setRole(AppRole.Borrower)
        assertEquals(AppRole.Borrower, prefs.role.first())
        prefs.setRole(AppRole.Lender)
        assertEquals(AppRole.Lender, prefs.role.first())
    }

    @Test
    fun theCurrencyDefaultsToPenAndPersistsAcrossANewStore() = runBlocking {
        val file = File(tmp.root, "e.preferences_pb")
        val first = store(file)
        assertEquals(LocalCurrency.PEN, first.currency.first())

        first.setCurrency(LocalCurrency.BRL)
        scopes.first().cancel()

        assertEquals(LocalCurrency.BRL, store(file).currency.first())
    }

    @Test
    fun clearingTheRoleKeepsTheCurrency() = runBlocking {
        val prefs = store(File(tmp.root, "f.preferences_pb"))
        prefs.setRole(AppRole.Lender)
        prefs.setCurrency(LocalCurrency.ARS)

        prefs.clearRole()

        assertNull(prefs.role.first())
        assertEquals(LocalCurrency.ARS, prefs.currency.first())
    }
}
