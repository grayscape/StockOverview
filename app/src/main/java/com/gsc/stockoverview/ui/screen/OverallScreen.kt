package com.gsc.stockoverview.ui.screen

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.repository.OverallRepository
import com.gsc.stockoverview.data.repository.OverallStats
import com.gsc.stockoverview.ui.components.StockTopAppBar
import com.gsc.stockoverview.ui.viewmodel.OverallViewModel
import com.gsc.stockoverview.ui.viewmodel.OverallViewModelFactory
import java.text.DecimalFormat

@Composable
fun OverallScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember {
        OverallRepository(
            database.transactionDao(),
            database.portfolioDao(),
            database.stockDao(),
            NaverStockApiService(),
            YahooStockApiService()
        )
    }
    val viewModel: OverallViewModel = viewModel(factory = OverallViewModelFactory(repository))
    
    val overallData by viewModel.overallData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            StockTopAppBar(title = "전체현황", onOpenDrawer = onOpenDrawer)
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (overallData.isEmpty()) {
                Text("데이터가 없습니다.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(overallData) { stats ->
                        OverallStatsCard(stats)
                    }
                }
            }
        }
    }
}

@Composable
fun OverallStatsCard(stats: OverallStats) {
    val numberFormat = DecimalFormat("#,###")
    val usdFormat = DecimalFormat("#,###.##")
    val profitFormat = DecimalFormat("+#,###;-#,###")

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = stats.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            if (stats.title == "기타내역") {
                StatRow("원화예수금", numberFormat.format(stats.krwDeposit))
                StatRow("달러예수금", "$ " + usdFormat.format(stats.usdDeposit))
                StatRow("원화배당금", numberFormat.format(stats.krwDividend))
                StatRow("달러배당금", "$ " + usdFormat.format(stats.usdDividend))
                StatRow("원화이자", numberFormat.format(stats.krwInterest))
                StatRow("달러이자", "$ " + usdFormat.format(stats.usdInterest))
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                StatRow("총 예수금(원화환산)", numberFormat.format(stats.evaluatedAssets))
            } else {
                if (stats.title == "총투자내역") {
                    StatRow("원금", numberFormat.format(stats.principal))
                }
                
                StatRow("평가자산", numberFormat.format(stats.evaluatedAssets))
                
                StatRow("운용금액", numberFormat.format(stats.operatingAmount))
                StatRow("평가금액", numberFormat.format(stats.evaluatedAmount))
                
                val profitColor = when {
                    stats.evaluatedProfit > 0 -> Color.Red
                    stats.evaluatedProfit < 0 -> Color.Blue
                    else -> Color.Unspecified
                }
                StatRow("평가수익", profitFormat.format(stats.evaluatedProfit), valueColor = profitColor)
                
                val realizedColor = when {
                    stats.realizedProfit > 0 -> Color.Red
                    stats.realizedProfit < 0 -> Color.Blue
                    else -> Color.Unspecified
                }
                StatRow("실현손익", profitFormat.format(stats.realizedProfit), valueColor = realizedColor)
                
                if (stats.title == "총투자내역") {
                    StatRow("예수금", numberFormat.format(stats.deposit))
                }
            }
        }
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
    }
}
