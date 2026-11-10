package com.latifi.wallet.ui.request

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error as semanticsError
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.latifi.wallet.R
import com.latifi.wallet.core.FieldError
import com.latifi.wallet.core.PercentFormat
import com.latifi.wallet.core.RequestField
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.ui.common.currentLocale
import com.latifi.wallet.ui.components.AmountBlock
import com.latifi.wallet.ui.components.ChipOption
import com.latifi.wallet.ui.components.ConfirmDialog
import com.latifi.wallet.ui.components.DetailRowData
import com.latifi.wallet.ui.components.DetailRows
import com.latifi.wallet.ui.components.FilterChipItem
import com.latifi.wallet.ui.components.LocalValueLine
import com.latifi.wallet.ui.components.PrimaryButton
import com.latifi.wallet.ui.components.ScreenScaffold
import com.latifi.wallet.ui.components.StatusFilterChips
import com.latifi.wallet.ui.components.StepProgress
import com.latifi.wallet.ui.components.TertiaryButton
import com.latifi.wallet.ui.components.localEstimateText
import com.latifi.wallet.ui.theme.LatiFiShapes
import com.latifi.wallet.ui.theme.LatiFiType
import com.latifi.wallet.ui.theme.MinTouchTarget
import com.latifi.wallet.ui.theme.Spacing

/** Everything S09 can ask for, so the screen stays stateless and a test can drive it. */
class RequestLoanActions(
    val onAmountChange: (String) -> Unit = {},
    val onAmountChip: (Int) -> Unit = {},
    val onRateChange: (String) -> Unit = {},
    val onTermSelect: (TermChoice) -> Unit = {},
    val onDaysChange: (String) -> Unit = {},
    val onDecrease: () -> Unit = {},
    val onIncrease: () -> Unit = {},
    val onBlur: (RequestField) -> Unit = {},
    val onReview: () -> Unit = {},
    val onBackToStep1: () -> Unit = {},
    val onLeaveRequested: () -> Unit = {},
    val onLeaveConfirmed: () -> Unit = {},
    val onStayEditing: () -> Unit = {},
    val onPublish: () -> Unit = {},
)

/**
 * S09 in two steps. Step 1 collects the amount, rate, term and installments with a visible label
 * and a helper under each field, errors as icon plus text, and the primary pinned above the
 * keyboard. Step 2 repeats the numbers the contract will use. The system Back and the up arrow ask
 * "Leave this request?" when anything was typed, and "Keep editing request" holds the default focus.
 */
@Composable
fun RequestLoanScreen(
    state: RequestLoanUiState,
    actions: RequestLoanActions,
    modifier: Modifier = Modifier,
) {
    // The system Back follows the step: step 2 returns to step 1, step 1 may ask before it leaves.
    BackHandler(enabled = !state.leaveDialog) {
        if (state.step == 2) actions.onBackToStep1() else actions.onLeaveRequested()
    }
    ScreenScaffold(
        title = stringResource(R.string.request_title),
        modifier = modifier,
        onBack = actions.onLeaveRequested,
        bottomBar = {
            if (state.step == 1) {
                PrimaryButton(
                    text = stringResource(R.string.request_review),
                    onClick = actions.onReview,
                    enabled = state.valid,
                    disabledReason = stringResource(R.string.request_disabled_reason),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton(
                        text = stringResource(R.string.request_publish),
                        onClick = actions.onPublish,
                        loading = state.waitingForIndexer,
                    )
                    TertiaryButton(
                        text = stringResource(R.string.action_back),
                        onClick = actions.onBackToStep1,
                        fillMaxWidth = true,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            StepProgress(step = state.step, total = 2)
            if (state.step == 1) Step1(state, actions) else Step2(state)
        }
    }
    if (state.leaveDialog) {
        ConfirmDialog(
            title = stringResource(R.string.leave_title),
            body = stringResource(R.string.leave_body),
            safeLabel = stringResource(R.string.leave_stay),
            onSafe = actions.onStayEditing,
            confirmLabel = stringResource(R.string.leave_confirm),
            onConfirm = actions.onLeaveConfirmed,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1(state: RequestLoanUiState, actions: RequestLoanActions) {
    Text(
        text = stringResource(R.string.request_step1),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    if (state.submitErrorCount > 0) {
        Text(
            text = pluralStringResource(R.plurals.profile_errors_count, state.submitErrorCount, state.submitErrorCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }

    // Amount, its shortcut chips and the local estimate under the field.
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FormField(
            value = state.amount,
            onValueChange = actions.onAmountChange,
            label = R.string.request_amount,
            helper = R.string.request_amount_help,
            error = state.errors[RequestField.AMOUNT]?.let { stringResource(R.string.err_amount_range) },
            keyboardType = KeyboardType.Decimal,
            onBlur = { actions.onBlur(RequestField.AMOUNT) },
        )
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val group = stringResource(R.string.request_amount)
            RequestLoanViewModel.AMOUNT_CHIPS.forEach { tokens ->
                FilterChipItem(
                    group = group,
                    text = tokens.toString(),
                    selected = state.amount == tokens.toString(),
                    onClick = { actions.onAmountChip(tokens) },
                )
            }
        }
        val estimate = amountEstimate(state)
        if (estimate != null) LocalValueLine(estimate)
    }

    FormField(
        value = state.rate,
        onValueChange = actions.onRateChange,
        label = R.string.request_rate,
        helper = R.string.request_rate_help,
        error = state.errors[RequestField.RATE]?.let { stringResource(R.string.err_rate_range, "0.01", "50") },
        keyboardType = KeyboardType.Decimal,
        onBlur = { actions.onBlur(RequestField.RATE) },
    )

    TermSection(state, actions)

    InstallmentsStepper(state.installments, actions)
}

/** The local estimate of the typed amount, or null while it is not a number the calculator accepts. */
@Composable
private fun amountEstimate(state: RequestLoanUiState): LocalValue? {
    val parsed = remember(state.amount) {
        (TokenAmount.parse(state.amount) as? com.latifi.wallet.core.AmountParse.Valid)?.amount
    } ?: return null
    return LocalValueProvider.valueFrom(state.rates, parsed, state.currency)
}

@Composable
private fun TermSection(state: RequestLoanUiState, actions: RequestLoanActions) {
    val termLabel = stringResource(R.string.request_term)
    val options = listOf(
        ChipOption(TermChoice.Days30, pluralStringResource(R.plurals.term_days, 30, 30)),
        ChipOption(TermChoice.Days45, pluralStringResource(R.plurals.term_days, 45, 45)),
        ChipOption(TermChoice.Days60, pluralStringResource(R.plurals.term_days, 60, 60)),
        ChipOption(TermChoice.Other, stringResource(R.string.request_term_other)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        StatusFilterChips(
            label = termLabel,
            options = options,
            selected = state.term,
            onSelect = actions.onTermSelect,
        )
        if (state.term == TermChoice.Other) {
            FormField(
                value = state.otherDays,
                onValueChange = actions.onDaysChange,
                label = R.string.request_term_days,
                helper = null,
                error = state.errors[RequestField.TERM]?.let { stringResource(R.string.err_term_range) },
                keyboardType = KeyboardType.Number,
                onBlur = { actions.onBlur(RequestField.TERM) },
            )
        } else if (state.errors[RequestField.TERM] == FieldError.EMPTY) {
            ErrorLine(stringResource(R.string.err_term_range))
        }
    }
}

/** Decrease and Increase are 48dp buttons around a value that is a polite live region. */
@Composable
private fun InstallmentsStepper(installments: Int, actions: RequestLoanActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.request_installments),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconButton(
                onClick = actions.onDecrease,
                enabled = installments > RequestLoanViewModel.MIN_INSTALLMENTS,
                modifier = Modifier.size(MinTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_remove),
                    contentDescription = stringResource(R.string.request_installments_dec),
                )
            }
            Text(
                text = pluralStringResource(R.plurals.installments_count, installments, installments),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = Spacing.sm)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
            IconButton(
                onClick = actions.onIncrease,
                enabled = installments < RequestLoanViewModel.MAX_INSTALLMENTS,
                modifier = Modifier.size(MinTouchTarget),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.request_installments_inc),
                )
            }
        }
    }
}

@Composable
private fun Step2(state: RequestLoanUiState) {
    val preview = state.preview ?: return
    val locale = currentLocale()
    Text(
        text = stringResource(R.string.request_review_title),
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() },
    )
    // The total comes from the conversion endpoint, so it is the authoritative local value.
    AmountBlock(
        label = stringResource(R.string.detail_total),
        amount = TokenAmount(preview.totalDue),
        local = state.totalLocal,
        style = LatiFiType.Amount.Display,
    )
    val rows = buildList {
        add(rowOf(stringResource(R.string.group_amount), preview.principal, state))
        add(
            DetailRowData(
                stringResource(R.string.detail_rate),
                stringResource(R.string.rate_per_year, PercentFormat.fromBasisPoints(preview.bps, locale)),
            ),
        )
        add(DetailRowData(stringResource(R.string.detail_term), pluralStringResource(R.plurals.term_days, preview.termDays, preview.termDays)))
        add(
            DetailRowData(
                stringResource(R.string.detail_installments),
                pluralStringResource(R.plurals.installments_count, preview.installments, preview.installments),
            ),
        )
        // The last installment absorbs the remainder, so it can differ by a few base units.
        preview.installmentAmounts.forEachIndexed { index, units ->
            val label = if (preview.installmentAmounts.size == 1) {
                stringResource(R.string.request_each)
            } else {
                stringResource(R.string.request_each) + " " + (index + 1) + "/" + preview.installmentAmounts.size
            }
            add(rowOf(label, units, state))
        }
        add(rowOf(stringResource(R.string.detail_total), preview.totalDue, state, authoritative = true))
    }
    DetailRows(rows)
    Text(
        text = stringResource(R.string.request_first_due),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = stringResource(R.string.network_fee_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.needsWallet) ErrorLine(stringResource(R.string.tx_fail_session))
    if (state.waitingForIndexer) {
        Text(
            text = stringResource(R.string.loading_loans),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun rowOf(label: String, units: java.math.BigInteger, state: RequestLoanUiState, authoritative: Boolean = false): DetailRowData {
    val value = if (authoritative) state.totalLocal else LocalValueProvider.valueFrom(state.rates, TokenAmount(units), state.currency)
    return DetailRowData(
        label = label,
        value = TokenAmount(units).format(currentLocale()),
        secondary = localEstimateText(value),
    )
}

@Composable
private fun ErrorLine(text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(
            painter = painterResource(R.drawable.ic_error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** A text field with a persistent label, the helper under it and the error as icon plus text. */
@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    helper: Int?,
    error: String?,
    keyboardType: KeyboardType,
    onBlur: () -> Unit,
) {
    var hadFocus by remember { mutableStateOf(false) }
    val required = stringResource(R.string.label_required)
    val helperText = helper?.let { stringResource(it) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .onFocusChanged { focus ->
                if (hadFocus && !focus.isFocused) onBlur()
                if (focus.isFocused) hadFocus = true
            }
            .semantics { if (error != null) semanticsError(error) },
        label = { Text(text = stringResource(label)) },
        supportingText = {
            if (error != null) {
                ErrorLine(error)
            } else {
                Text(text = if (helperText != null) "$required. $helperText" else required)
            }
        },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions.Default,
        shape = LatiFiShapes.field,
    )
}
