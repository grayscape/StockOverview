package com.gsc.stockoverview.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.repository.StockRepository
import com.gsc.stockoverview.ui.viewmodel.StockViewModel
import com.gsc.stockoverview.ui.viewmodel.StockViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(
    stockCode: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { StockRepository(database.stockDao()) }
    val viewModel: StockViewModel = viewModel(factory = StockViewModelFactory(repository))
    
    var stock by remember { mutableStateOf<StockEntity?>(null) }
    var shortName by remember { mutableStateOf("") }

    LaunchedEffect(stockCode) {
        stock = repository.getStockByCode(stockCode)
        stock?.let {
            shortName = it.stockShortName
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stock?.stockName ?: "종목 상세") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        stock?.let { currentStock ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = currentStock.stockCode,
                    onValueChange = {},
                    label = { Text("종목코드") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )

                OutlinedTextField(
                    value = currentStock.stockName,
                    onValueChange = {},
                    label = { Text("종목명") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )

                OutlinedTextField(
                    value = shortName,
                    onValueChange = { shortName = it },
                    label = { Text("종목약어명") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = "${currentStock.currentPrice} ${currentStock.currency}",
                    onValueChange = {},
                    label = { Text("현재가") },
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true
                )

                Button(
                    onClick = {
                        scope.launch {
                            repository.insertStock(currentStock.copy(stockShortName = shortName))
                            onBack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("저장")
                }
            }
        } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
