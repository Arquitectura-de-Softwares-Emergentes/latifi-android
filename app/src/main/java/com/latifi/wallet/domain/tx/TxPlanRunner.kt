package com.latifi.wallet.domain.tx

import androidx.annotation.StringRes

/**
 * Runs the steps of a plan from [TxPlans] through the coordinator. A single step runs at once. With
 * an approval first, the approve step shows "Step 1 of 2" with the exact amount it allows, and the
 * button of its Confirmed state ([afterAllowLabel]) starts the second step, so the person always
 * sees one transaction at a time and the second one never starts by itself.
 */
object TxPlanRunner {
    /**
     * [allowAmountText] is the exact amount of the approval as text without the unit. [last] builds
     * the context of the final step from its position, so the flow can add the success copy and the
     * button that leaves the flow. Returns false when another transaction is in flight.
     */
    fun run(
        coordinator: TransactionCoordinator,
        from: String,
        steps: List<TxStep>,
        action: TxAction,
        allowAmountText: String,
        @StringRes afterAllowLabel: Int,
        last: (stepIndex: Int, stepCount: Int) -> TxContext,
    ): Boolean {
        if (steps.size == 1) return coordinator.begin(last(1, 1), steps.single(), from)
        val approve = steps.first()
        val final = steps.last()
        val second = last(2, 2)
        val first = TxContext(
            action = action,
            stepIndex = 1,
            stepCount = 2,
            confirmedPrimary = TxPrimary(afterAllowLabel) { coordinator.begin(second, final, from) },
            allowAmountText = allowAmountText,
            onBackToRequests = second.onBackToRequests,
        )
        return coordinator.begin(first, approve, from)
    }
}
