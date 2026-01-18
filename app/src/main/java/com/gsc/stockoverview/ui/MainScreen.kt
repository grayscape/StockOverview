package com.gsc.stockoverview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsc.stockoverview.ui.screen.*
import kotlinx.coroutines.launch

enum class Screen(val title: String) {
    OVERALL("전체현황"),
    STOCK_WISE("종목별현황"),
    ACCOUNT_WISE("계좌별현황"),
    PERIOD_WISE("기간별현황"),
    TRADING_LOG("거래내역"),
    PORTFOLIO("포트폴리오"),
    TRANSACTION_DETAIL("거래내역상세")
}

@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.OVERALL) }

    val onOpenDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("전체 메뉴", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                Screen.values().forEach { screen ->
                    NavigationDrawerItem(
                        label = { Text(screen.title) },
                        selected = currentScreen == screen,
                        onClick = {
                            currentScreen = screen
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    BottomNavItem(
                        selected = currentScreen == Screen.OVERALL,
                        onClick = { currentScreen = Screen.OVERALL },
                        icon = Icons.Default.Analytics,
                        label = "전체현황"
                    )
                    BottomNavItem(
                        selected = currentScreen == Screen.STOCK_WISE,
                        onClick = { currentScreen = Screen.STOCK_WISE },
                        icon = Icons.Default.TrendingUp,
                        label = "종목별"
                    )
                    BottomNavItem(
                        selected = currentScreen == Screen.ACCOUNT_WISE,
                        onClick = { currentScreen = Screen.ACCOUNT_WISE },
                        icon = Icons.Default.AccountBalance,
                        label = "계좌별"
                    )
                    BottomNavItem(
                        selected = currentScreen == Screen.PERIOD_WISE,
                        onClick = { currentScreen = Screen.PERIOD_WISE },
                        icon = Icons.Default.DateRange,
                        label = "기간별"
                    )
                    BottomNavItem(
                        selected = currentScreen == Screen.TRADING_LOG,
                        onClick = { currentScreen = Screen.TRADING_LOG },
                        icon = Icons.Default.ListAlt,
                        label = "거래내역"
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()).fillMaxSize()) {
                when (currentScreen) {
                    Screen.OVERALL -> OverallScreen(onOpenDrawer)
                    Screen.STOCK_WISE -> StockWiseScreen(onOpenDrawer)
                    Screen.ACCOUNT_WISE -> AccountWiseScreen(onOpenDrawer)
                    Screen.PERIOD_WISE -> PeriodWiseScreen(onOpenDrawer)
                    Screen.TRADING_LOG -> TransactionScreen(onOpenDrawer)
                    Screen.PORTFOLIO -> PortfolioScreen(onOpenDrawer)
                    Screen.TRANSACTION_DETAIL -> TransactionDetailScreen(onOpenDrawer)
                }
            }
        }
    }
}

@Composable
fun RowScope.BottomNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label, fontSize = 10.sp) }
    )
}
