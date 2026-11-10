package com.latifi.wallet.ui.nav

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.latifi.wallet.domain.model.AppRole
import com.latifi.wallet.ui.components.LocalMenuAction
import kotlinx.coroutines.launch

/** Drawer items are this high, so a 200 percent label still has room. */
private val DrawerItemHeight = 56.dp

/**
 * Role navigation that adapts to the room and to the text size.
 *
 * Compact width shows a bottom bar, medium and expanded width a rail (both through
 * [NavigationSuiteScaffold]). From font scale 1.3 up the suite layout is switched off and a
 * [ModalNavigationDrawer] takes over, opened from the Menu button that ScreenScaffold shows
 * when [LocalMenuAction] is set. A bar slot is about 90dp wide and a 200 percent label is not,
 * so the drawer is what keeps destination names whole.
 *
 * The selected item uses the accent: primary for icon and label on the secondary container.
 */
@Composable
fun AdaptiveNavScaffold(
    role: AppRole,
    selected: TabDestination,
    onSelect: (TabDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tabs = tabsFor(role)
    val largeText = isLargeTextMode(LocalDensity.current.fontScale)
    val colors = MaterialTheme.colorScheme

    val layoutType = if (largeText) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    }

    val barColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        indicatorColor = colors.secondaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )
    val railColors = NavigationRailItemDefaults.colors(
        selectedIconColor = colors.primary,
        selectedTextColor = colors.primary,
        indicatorColor = colors.secondaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )

    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = barColors,
        navigationRailItemColors = railColors,
    )

    val suite: @Composable () -> Unit = {
        NavigationSuiteScaffold(
            modifier = if (largeText) Modifier else modifier,
            layoutType = layoutType,
            containerColor = colors.background,
            contentColor = colors.onBackground,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = colors.surface,
                navigationRailContainerColor = colors.surface,
            ),
            navigationSuiteItems = {
                tabs.forEach { tab ->
                    item(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(painter = painterResource(tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        colors = itemColors,
                    )
                }
            },
            content = content,
        )
    }

    if (!largeText) {
        suite()
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openMenu: () -> Unit = remember(drawerState, scope) {
        { scope.launch { drawerState.open() } }
    }
    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = colors.surface) {
                tabs.forEach { tab ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(tab.label)) },
                        icon = { Icon(painter = painterResource(tab.icon), contentDescription = null) },
                        selected = tab == selected,
                        onClick = {
                            onSelect(tab)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier
                            .padding(NavigationDrawerItemDefaults.ItemPadding)
                            .heightIn(min = DrawerItemHeight),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = colors.secondaryContainer,
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            unselectedIconColor = colors.onSurfaceVariant,
                            unselectedTextColor = colors.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) {
        CompositionLocalProvider(LocalMenuAction provides openMenu) { suite() }
    }
}
