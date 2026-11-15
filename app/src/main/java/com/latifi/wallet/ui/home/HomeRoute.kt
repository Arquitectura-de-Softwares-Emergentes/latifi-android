package com.latifi.wallet.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigInteger

/**
 * The Home tab body. [onBrowse] selects the Invest tab, which only a lender sees.
 */
@Composable
fun HomeRoute(onBrowse: () -> Unit, onRequestLoan: () -> Unit = {}, onPay: (BigInteger) -> Unit = {}) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onPay = onPay,
        onRequestLoan = onRequestLoan,
        onBrowse = onBrowse,
    )
}
