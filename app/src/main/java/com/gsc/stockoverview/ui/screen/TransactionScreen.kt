package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.repository.TransactionRepository
import com.gsc.stockoverview.ui.components.StockTable
import com.gsc.stockoverview.ui.components.StockTopAppBar
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
            TransactionRepository(database.transactionDao())
        )
    )

    val transactions by viewModel.transactionList.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "거래내역",
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StockTable(
                headers = listOf(
                    "계좌", "매매일자", "구분", "종목명", "단가", "수량",
                    "수수료", "세금", "거래금액", "손익금액", "수익률", "환율", "환차손익", "거래종류상세"
                ),
                items = transactions,
                cellContent = { item ->
                    listOf(
                        item.account,
                        item.tradeDate,
                        item.type,
                        formatStockName(item.stockName),
                        formatDouble(item.price),
                        item.quantity.toString(),
                        formatLong(item.fee),
                        formatLong(item.tax),
                        formatDouble(item.amount),
                        formatLong(item.profitLoss),
                        "${item.yield}%",
                        formatDouble(item.exchangeRate),
                        formatLong(item.exchangeProfitLoss),
                        item.typeDetail
                    )
                }
            )
        }
    }
}
