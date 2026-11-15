package com.latifi.wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LoanStatusMappingTest {
    @Test
    fun openMapsToOpen() = assertEquals(UiLoanStatus.Open, LoanStatus.OPEN.toUi())

    @Test
    fun activeMapsToPending() = assertEquals(UiLoanStatus.Pending, LoanStatus.ACTIVE.toUi())

    @Test
    fun repaidMapsToPaid() = assertEquals(UiLoanStatus.Paid, LoanStatus.REPAID.toUi())

    @Test
    fun defaultedMapsToOverdue() = assertEquals(UiLoanStatus.Overdue, LoanStatus.DEFAULTED.toUi())

    @Test
    fun everyStatusHasAUiStatus() {
        assertEquals(LoanStatus.entries.size, LoanStatus.entries.map { it.toUi() }.toSet().size)
    }
}
