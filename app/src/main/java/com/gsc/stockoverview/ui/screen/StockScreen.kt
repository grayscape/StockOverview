package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.ui.components.StockTopAppBar

@Composable
fun StockScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val stockDao = remember { AppDatabase.getDatabase(context).stockDao() }
    val stockList by stockDao.getAllStocks().collectAsState(initial = emptyList())

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("국내", "해외")

    // 데이터 필터링 및 정렬 (종목명 ASC)
    val filteredStocks = remember(stockList, selectedTabIndex) {
        val type = if (selectedTabIndex == 0) "KOREA" else "OVERSEAS"
        stockList.filter { it.stockType == type }
            .sortedBy { it.stockName }
    }

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "종목 정보",
                onOpenDrawer = onOpenDrawer
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredStocks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("${tabs[selectedTabIndex]} 종목 정보가 없습니다.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredStocks, key = { it.stockCode }) { stock ->
                        StockItem(stock)
                    }
                }
            }
        }
    }
}

@Composable
fun StockItem(stock: StockEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stock.stockName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stock.stockCode,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val priceText = if (stock.stockType == "KOREA") {
                    String.format("%,.0f", stock.currentPrice)
                } else {
                    String.format("%,.2f", stock.currentPrice)
                }
                Text(text = "현재가: $priceText ${stock.currency}")
                Text(text = stock.marketType)
            }
        }
    }
}
