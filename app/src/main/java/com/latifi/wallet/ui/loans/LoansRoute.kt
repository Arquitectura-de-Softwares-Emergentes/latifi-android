package com.latifi.wallet.ui.loans

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** The Loans tab body. */
@Composable
fun LoansRoute() {
    val viewModel: LoansViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoansScreen(state = state, onOpenLoan = {})
}
