package com.latifi.wallet.ui.request

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.latifi.wallet.core.FieldError
import com.latifi.wallet.core.RequestField
import com.latifi.wallet.data.fake.FakeData
import com.latifi.wallet.data.fake.FakeExchangeRateRepository
import com.latifi.wallet.data.fake.FakeLoanRepository
import com.latifi.wallet.data.fake.FakeProfileRepository
import com.latifi.wallet.data.fake.FakeReceiptScript
import com.latifi.wallet.data.fake.FakeWalletScript
import com.latifi.wallet.data.session.InMemorySessionStore
import com.latifi.wallet.domain.model.Contact
import com.latifi.wallet.domain.model.ContactType
import com.latifi.wallet.domain.model.Profile
import com.latifi.wallet.domain.profile.ProfileGate
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.session.Session
import com.latifi.wallet.domain.tx.IndexerLagWaiter
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.domain.wallet.WalletRequest
import com.latifi.wallet.support.FakePrefsStore
import com.latifi.wallet.support.MainDispatcherRule
import com.latifi.wallet.support.TxHarness
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RequestLoanViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val clock = Clock.fixed(FakeData.EPOCH, ZoneOffset.UTC)
    private val profiles = FakeProfileRepository(clock).apply {
        seed(
            Profile(
                address = TxHarness.ADDRESS,
                displayName = "Ana",
                contact = Contact(ContactType.EMAIL, "ana@example.com"),
                createdAt = FakeData.EPOCH,
                updatedAt = FakeData.EPOCH,
            ),
        )
    }
    private val loans = FakeLoanRepository(clock)

    private class Setup(val vm: RequestLoanViewModel, val h: TxHarness, val handle: SavedStateHandle)

    private fun TestScope.setup(
        handle: SavedStateHandle = SavedStateHandle(),
        profileRepo: FakeProfileRepository = profiles,
        script: FakeWalletScript = FakeWalletScript.NeverAnswer,
    ): Setup {
        val h = TxHarness(backgroundScope, testScheduler).also { it.wallet.script = script }
        val store = InMemorySessionStore().also {
            runBlocking { it.save(Session(TxHarness.ADDRESS, "token", Instant.parse("2030-01-01T00:00:00Z"))) }
        }
        val vm = RequestLoanViewModel(
            saved = handle,
            coordinator = h.coordinator,
            wallet = h.wallet,
            loans = loans,
            rates = LocalValueProvider(FakeExchangeRateRepository(clock), clock),
            exchange = FakeExchangeRateRepository(clock),
            waiter = IndexerLagWaiter(),
            prefs = FakePrefsStore(),
            sessions = store,
            gate = ProfileGate(profileRepo),
        )
        runCurrent()
        return Setup(vm, h, handle)
    }

    private fun RequestLoanViewModel.fill(amount: String = "100", rate: String = "10.00", term: TermChoice = TermChoice.Days30) {
        onAmountChange(amount)
        onRateChange(rate)
        onTermSelect(term)
    }

    @Test
    fun theAmountBoundariesAreOneToTenThousandWithSixDecimals() = runTest {
        val vm = setup().vm
        vm.onTermSelect(TermChoice.Days30)
        fun validFor(text: String): Boolean {
            vm.onAmountChange(text)
            return vm.state.value.valid
        }

        assertFalse("0.99", validFor("0.99"))
        assertTrue("1", validFor("1"))
        assertTrue("10000", validFor("10000"))
        assertFalse("10001", validFor("10001"))
        assertTrue("6 decimals", validFor("1.123456"))
        assertFalse("7 decimals", validFor("1.1234567"))
    }

    @Test
    fun theRateBoundariesAreOneCentToFiftyPercentWithTwoDecimals() = runTest {
        val vm = setup().vm
        vm.onAmountChange("100")
        vm.onTermSelect(TermChoice.Days30)
        fun validFor(text: String): Boolean {
            vm.onRateChange(text)
            return vm.state.value.valid
        }

        assertFalse("0.00", validFor("0.00"))
        assertTrue("0.01", validFor("0.01"))
        assertTrue("50.00", validFor("50.00"))
        assertFalse("50.01", validFor("50.01"))
        assertFalse("3 decimals", validFor("10.001"))
    }

    @Test
    fun theOtherTermAcceptsOneToThreeHundredSixtyFiveDays() = runTest {
        val vm = setup().vm
        vm.onAmountChange("100")
        vm.onTermSelect(TermChoice.Other)
        fun validFor(days: String): Boolean {
            vm.onDaysChange(days)
            return vm.state.value.valid
        }

        assertFalse("0", validFor("0"))
        assertTrue("1", validFor("1"))
        assertTrue("365", validFor("365"))
        assertFalse("366", validFor("366"))
        assertFalse("empty", validFor(""))
    }

    @Test
    fun theStepperStopsAtOneAndSix() = runTest {
        val vm = setup().vm

        vm.onDecrease()
        assertEquals(1, vm.state.value.installments)
        repeat(8) { vm.onIncrease() }
        assertEquals(6, vm.state.value.installments)
        repeat(8) { vm.onDecrease() }
        assertEquals(1, vm.state.value.installments)
    }

    @Test
    fun eachShortcutChipSetsItsAmount() = runTest {
        val vm = setup().vm

        for (tokens in listOf(50, 100, 250, 500)) {
            vm.onAmountChip(tokens)
            assertEquals(tokens.toString(), vm.state.value.amount)
        }
    }

    @Test
    fun theRateStartsAtTenPercent() = runTest {
        assertEquals("10.00", setup().vm.state.value.rate)
    }

    @Test
    fun theReasonShowsUntilAllThreeAreValid() = runTest {
        val vm = setup().vm
        assertFalse(vm.state.value.valid)
        vm.onAmountChange("100")
        assertFalse(vm.state.value.valid)
        vm.onTermSelect(TermChoice.Days45)

        assertTrue(vm.state.value.valid)
    }

    @Test
    fun anErrorShowsOnBlurAndNotWhileTyping() = runTest {
        val vm = setup().vm
        vm.onAmountChange("10001")
        assertTrue("typing shows no error", vm.state.value.errors.isEmpty())

        vm.onBlur(RequestField.AMOUNT)

        assertEquals(FieldError.OUT_OF_RANGE, vm.state.value.errors[RequestField.AMOUNT])
    }

    @Test
    fun reviewOnAnInvalidFormShowsEveryErrorAndCountsThem() = runTest {
        val vm = setup().vm

        vm.onReview()

        assertEquals(setOf(RequestField.AMOUNT, RequestField.TERM), vm.state.value.errors.keys)
        assertEquals(FieldError.EMPTY, vm.state.value.errors[RequestField.TERM])
        assertEquals(2, vm.state.value.submitErrorCount)
        assertEquals(1, vm.state.value.step)
    }

    @Test
    fun stepTwoNumbersEqualTheCalculatorForTheAdrExample() = runTest {
        val vm = setup().vm
        vm.fill("100", "10.00", TermChoice.Days30)
        vm.onIncrease()

        vm.onReview()
        runCurrent()

        val preview = vm.state.value.preview!!
        assertEquals(2, vm.state.value.step)
        assertEquals(listOf(BigInteger.valueOf(50_410_958), BigInteger.valueOf(50_410_959)), preview.installmentAmounts)
        assertEquals(BigInteger.valueOf(100_821_917), preview.totalDue)
        assertTrue("the authoritative total", vm.state.value.totalLocal is LocalValue.Value)
    }

    @Test
    fun backReturnsToStepOneWithEveryValueIntact() = runTest {
        val vm = setup().vm
        vm.fill("250", "12.50", TermChoice.Days60)
        vm.onReview()

        vm.onBackToStep1()

        val s = vm.state.value
        assertEquals(1, s.step)
        assertEquals("250", s.amount)
        assertEquals("12.50", s.rate)
        assertEquals(TermChoice.Days60, s.term)
    }

    @Test
    fun valuesSurviveASavedStateRestore() = runTest {
        val first = setup()
        first.vm.fill("300", "8.00", TermChoice.Other)
        first.vm.onDaysChange("90")
        first.vm.onIncrease()
        first.vm.onIncrease()

        val restored = setup(handle = first.handle).vm.state.value

        assertEquals("300", restored.amount)
        assertEquals("8.00", restored.rate)
        assertEquals(TermChoice.Other, restored.term)
        assertEquals("90", restored.otherDays)
        assertEquals(3, restored.installments)
        assertTrue(restored.valid)
    }

    @Test
    fun theLeaveDialogOpensOnlyWhenTheFormIsDirty() = runTest {
        val clean = setup().vm
        clean.effects.test {
            clean.onLeaveRequested()
            assertEquals(RequestLoanEffect.Leave, awaitItem())
        }
        assertFalse(clean.state.value.leaveDialog)

        val dirty = setup().vm
        dirty.onAmountChip(50)
        dirty.onLeaveRequested()
        assertTrue(dirty.state.value.leaveDialog)

        dirty.onStayEditing()
        assertFalse(dirty.state.value.leaveDialog)
        assertEquals("50", dirty.state.value.amount)
    }

    @Test
    fun leavingConfirmedClosesTheScreen() = runTest {
        val vm = setup().vm
        vm.onAmountChip(50)
        vm.onLeaveRequested()

        vm.effects.test {
            vm.onLeaveConfirmed()
            assertEquals(RequestLoanEffect.Leave, awaitItem())
        }
    }

    @Test
    fun publishBeginsExactlyOneRequestLoanStepAfterTheProfileGate() = runTest {
        val s = setup()
        s.vm.fill("100", "10.00", TermChoice.Days30)
        s.vm.onIncrease()
        s.vm.onReview()

        s.vm.onPublish()
        s.vm.onPublish()
        runCurrent()

        val sent = s.h.wallet.sentRequests.map { it.second }.filterIsInstance<WalletRequest.SendTransaction>()
        assertEquals(1, sent.size)
        assertTrue(sent.single().data.startsWith("0x430a09d0"))
        assertEquals(com.latifi.wallet.data.chain.Deployment.LOANBOOK_ADDRESS, sent.single().to)
        assertEquals(1, profiles.getCalls)
    }

    @Test
    fun aMissingProfileOpensTheFormAndSendsNothing() = runTest {
        val s = setup(profileRepo = FakeProfileRepository(clock))
        s.vm.fill()
        s.vm.onReview()

        s.vm.effects.test {
            s.vm.onPublish()
            assertEquals(RequestLoanEffect.OpenProfileForm, awaitItem())
        }
        assertTrue(s.h.wallet.sentRequests.isEmpty())
    }

    @Test
    fun theValuesSurviveAFailedTransaction() = runTest {
        val s = setup(script = FakeWalletScript.Reject(delayMillis = 0))
        s.vm.fill("100", "9.50", TermChoice.Days45)
        s.vm.onReview()
        s.vm.onPublish()
        runCurrent()
        assertTrue(s.h.coordinator.state.value is TxState.Failed)

        val after = s.vm.state.value
        assertEquals("100", after.amount)
        assertEquals("9.50", after.rate)
        assertEquals(TermChoice.Days45, after.term)
        assertEquals(2, after.step)
    }

    @Test
    fun theConfirmedPrimaryWaitsForTheIndexedLoanThenOpensLoans() = runTest {
        val s = setup(script = FakeWalletScript.AutoApprove(delayMillis = 0))
        s.vm.fill()
        s.vm.onReview()
        s.vm.onPublish()
        advanceTimeBy(6_000)
        runCurrent()
        val confirmed = s.h.coordinator.state.value as TxState.Confirmed
        assertNull("the fake node has no log, so the id is absent", confirmed.loanId)

        s.vm.effects.test {
            s.h.coordinator.context.value!!.confirmedPrimary!!.onClick()
            runCurrent()
            assertEquals(RequestLoanEffect.OpenLoans, awaitItem())
        }
        assertFalse(s.vm.state.value.waitingForIndexer)
    }

    @Test
    fun withTheLoanIdTheWaitEndsWhenTheApiListsTheLoan() = runTest {
        val s = setup(script = FakeWalletScript.AutoApprove(delayMillis = 0))
        val newId = loans.loans.maxOf { it.loanId }.add(BigInteger.ONE)
        val topic = "0x" + newId.toString(16).padStart(64, '0')
        s.h.rpc.script(
            TxHarness.hashOf(1),
            FakeReceiptScript(
                logs = listOf(
                    com.latifi.wallet.data.chain.LogEntry(
                        com.latifi.wallet.data.chain.Deployment.LOANBOOK_ADDRESS,
                        listOf(com.latifi.wallet.data.chain.ReceiptLogParser.LOAN_REQUESTED_TOPIC, topic),
                        "0x",
                    ),
                ),
            ),
        )
        s.vm.fill()
        s.vm.onReview()
        s.vm.onPublish()
        advanceTimeBy(6_000)
        runCurrent()
        assertEquals(newId, (s.h.coordinator.state.value as TxState.Confirmed).loanId)

        s.vm.effects.test {
            s.h.coordinator.context.value!!.confirmedPrimary!!.onClick()
            runCurrent()
            expectNoEvents()
            assertTrue("waiting for the indexer", s.vm.state.value.waitingForIndexer)
            // The API lists the loan 7 seconds later.
            advanceTimeBy(7_000)
            loans.loans = loans.loans + loans.loans.first().copy(loanId = newId)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(RequestLoanEffect.OpenLoans, awaitItem())
        }
        assertNotNull(s.vm.state.value)
    }
}
