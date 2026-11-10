@file:Suppress("UsingMaterialAndMaterial3Libraries")

package com.latifi.wallet.ui.wallet

import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.navigation.ModalBottomSheetLayout
import androidx.compose.material.navigation.rememberBottomSheetNavigator
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reown.appkit.ui.appKitGraph

/**
 * The only file that imports Material 2. The AppKit wallet picker is a Material 2 bottom
 * sheet destination, so it needs this host. Everything else in the app uses Material 3.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AppKitHost(content: @Composable (NavController) -> Unit) {
    val sheetNavigator = rememberBottomSheetNavigator()
    val navController = rememberNavController(sheetNavigator)
    ModalBottomSheetLayout(sheetNavigator) {
        NavHost(navController, startDestination = "app") {
            composable("app") { content(navController) }
            appKitGraph(navController)
        }
    }
}
