@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.latifi.wallet.ui.wallet

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController

/**
 * The only file that imports Material 2. The AppKit wallet picker is a Material 2 bottom sheet
 * destination, so the NavController must come from a bottom sheet navigator and the content must
 * sit inside a [ModalBottomSheetLayout]. The host does not create a NavHost: the one NavHost of the
 * app lives in `LatiFiNavHost`, which receives this controller and adds the AppKit graph. Everything
 * else in the app uses Material 3. AppKit is third party UI and is tracked in docs/EXCEPTIONS.md.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppKitHost(content: @Composable (NavHostController) -> Unit) {
    val sheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(sheetNavigator)
    ModalBottomSheetLayout(sheetNavigator) {
        content(navController)
    }
}
