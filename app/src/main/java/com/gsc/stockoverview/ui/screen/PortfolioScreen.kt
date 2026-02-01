package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.repository.PortfolioRepository
import com.gsc.stockoverview.ui.components.StockTopAppBar
import com.gsc.stockoverview.ui.viewmodel.PortfolioItemUi
import com.gsc.stockoverview.ui.viewmodel.PortfolioViewModel
import com.gsc.stockoverview.ui.viewmodel.PortfolioViewModelFactory
import java.text.DecimalFormat

@Composable
fun PortfolioScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { 
        PortfolioRepository(
            database.portfolioDao(),
            database.stockDao(),
            database.transactionDao(),
            NaverStockApiService(),
            YahooStockApiService()
        )
    }
    val viewModel: PortfolioViewModel = viewModel(factory = PortfolioViewModelFactory(repository))
    val uiState by viewModel.uiState.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showEditWeightDialog by remember { mutableStateOf<PortfolioItemUi?>(null) }
    var itemToDelete by remember { mutableStateOf<PortfolioItemUi?>(null) }

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "포트폴리오",
                onOpenDrawer = onOpenDrawer,
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "종목 추가")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding)) {
                PortfolioHeader()
                
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        TotalRow(
                            totalTargetWeight = uiState.totalTargetWeight,
                            totalBaseAmount = uiState.totalBaseAmount,
                            totalEvalAmount = uiState.totalEvaluationAmount,
                            totalInvestedAmount = uiState.totalInvestedAmount
                        )
                    }

                    items(uiState.items) { item ->
                        PortfolioItemRow(
                            item = item,
                            onEditWeight = { showEditWeightDialog = item },
                            onDelete = { itemToDelete = item }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }
        }

        if (showAddDialog) {
            StockSelectionDialog(
                stocks = uiState.allStocks,
                onDismiss = { showAddDialog = false },
                onSelect = { stock ->
                    viewModel.addStock(stock.stockCode)
                    showAddDialog = false
                }
            )
        }

        showEditWeightDialog?.let { item ->
            EditWeightDialog(
                item = item,
                onDismiss = { showEditWeightDialog = null },
                onConfirm = { weight ->
                    viewModel.updateWeight(item.stockCode, weight)
                    showEditWeightDialog = null
                }
            )
        }

        itemToDelete?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDelete = null },
                title = { Text("종목 삭제") },
                text = { Text("'${item.stockName}' 종목을 포트폴리오에서 삭제하시겠습니까?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteStock(item.stockCode)
                            itemToDelete = null
                        }
                    ) {
                        Text("삭제", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDelete = null }) {
                        Text("취소")
                    }
                }
            )
        }
    }
}

@Composable
fun PortfolioHeader() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderText("종목", Modifier.weight(1.5f))
                HeaderText("목표비중", Modifier.weight(1f))
                HeaderText("비중금액", Modifier.weight(1.2f))
                HeaderText("조정률", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                HeaderText("투자금액", Modifier.weight(1.5f))
                HeaderText("평가비중", Modifier.weight(1f))
                HeaderText("평가금액", Modifier.weight(1.2f))
                HeaderText("조정금액", Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun TotalRow(
    totalTargetWeight: Double,
    totalBaseAmount: Double,
    totalEvalAmount: Double,
    totalInvestedAmount: Double
) {
    val totalAdjAmt = totalBaseAmount - totalEvalAmount
    val totalAdjRate = if (totalEvalAmount != 0.0) (totalAdjAmt / totalEvalAmount) * 100.0 else 0.0

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                DataText("합계", Modifier.weight(1.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                DataText(formatPercent(totalTargetWeight), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                DataText(formatAmount(totalBaseAmount), Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                DataText(formatPercent(totalAdjRate), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                DataText(formatAmount(totalInvestedAmount), Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                DataText("100.0%", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                DataText(formatAmount(totalEvalAmount), Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
                DataText(formatAmount(totalAdjAmt), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PortfolioItemRow(
    item: PortfolioItemUi,
    onEditWeight: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1.5f)) {
                Text(item.stockName, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(item.stockCode, fontSize = 10.sp, color = Color.Gray)
            }
            
            Row(modifier = Modifier.weight(1f).clickable { onEditWeight() }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                DataText(formatPercent(item.targetWeight), Modifier)
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color.Gray)
            }
            
            DataText(formatAmount(item.targetAmount), Modifier.weight(1.2f))
            DataText(formatPercent(item.adjustmentRate), Modifier.weight(1f))
        }
        
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DataText(formatAmount(item.investedAmount), Modifier.weight(1.5f))
            DataText(formatPercent(item.currentWeight), Modifier.weight(1f))
            DataText(formatAmount(item.evaluationAmount), Modifier.weight(1.2f))
            
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                DataText(formatAmount(item.adjustmentAmount), Modifier.weight(1f))
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "삭제", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun DataText(
    text: String,
    modifier: Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.End
) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 2.dp),
        fontSize = 13.sp,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        maxLines = 1
    )
}

@Composable
fun StockSelectionDialog(
    stocks: List<StockEntity>,
    onDismiss: () -> Unit,
    onSelect: (StockEntity) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종목 추가") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(stocks) { stock ->
                    ListItem(
                        headlineContent = { Text(stock.stockName) },
                        supportingContent = { Text(stock.stockCode) },
                        modifier = Modifier.clickable { onSelect(stock) }
                    )
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    )
}

@Composable
fun EditWeightDialog(
    item: PortfolioItemUi,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var weightText by remember { mutableStateOf(item.targetWeight.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.stockName} 비중 수정") },
        text = {
            TextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("목표 비중 (%)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { 
                weightText.toDoubleOrNull()?.let { onConfirm(it) }
            }) {
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

private fun formatAmount(amount: Double): String {
    return DecimalFormat("#,###").format(amount)
}

private fun formatPercent(percent: Double): String {
    return String.format("%.1f%%", percent)
}
