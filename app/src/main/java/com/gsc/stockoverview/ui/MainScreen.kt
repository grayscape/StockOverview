package com.gsc.stockoverview.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.ui.screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val title: String) {
    OVERALL("전체현황"),
    STOCK_WISE("종목별현황"),
    ACCOUNT_WISE("계좌별현황"),
    PERIOD_WISE("기간별현황"),
    TRADING_LOG("거래내역"),
    STOCK("종목"),
    PORTFOLIO("포트폴리오"),
    TRANSACTION_DETAIL("거래내역상세")
}

@Composable
fun MainScreen() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.OVERALL) }
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    val onOpenDrawer: () -> Unit = {
        scope.launch { drawerState.open() }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("데이터 초기화") },
            text = { Text("모든 거래 내역 및 종목 정보를 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                AppDatabase.getDatabase(context).clearAllData()
                            }
                            showDeleteConfirmDialog = false
                        }
                    }
                ) {
                    Text("초기화", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("전체 메뉴", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()
                
                // 일반 화면 메뉴
                Screen.entries.forEach { screen ->
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

                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                
                // DB 초기화 메뉴
                NavigationDrawerItem(
                    label = { Text("DB 초기화") },
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    selected = false,
                    onClick = {
                        showDeleteConfirmDialog = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedIconColor = MaterialTheme.colorScheme.error,
                        unselectedTextColor = MaterialTheme.colorScheme.error
                    )
                )
                Spacer(Modifier.height(12.dp))
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
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
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
                        icon = Icons.AutoMirrored.Filled.ListAlt,
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
                    Screen.STOCK -> StockScreen(onOpenDrawer)
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
