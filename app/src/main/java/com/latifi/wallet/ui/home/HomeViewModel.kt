package com.latifi.wallet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.latifi.wallet.core.LocalCurrency
import com.latifi.wallet.core.TokenAmount
import com.latifi.wallet.domain.chain.ChainReadRepository
import com.latifi.wallet.domain.chain.FeePolicy
import com.latifi.wallet.domain.error.AppError
import com.latifi.wallet.domain.error.AppException
import com.latifi.wallet.domain.loan.LoanQuery
import com.latifi.wallet.domain.loan.LoanRepository
import com.latifi.wallet.domain.loan.LoanSort
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.domain.model.BorrowerReputation
import com.latifi.wallet.domain.model.InstallmentSchedule
import com.latifi.wallet.domain.model.Loan
import com.latifi.wallet.domain.model.LoanStatus
import com.latifi.wallet.domain.prefs.PrefsStore
import com.latifi.wallet.domain.profile.SessionHints
import com.latifi.wallet.domain.rates.LocalValueProvider
import com.latifi.wallet.domain.rates.RatesState
import com.latifi.wallet.domain.reputation.ReputationRepository
import com.latifi.wallet.domain.session.SessionStore
import com.latifi.wallet.domain.tx.BalanceRefresh
import com.latifi.wallet.ui.common.ErrorCopy
import com.latifi.wallet.ui.common.Loadable
import com.latifi.wallet.ui.nav.Overlays
import com.latifi.wallet.ui.overlay.OverlayController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.time.Instant
import javax.inject.Inject

/** The parts of Home that load on their own. A failure in one never hides the others. */
enum class HomeSection { Balance, Reputation, NextInstallment, OpenRequests, NextPayment }

/** The loan behind a "next" card: what is still owed on the current installment and when it falls due. */
data class NextDueCard(val loanId: BigInteger, val amount: TokenAmount, val dueAt: Instant?)

/**
 * What Home shows. Each field is the state of one section. Borrower sections are [reputation] and
 * [nextInstallment]. Lender sections are [openRequests] and [nextPayment]. [reputation] is
 * `Success(null)` when the API has no reputation for the address yet, which draws no chip.
 */
data class HomeUiState(
    val role: AppRole = AppRole.Borrower,
    val balance: Loadable<TokenAmount> = Loadable.Loading,
    val reputation: Loadable<BorrowerReputation?> = Loadable.Loading,
    val nextInstallment: Loadable<NextDueCard?> = Loadable.Loading,
    val openRequests: Loadable<Long> = Loadable.Loading,
    val nextPayment: Loadable<NextDueCard?> = Loadable.Loading,
    /** True when the POL balance is strictly below the fee threshold. A failed read never sets it. */
    val lowFee: Boolean = false,
    /** The name to greet, consumed once from [SessionHints] when this screen first opened. */
    val greeting: String? = null,
    val rates: RatesState = RatesState.Loading,
    val currency: LocalCurrency = LocalCurrency.PEN,
)

sealed interface HomeEvent {
    /** The Reload button of one section. */
    data class Reload(val section: HomeSection) : HomeEvent

    /** The tertiary Get test coins action: asks for the coins sheet, which plan 05-12 draws. */
    data object GetCoins : HomeEvent
}

/**
 * S06 for both roles. Every read uses the address of the stored session, never a route argument.
 * Each section keeps its own state and its own job, so one failed read shows an error in that
 * section only and Reload runs just that section. The role comes from the stored choice, so a
 * role change loads the sections of the new role. The greeting is consumed from [SessionHints]
 * once when this view model is created, so it shows once and a later visit gets nothing.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val chain: ChainReadRepository,
    private val loans: LoanRepository,
    private val reputation: ReputationRepository,
    private val sessions: SessionStore,
    private val rates: LocalValueProvider,
    private val overlays: OverlayController,
    hints: SessionHints,
    prefs: PrefsStore,
    balanceRefresh: BalanceRefresh = BalanceRefresh(),
) : ViewModel() {
    private data class Sections(
        val balance: Loadable<TokenAmount> = Loadable.Loading,
        val reputation: Loadable<BorrowerReputation?> = Loadable.Loading,
        val nextInstallment: Loadable<NextDueCard?> = Loadable.Loading,
        val openRequests: Loadable<Long> = Loadable.Loading,
        val nextPayment: Loadable<NextDueCard?> = Loadable.Loading,
        val lowFee: Boolean = false,
    )

    private val sections = MutableStateFlow(Sections())
    private val role = MutableStateFlow(AppRole.Borrower)
    private val greeting = MutableStateFlow(hints.consume())
    private val jobs = mutableMapOf<HomeSection, Job>()

    val state: StateFlow<HomeUiState> = combine(
        sections,
        role,
        greeting,
        rates.rates,
        prefs.currency,
    ) { sections, role, greeting, ratesState, currency ->
        HomeUiState(
            role = role,
            balance = sections.balance,
            reputation = sections.reputation,
            nextInstallment = sections.nextInstallment,
            openRequests = sections.openRequests,
            nextPayment = sections.nextPayment,
            lowFee = sections.lowFee,
            greeting = greeting,
            rates = ratesState,
            currency = currency,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState(greeting = greeting.value))

    init {
        viewModelScope.launch { rates.ensure() }
        // A confirmed test coin request asks for a fresh balance, so Home shows it without a reload.
        viewModelScope.launch { balanceRefresh.ticks.collect { load(HomeSection.Balance) } }
        viewModelScope.launch {
            prefs.role.map { it ?: AppRole.Borrower }.distinctUntilChanged().collect { chosen ->
                role.value = chosen
                sectionsOf(chosen).forEach(::load)
            }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.Reload -> load(event.section)
            HomeEvent.GetCoins -> overlays.show(Overlays.COINS)
        }
    }

    private fun sectionsOf(role: AppRole): List<HomeSection> = when (role) {
        AppRole.Borrower -> listOf(HomeSection.Balance, HomeSection.Reputation, HomeSection.NextInstallment)
        AppRole.Lender -> listOf(HomeSection.Balance, HomeSection.OpenRequests, HomeSection.NextPayment)
    }

    private fun load(section: HomeSection) {
        jobs[section]?.cancel()
        sections.update { it.markLoading(section) }
        jobs[section] = viewModelScope.launch {
            val address = sessions.session.first { it != null }!!.address
            when (section) {
                HomeSection.Balance -> loadBalance(address)
                HomeSection.Reputation -> loadReputation(address)
                HomeSection.NextInstallment -> loadNextInstallment(address)
                HomeSection.OpenRequests -> loadOpenRequests()
                HomeSection.NextPayment -> loadNextPayment(address)
            }
        }
    }

    private fun Sections.markLoading(section: HomeSection): Sections = when (section) {
        HomeSection.Balance -> copy(balance = Loadable.Loading)
        HomeSection.Reputation -> copy(reputation = Loadable.Loading)
        HomeSection.NextInstallment -> copy(nextInstallment = Loadable.Loading)
        HomeSection.OpenRequests -> copy(openRequests = Loadable.Loading)
        HomeSection.NextPayment -> copy(nextPayment = Loadable.Loading)
    }

    private suspend fun loadBalance(address: String) {
        val tokens = chain.tokenBalance(address)
        // The fee balance only decides the banner. A failed read means "unknown", and unknown shows nothing.
        val low = chain.polBalance(address).map { FeePolicy.isLow(it) }.getOrDefault(false)
        sections.update {
            it.copy(
                balance = tokens.fold({ amount -> Loadable.Success(amount) }, { error -> Loadable.Error(ErrorCopy.kindOf(error)) }),
                lowFee = low,
            )
        }
    }

    private suspend fun loadReputation(address: String) {
        val result = reputation.get(address)
        val next: Loadable<BorrowerReputation?> = result.fold(
            onSuccess = { Loadable.Success(BorrowerReputation(it.score, it.level)) },
            onFailure = { error ->
                // No reputation yet is not a failure: Home simply draws no chip.
                if ((error as? AppException)?.error == AppError.ReputationNotFound) {
                    Loadable.Success(null)
                } else {
                    Loadable.Error(ErrorCopy.kindOf(error))
                }
            },
        )
        sections.update { it.copy(reputation = next) }
    }

    private suspend fun loadNextInstallment(address: String) {
        val result = loans.list(
            LoanQuery(status = LoanStatus.ACTIVE, borrower = address, sort = LoanSort.NextDue, size = 1),
        )
        sections.update { it.copy(nextInstallment = result.toCard()) }
    }

    private suspend fun loadOpenRequests() {
        val result = loans.list(LoanQuery(status = LoanStatus.OPEN, size = 1))
        val next = result.fold(
            onSuccess = { Loadable.Success(it.totalItems) },
            onFailure = { Loadable.Error(ErrorCopy.kindOf(it)) },
        )
        sections.update { it.copy(openRequests = next) }
    }

    private suspend fun loadNextPayment(address: String) {
        val result = loans.list(
            LoanQuery(status = LoanStatus.ACTIVE, lender = address, sort = LoanSort.NextDue, size = 1),
        )
        sections.update { it.copy(nextPayment = result.toCard()) }
    }

    private fun Result<com.latifi.wallet.domain.model.Page<Loan>>.toCard(): Loadable<NextDueCard?> =
        fold(
            onSuccess = { page -> Loadable.Success(page.items.firstOrNull()?.toNextDueCard()) },
            onFailure = { Loadable.Error(ErrorCopy.kindOf(it)) },
        )

    private fun Loan.toNextDueCard(): NextDueCard =
        NextDueCard(loanId, TokenAmount(InstallmentSchedule.remainingOnCurrent(this)), nextDueDate)
}
