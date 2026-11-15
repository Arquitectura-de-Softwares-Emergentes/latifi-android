package com.latifi.wallet.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.util.Locale

/** The locale the app shows right now, which follows the language the person picked in Profile. */
@Composable
@ReadOnlyComposable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** Today in the device zone, read once per composition so a date never flips while a screen is open. */
@Composable
fun rememberToday(): LocalDate = remember { LocalDate.now() }
