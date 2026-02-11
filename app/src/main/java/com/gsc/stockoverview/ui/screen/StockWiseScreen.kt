package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.repository.AccountStockStatusRepository
import com.gsc.stockoverview.data.repository.CommonCodeRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import com.gsc.stockoverview.ui.components.StockTopAppBar
import com.gsc.stockoverview.ui.components.formatCurrency
import com.gsc.stockoverview.ui.components.formatDouble
import com.gsc.stockoverview.ui.viewmodel.CommonCodeViewModel
import com.gsc.stockoverview.ui.viewmodel.CommonCodeViewModelFactory
import com.gsc.stockoverview.ui.viewmodel.StockWiseItem
import com.gsc.stockoverview.ui.viewmodel.StockWiseViewModel
import com.gsc.stockoverview.ui.viewmodel.StockWiseViewModelFactory

@Composable
fun StockWiseScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    
    val commonCodeViewModel: CommonCodeViewModel = viewModel(
        factory = CommonCodeViewModelFactory(CommonCodeRepository(database.commonCodeDao()))
    )
    val accountCodes by commonCodeViewModel.getCodesByParent("ACC_ROOT").collectAsState(initial = emptyList())
    
    val viewModel: StockWiseViewModel = viewModel(
        factory = StockWiseViewModelFactory(
            transactionRepository = TransactionRepository(
                database.transactionDao(),
                database.transactionRawDao(),
                database.tradingLogRawDao(),
                database.overseasTradingLogRawDao(),
                database.stockDao()
            ),
            accountStockStatusRepository = AccountStockStatusRepository(database.accountStockStatusDao()),
            stockDao = database.stockDao(),
            naverApi = NaverStockApiService(),
            yahooApi = YahooStockApiService()
        )
    )

    val stockWiseList by viewModel.stockWiseList.collectAsState(initial = emptyList())
    val selectedTab by viewModel.selectedTab.collectAsState()
    
    val tabs = remember(accountCodes) {
        listOf("전체") + accountCodes.map { it.name }
    }

    // 화면 진입 시 초기 시세 갱신
    LaunchedEffect(Unit) {
        viewModel.refreshPrices()
    }

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "종목별현황",
                onOpenDrawer = onOpenDrawer,
                actions = {
                    TextButton(onClick = { viewModel.refreshPrices() }) {
                        Text("시세갱신", color = MaterialTheme.colorScheme.primary)
                    }
                }
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
                            text = { Text(text = tab, style = MaterialTheme.typography.titleSmall) }
                        )
                    }
                }
            }

            StockWiseHeader()
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(stockWiseList) { item ->
                    StockWiseRow(item)
                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun StockWiseHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderText("종목명", Modifier.weight(0.8f), textAlign = TextAlign.Start)
            HeaderText("평균단가", Modifier.weight(0.8f))
            HeaderText("평가금액", Modifier.weight(1.2f))
            HeaderText("평가손익", Modifier.weight(1.2f))
            HeaderText("손익금액", Modifier.weight(1.2f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderText("보유량", Modifier.weight(0.8f), textAlign = TextAlign.Start)
            HeaderText("현재시세", Modifier.weight(0.8f))
            HeaderText("매입금액", Modifier.weight(1.2f))
            HeaderText("평가손익률", Modifier.weight(1.2f))
            HeaderText("손익률", Modifier.weight(1.2f))
        }
    }
}

@Composable
fun HeaderText(text: String, modifier: Modifier, textAlign: TextAlign = TextAlign.End) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun StockWiseRow(item: StockWiseItem) {
    val evalProfitColor = when {
        item.evaluationProfit > 0 -> Color.Red
        item.evaluationProfit < 0 -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val profitLossColor = when {
        item.profitLossAmount > 0 -> Color.Red
        item.profitLossAmount < 0 -> Color.Blue
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        // 첫 번째 줄: 종목명, 평균단가, 평가금액, 평가손익, 손익금액
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = item.stockName,
                modifier = Modifier.weight(0.8f).padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Start
            )
            StockValueCell(
                value = item.averagePrice,
                currency = item.currency,
                exchangeRate = item.exchangeRate,
                modifier = Modifier.weight(0.8f)
            )
            StockValueCell(
                value = item.evaluationAmount,
                currency = item.currency,
                exchangeRate = item.exchangeRate,
                modifier = Modifier.weight(1.2f)
            )
            StockValueCell(
                value = item.evaluationProfit,
                currency = item.currency,
                exchangeRate = item.exchangeRate,
                modifier = Modifier.weight(1.2f),
                color = evalProfitColor
            )
            StockValueCell(
                value = item.profitLossAmount,
                currency = item.currency,
                exchangeRate = item.exchangeRate,
                modifier = Modifier.weight(1.2f),
                color = profitLossColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        // 두 번째 줄: 보유량, 현재시세, 매입금액, 평가손익률, 손익률
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = formatDouble(item.holdVolume),
                modifier = Modifier.weight(0.8f).padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                textAlign = TextAlign.Start
            )
            StockValueCell(
                value = item.currentPrice,
                currency = item.currency,
                exchangeRate = item.exchangeRate,
                modifier = Modifier.weight(0.8f)
            )
            StockValueCell(
                value = item.purchaseAmount,
                currency = item.currency,
                exchangeRate = item.exchangeRate,
                modifier = Modifier.weight(1.2f)
            )
            ValueText(
                text = "${formatCurrency(item.evaluationProfitRate, item.currency, true)}%",
                modifier = Modifier.weight(1.2f).padding(top = 2.dp),
                color = evalProfitColor,
                style = MaterialTheme.typography.bodySmall
            )
            ValueText(
                text = "${formatCurrency(item.profitLossRate, item.currency, true)}%",
                modifier = Modifier.weight(1.2f).padding(top = 2.dp),
                color = profitLossColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StockValueCell(
    value: Double,
    currency: String,
    exchangeRate: Double,
    modifier: Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = formatCurrency(value, currency),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            textAlign = TextAlign.End,
            maxLines = 1
        )
        if (currency == "USD") {
            Text(
                text = formatCurrency(value * exchangeRate, "KRW"),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = color.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ValueText(
    text: String,
    modifier: Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    textAlign: TextAlign = TextAlign.End
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1
    )
}
