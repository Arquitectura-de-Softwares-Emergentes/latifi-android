package com.latifi.wallet.ui.request

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.R
import com.latifi.wallet.core.FieldError
import com.latifi.wallet.core.LoanPreview
import com.latifi.wallet.core.LoanRequestCalculator
import com.latifi.wallet.core.LoanRequestResult
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.RequestField
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.ProfileGate
import com.latifi.wallet.domain.rates.ExchangeRateRepository
import com.latifi.wallet.domain.rates.LocalValue
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.tx.IndexerLagWaiter
import com.latifi.wallet.domain.tx.TransactionCoordinator
import com.latifi.wallet.domain.tx.TxAction
import com.latifi.wallet.domain.tx.TxContext
import com.latifi.wallet.domain.tx.TxPlans
import com.latifi.wallet.domain.tx.TxPrimary
import com.latifi.wallet.domain.tx.TxState
import com.latifi.wallet.domain.wallet.WalletGateway
import com.latifi.wallet.domain.wallet.WalletState
import com.latifi.wallet.ui.profile.runProfileGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/** The term chips. [None] means the person has not picked one yet. */
enum class TermChoice(val days: Int?) {
    None(null),
    Days30(30),
    Days45(45),
    Days60(60),
    Other(null),
}

/**
 * What S09 draws. The inputs are the typed text, so a half typed number is never rewritten.
 * [errors] holds only the errors that may show now (a field the person left, or any field after
 * Review was tried). [preview] is the calculator result of the current inputs, null while they are
 * not all valid, and [valid] is what enables Review request.
 */
data class RequestLoanUiState(
    val step: Int = 1,
    val amount: String = "",
    val rate: String = RequestLoanViewModel.DEFAULT_RATE,
    val term: TermChoice = TermChoice.None,
    val otherDays: String = "",
    val installments: Int = 1,
    val errors: Map<RequestField, FieldError> = emptyMap(),
    /** The number of invalid fields found when Review was tried, announced once. Zero otherwise. */
    val submitErrorCount: Int = 0,
    val preview: LoanPreview? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
    /** The authoritative local value of the total to repay, from the conversion endpoint. */
    val totalLocal: LocalValue = LocalValue.Loading,
    val leaveDialog: Boolean = false,
    /** True while the app waits for the indexer to list the new loan after Confirmed. */
    val waitingForIndexer: Boolean = false,
    val needsWallet: Boolean = false,
) {
    val valid: Boolean get() = preview != null

    /** Any input at all. A dirty form asks before it closes. */
    val dirty: Boolean
        get() = amount.isNotEmpty() || rate != RequestLoanViewModel.DEFAULT_RATE || term != TermChoice.None ||
            otherDays.isNotEmpty() || installments != 1

    /** The term in days the calculator gets: 0 when it is missing or typed wrongly. */
    val termDays: Int
        get() = term.days ?: if (term == TermChoice.Other) otherDays.toIntOrNull() ?: 0 else 0
}

sealed interface RequestLoanEffect {
    /** The person leaves S09 with nothing published. */
    data object Leave : RequestLoanEffect

    /** After Confirmed and the indexer wait: show the Loans tab. */
    data object OpenLoans : RequestLoanEffect

    /** The profile is missing at Publish. The form opens and the values stay here. */
    data object OpenProfileForm : RequestLoanEffect

    /** The profile check failed (offline or server). Nothing was published. */
    data class ProfileCheckFailed(val error: Throwable) : RequestLoanEffect

    /** Moves focus to the first invalid field after Review was tried. */
    data class FocusField(val field: RequestField) : RequestLoanEffect
}

/**
 * S09. Every input lives in the [SavedStateHandle], so the values survive process death and a
 * failed transaction. Validation and the numbers come from [LoanRequestCalculator], the same code
 * the contract math tests cover. Publish checks the profile gate again as a safety net (the entry
 * callbacks checked it first), then begins one `requestLoan` step through the coordinator: the
 * call moves no tokens, so there is no approval. After Confirmed the primary of the sheet waits
 * for the indexer to list the loan (3 second checks for up to 30 seconds) and then asks the route
 * to open Loans.
 */
@HiltViewModel
class RequestLoanViewModel @Inject constructor(
    private val saved: SavedStateHandle,
    private val coordinator: TransactionCoordinator,
    private val wallet: WalletGateway,
    private val loans: LoanRepository,
    private val rates: LocalValueProvider,
    private val exchange: ExchangeRateRepository,
    private val waiter: IndexerLagWaiter,
    prefs: PrefsStore,
    val sessions: SessionStore,
    val gate: ProfileGate,
) : ViewModel() {
    private val mutableState = MutableStateFlow(restore())
    val state: StateFlow<RequestLoanUiState> = mutableState.asStateFlow()

    private val effectChannel = Channel<RequestLoanEffect>(Channel.BUFFERED)
    val effects: Flow<RequestLoanEffect> = effectChannel.receiveAsFlow()

    /** The fields the person has left, so a half typed value is not flagged while typing. */
    private var blurred: Set<RequestField> = saved.get<String>(KEY_BLURRED).orEmpty().split(',')
        .mapNotNull { name -> RequestField.entries.firstOrNull { it.name == name } }.toSet()
    private var submitted = saved.get<Boolean>(KEY_SUBMITTED) ?: false
    private var publishStarted = false

    init {
        publishValidation()
        viewModelScope.launch { rates.rates.collect { r -> mutableState.value = mutableState.value.copy(rates = r) } }
        viewModelScope.launch {
            prefs.currency.collect { c ->
                mutableState.value = mutableState.value.copy(currency = c)
                if (mutableState.value.step == 2) loadTotalLocal()
            }
        }
        viewModelScope.launch { rates.ensure() }
        if (mutableState.value.step == 2 && mutableState.value.preview != null) loadTotalLocal()
    }

    // ---- inputs ----

    fun onAmountChange(text: String) = edit { copy(amount = text.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_AMOUNT_TEXT)) }

    /** A shortcut chip sets the field, and a chip is a deliberate choice, so the field counts as left. */
    fun onAmountChip(tokens: Int) {
        blurred = blurred + RequestField.AMOUNT
        edit { copy(amount = tokens.toString()) }
    }

    fun onRateChange(text: String) = edit { copy(rate = text.filter { it.isDigit() || it == '.' || it == ',' }.take(MAX_RATE_TEXT)) }

    fun onTermSelect(choice: TermChoice) {
        blurred = blurred + RequestField.TERM
        edit { copy(term = choice) }
    }

    fun onDaysChange(text: String) = edit { copy(otherDays = text.filter { it.isDigit() }.take(MAX_DAYS_TEXT)) }

    fun onDecrease() = edit { copy(installments = (installments - 1).coerceAtLeast(MIN_INSTALLMENTS)) }

    fun onIncrease() = edit { copy(installments = (installments + 1).coerceAtMost(MAX_INSTALLMENTS)) }

    /** The person left [field]: its error may show now. */
    fun onBlur(field: RequestField) {
        if (field in blurred) return
        blurred = blurred + field
        publishValidation()
    }

    // ---- steps ----

    /** Review request. With an invalid form it shows every error, counts them and focuses the first. */
    fun onReview() {
        val current = mutableState.value
        if (current.preview == null) {
            submitted = true
            persistFlags()
            val errors = evaluate(current).second
            publishValidation(submitErrorCount = errors.size)
            errors.keys.firstOrNull()?.let { effectChannel.trySend(RequestLoanEffect.FocusField(it)) }
            return
        }
        setStep(2)
        loadTotalLocal()
    }

    /** Back on step 2 returns to step 1 with every value intact. */
    fun onBackToStep1() = setStep(1)

    // ---- leaving ----

    /** The up arrow or the system Back: ask first when something was typed. */
    fun onLeaveRequested() {
        if (mutableState.value.dirty) {
            mutableState.value = mutableState.value.copy(leaveDialog = true)
        } else {
            effectChannel.trySend(RequestLoanEffect.Leave)
        }
    }

    fun onLeaveConfirmed() {
        mutableState.value = mutableState.value.copy(leaveDialog = false)
        effectChannel.trySend(RequestLoanEffect.Leave)
    }

    fun onStayEditing() {
        mutableState.value = mutableState.value.copy(leaveDialog = false)
    }

    // ---- publishing ----

    /** Publish request: profile gate first, then one `requestLoan` step. */
    fun onPublish() {
        val valid = evaluate(mutableState.value).first as? LoanRequestResult.Valid ?: return
        if (publishStarted) return
        publishStarted = true
        viewModelScope.launch {
            try {
                runProfileGate(
                    gate = gate,
                    session = sessions,
                    onReady = { begin(valid) },
                    onMissing = { effectChannel.trySend(RequestLoanEffect.OpenProfileForm) },
                    onError = { effectChannel.trySend(RequestLoanEffect.ProfileCheckFailed(it)) },
                )
            } finally {
                publishStarted = false
            }
        }
    }

    private fun begin(valid: LoanRequestResult.Valid) {
        val from = (wallet.state.value as? WalletState.Connected)?.address
        if (from == null) {
            mutableState.value = mutableState.value.copy(needsWallet = true)
            return
        }
        mutableState.value = mutableState.value.copy(needsWallet = false)
        val context = TxContext(
            action = TxAction.Request,
            confirmedPrimary = TxPrimary(R.string.request_view_loans) { onViewLoans() },
            successTitleRes = R.string.request_success_title,
            successBodyRes = R.string.request_success_body,
        )
        coordinator.begin(context, TxPlans.requestLoan(valid.calldata), from)
    }

    /** The primary of the Confirmed sheet: wait for the indexer to list the loan, then open Loans. */
    private fun onViewLoans() {
        val loanId: BigInteger? = (coordinator.state.value as? TxState.Confirmed)?.loanId
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(waitingForIndexer = true)
            if (loanId != null) waiter.await { loans.get(loanId).isSuccess }
            mutableState.value = mutableState.value.copy(waitingForIndexer = false)
            effectChannel.trySend(RequestLoanEffect.OpenLoans)
        }
    }

    // ---- internals ----

    private fun edit(change: RequestLoanUiState.() -> RequestLoanUiState) {
        mutableState.value = mutableState.value.change()
        persistInputs()
        publishValidation()
    }

    private fun setStep(step: Int) {
        mutableState.value = mutableState.value.copy(step = step, totalLocal = LocalValue.Loading)
        saved[KEY_STEP] = step
    }

    private fun loadTotalLocal() {
        val total = mutableState.value.preview?.totalDue ?: return
        val currency = mutableState.value.currency
        viewModelScope.launch {
            val result = exchange.convert(TokenAmount(total), currency).fold(
                onSuccess = { LocalValue.Value(it.value, currency, it.stale, it.asOf) },
                onFailure = { LocalValue.Unavailable },
            )
            mutableState.value = mutableState.value.copy(totalLocal = result)
        }
    }

    /** The calculator result and the errors a person may see now. */
    private fun evaluate(current: RequestLoanUiState): Pair<LoanRequestResult, Map<RequestField, FieldError>> {
        val result = LoanRequestCalculator.calculate(current.amount, current.rate, current.termDays, current.installments)
        val errors = (result as? LoanRequestResult.Invalid)?.errors.orEmpty().toMutableMap()
        // A term that was not chosen is an empty field, not a value out of range.
        if (current.term == TermChoice.None && RequestField.TERM in errors) errors[RequestField.TERM] = FieldError.EMPTY
        return result to errors
    }

    private fun publishValidation(submitErrorCount: Int = 0) {
        val current = mutableState.value
        val (result, errors) = evaluate(current)
        val visible = errors.filterKeys { submitted || it in blurred }
        mutableState.value = current.copy(
            preview = (result as? LoanRequestResult.Valid)?.preview,
            errors = visible,
            submitErrorCount = submitErrorCount,
            // Back on step 1 with a changed input: the review is stale until the form is valid again.
            step = if (result is LoanRequestResult.Valid) current.step else 1,
        )
        persistFlags()
    }

    private fun persistInputs() {
        val s = mutableState.value
        saved[KEY_AMOUNT] = s.amount
        saved[KEY_RATE] = s.rate
        saved[KEY_TERM] = s.term.name
        saved[KEY_DAYS] = s.otherDays
        saved[KEY_INSTALLMENTS] = s.installments
    }

    private fun persistFlags() {
        saved[KEY_BLURRED] = blurred.joinToString(",") { it.name }
        saved[KEY_SUBMITTED] = submitted
    }

    private fun restore(): RequestLoanUiState = RequestLoanUiState(
        step = saved.get<Int>(KEY_STEP) ?: 1,
        amount = saved.get<String>(KEY_AMOUNT).orEmpty(),
        rate = saved.get<String>(KEY_RATE) ?: DEFAULT_RATE,
        term = saved.get<String>(KEY_TERM)?.let { name -> TermChoice.entries.firstOrNull { it.name == name } } ?: TermChoice.None,
        otherDays = saved.get<String>(KEY_DAYS).orEmpty(),
        installments = (saved.get<Int>(KEY_INSTALLMENTS) ?: 1).coerceIn(MIN_INSTALLMENTS, MAX_INSTALLMENTS),
    )

    companion object {
        /** The documented default rate, in percent a year. It is stored as 1000 basis points. */
        const val DEFAULT_RATE = "10.00"
        const val MIN_INSTALLMENTS = 1
        const val MAX_INSTALLMENTS = 6

        /** The shortcut amounts of the chips, in whole tUSD. */
        val AMOUNT_CHIPS = listOf(50, 100, 250, 500)

        private const val MAX_AMOUNT_TEXT = 12
        private const val MAX_RATE_TEXT = 6
        private const val MAX_DAYS_TEXT = 3

        private const val KEY_STEP = "step"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_RATE = "rate"
        private const val KEY_TERM = "term"
        private const val KEY_DAYS = "days"
        private const val KEY_INSTALLMENTS = "installments"
        private const val KEY_BLURRED = "blurred"
        private const val KEY_SUBMITTED = "submitted"
    }
}
