package com.latifi.wallet.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Home tab body. [onBrowse] selects the Invest tab, which only a lender sees.
 */
@Composable
fun HomeRoute(onBrowse: () -> Unit) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        // The Pay installment screen is registered by plan 05-12, which wires this button.
        onPay = {},
        // The Request a loan screen is registered by plan 05-12, which wires this button.
        onRequestLoan = {},
        onBrowse = onBrowse,
    )
}
