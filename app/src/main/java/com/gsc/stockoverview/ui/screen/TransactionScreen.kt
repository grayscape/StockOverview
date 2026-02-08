package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.repository.AccountStatusRepository
import com.gsc.stockoverview.data.repository.AccountStockStatusRepository
import com.gsc.stockoverview.data.repository.CommonCodeRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import com.gsc.stockoverview.ui.components.StockTable
import com.gsc.stockoverview.ui.components.StockTopAppBar
import com.gsc.stockoverview.ui.components.formatDate
import com.gsc.stockoverview.ui.components.formatDouble
import com.gsc.stockoverview.ui.components.formatStockName
import com.gsc.stockoverview.ui.viewmodel.CommonCodeViewModel
import com.gsc.stockoverview.ui.viewmodel.CommonCodeViewModelFactory
import com.gsc.stockoverview.ui.viewmodel.TransactionViewModel
import com.gsc.stockoverview.ui.viewmodel.TransactionViewModelFactory

@Composable
fun TransactionScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }

    val commonCodeViewModel: CommonCodeViewModel = viewModel(
        factory = CommonCodeViewModelFactory(CommonCodeRepository(database.commonCodeDao()))
    )
    val accountCodes by commonCodeViewModel.getCodesByParent("ACC_ROOT").collectAsState(initial = emptyList())

    val viewModel: TransactionViewModel = viewModel(
        factory = TransactionViewModelFactory(
            repository = TransactionRepository(
                database.transactionDao(),
                database.transactionRawDao(),
                database.tradingLogRawDao(),
                database.overseasTradingLogRawDao(),
                database.stockDao()
            ),
            accountStockStatusRepository = AccountStockStatusRepository(database.accountStockStatusDao()),
            accountRepository = AccountStatusRepository(database.accountStatusDao())
        )
    )

    val transactions by viewModel.transactionList.collectAsState(initial = emptyList())
    val selectedTab by viewModel.selectedTab.collectAsState()
    
    val tabs = remember(accountCodes) {
        listOf("전체") + accountCodes.map { it.name }
    }

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "거래내역",
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (tabs.size > 1) {
                ScrollableTabRow(
                    selectedTabIndex = if (tabs.indexOf(selectedTab) >= 0) tabs.indexOf(selectedTab) else 0,
                    edgePadding = 16.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            text = { 
                                Text(
                                    text = tab,
                                    style = MaterialTheme.typography.titleSmall
                                ) 
                            }
                        )
                    }
                }
            }

            val isAllTab = selectedTab == "전체"
            val headers = remember(isAllTab) {
                val baseHeaders = listOf(
                    "매매일자", "구분", "거래명", "거래금액", "단가", "거래량",
                    "수수료", "세금", "손익금액", "수익률", "거래종류상세"
                )
                if (isAllTab) listOf("계좌") + baseHeaders else baseHeaders
            }

            StockTable(
                headers = headers,
                items = transactions,
                cellContent = { item ->
                    val baseContent = listOf(
                        formatDate(item.tradeDate),
                        item.type,
                        formatStockName(item.transactionName),
                        formatDouble(item.amount),
                        formatDouble(item.price),
                        formatDouble(item.volume),
                        formatDouble(item.fee),
                        formatDouble(item.tax),
                        formatDouble(item.profitLoss),
                        formatDouble(item.yield) + "%",
                        item.typeDetail
                    )
                    if (isAllTab) listOf(item.account) + baseContent else baseContent
                }
            )
        }
    }
}
