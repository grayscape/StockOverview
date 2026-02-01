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
import com.gsc.stockoverview.data.repository.OverseasTradingLogRawRepository
import com.gsc.stockoverview.data.repository.StockRepository
import com.gsc.stockoverview.data.repository.TradingLogRawRepository
import com.gsc.stockoverview.data.repository.TransactionRawRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import com.gsc.stockoverview.ui.components.StockTable
import com.gsc.stockoverview.ui.components.StockTopAppBar
import com.gsc.stockoverview.ui.components.formatDate
import com.gsc.stockoverview.ui.components.formatDouble
import com.gsc.stockoverview.ui.components.formatLong
import com.gsc.stockoverview.ui.components.formatStockName
import com.gsc.stockoverview.ui.viewmodel.TransactionViewModel
import com.gsc.stockoverview.ui.viewmodel.TransactionViewModelFactory

@Composable
fun TransactionScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }

    val viewModel: TransactionViewModel = viewModel(
        factory = TransactionViewModelFactory(
            repository = TransactionRepository(database.transactionDao()),
            transactionRawRepository = TransactionRawRepository(database.transactionRawDao()),
            tradingLogRawRepository = TradingLogRawRepository(database.tradingLogRawDao()),
            overseasTradingLogRawRepository = OverseasTradingLogRawRepository(database.overseasTradingLogRawDao()),
            stockRepository = StockRepository(database.stockDao())
        )
    )

    val transactions by viewModel.transactionList.collectAsState(initial = emptyList())
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tabs = listOf("전체", "일반", "ISA", "연금", "IRP", "퇴직IRP", "금통장", "CMA")

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "거래내역",
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
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

            StockTable(
                headers = listOf(
                    "계좌", "매매일자", "구분", "거래명", "거래금액", "단가", "거래량",
                    "수수료", "세금", "손익금액", "수익률", "거래종류상세"
                ),
                items = transactions,
                cellContent = { item ->
                    listOf(
                        item.account,
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
                }
            )
        }
    }
}
