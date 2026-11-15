package com.latifi.wallet.domain.auth

import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReauthCoordinatorTest {
    private val overlays = OverlayController()
    private val coordinator = DefaultReauthCoordinator(overlays)

    @Test
    fun twoParallelCallersShareOneRequestAndOneOutcome() = runTest {
        val first = async { coordinator.ensureSession(SignInReason.SessionExpired) }
        val second = async { coordinator.ensureSession(SignInReason.SessionExpired) }
        runCurrent()

        assertEquals(1, coordinator.runs)
        assertEquals(Overlays.SIGN_IN, overlays.current.value)
        assertNotNull(coordinator.request.value)
        assertTrue(first.isActive && second.isActive)

        coordinator.report(SignInOutcome.Signed)

        assertEquals(SignInOutcome.Signed, first.await())
        assertEquals(SignInOutcome.Signed, second.await())
        assertEquals(1, coordinator.runs)
        assertNull(overlays.current.value)
        assertNull(coordinator.request.value)
    }

    @Test
    fun aCancelledRunWakesEveryCallerWithCancelled() = runTest {
        val first = async { coordinator.ensureSession(SignInReason.FirstSignIn) }
        val second = async { coordinator.ensureSession(SignInReason.SessionExpired) }
        runCurrent()

        coordinator.report(SignInOutcome.Cancelled)

        assertEquals(SignInOutcome.Cancelled, first.await())
        assertEquals(SignInOutcome.Cancelled, second.await())
    }

    @Test
    fun aSecondCallAfterSuccessStartsANewRun() = runTest {
        val first = async { coordinator.ensureSession(SignInReason.FirstSignIn) }
        runCurrent()
        val firstId = coordinator.request.value!!.id
        coordinator.report(SignInOutcome.Signed)
        first.await()

        val second = async { coordinator.ensureSession(SignInReason.SessionExpired) }
        runCurrent()

        assertEquals(2, coordinator.runs)
        assertNotEquals(firstId, coordinator.request.value!!.id)
        assertEquals(SignInReason.SessionExpired, coordinator.request.value!!.reason)
        assertEquals(Overlays.SIGN_IN, overlays.current.value)
        coordinator.report(SignInOutcome.Signed)
        assertEquals(SignInOutcome.Signed, second.await())
    }

    @Test
    fun theSheetAppearsAboveAnOpenTransactionSheetAndLeavesItOpen() = runTest {
        overlays.show(Overlays.TX_STATUS)
        val run = async { coordinator.ensureSession(SignInReason.SessionExpired) }
        runCurrent()
        assertEquals(Overlays.SIGN_IN, overlays.current.value)

        coordinator.report(SignInOutcome.Signed)
        run.await()

        assertEquals(Overlays.TX_STATUS, overlays.current.value)
    }

    @Test
    fun reportingWithNoRunOpenIsHarmless() = runTest {
        coordinator.report(SignInOutcome.Cancelled)
        assertEquals(0, coordinator.runs)
    }
}
