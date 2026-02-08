package com.gsc.stockoverview.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsc.stockoverview.data.AppDatabase
import com.gsc.stockoverview.data.repository.*
import com.gsc.stockoverview.ui.components.StockTable
import com.gsc.stockoverview.ui.components.StockTopAppBar
import com.gsc.stockoverview.ui.components.formatDate
import com.gsc.stockoverview.ui.components.formatDouble
import com.gsc.stockoverview.ui.components.formatLong
import com.gsc.stockoverview.ui.components.formatStockName
import com.gsc.stockoverview.ui.viewmodel.*
import com.gsc.stockoverview.utils.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TransactionDetailScreen(onOpenDrawer: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val excelReader = remember { ExcelReader(context) }

    // Repository 및 ViewModel 설정
    val transactionRepo = remember { 
        TransactionRepository(
            database.transactionDao(),
            database.transactionRawDao(),
            database.tradingLogRawDao(),
            database.overseasTradingLogRawDao(),
            database.stockDao()
        ) 
    }
    val transactionRawRepo = remember { TransactionRawRepository(database.transactionRawDao()) }
    val tradingLogRawRepo = remember { TradingLogRawRepository(database.tradingLogRawDao()) }
    val overseasTradingLogRawRepo = remember { OverseasTradingLogRawRepository(database.overseasTradingLogRawDao()) }
    val stockRepo = remember { StockRepository(database.stockDao()) }
    val accountStockStatusRepo = remember { AccountStockStatusRepository(database.accountStockStatusDao()) }
    val accountRepo = remember { AccountStatusRepository(database.accountStatusDao()) }

    val transactionRawViewModel: TransactionRawViewModel = viewModel(factory = TransactionRawViewModelFactory(transactionRawRepo, excelReader))
    val tradingLogRawViewModel: TradingLogRawViewModel = viewModel(factory = TradingLogRawViewModelFactory(tradingLogRawRepo, excelReader))
    val overseasTradingLogRawViewModel: OverseasTradingLogRawViewModel = viewModel(factory = OverseasTradingLogRawViewModelFactory(overseasTradingLogRawRepo, excelReader))
    val stockViewModel: StockViewModel = viewModel(factory = StockViewModelFactory(stockRepo))
    val transactionViewModel: TransactionViewModel = viewModel(
        factory = TransactionViewModelFactory(transactionRepo, accountStockStatusRepo, accountRepo)
    )

    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val sheetData = withContext(Dispatchers.IO) {
                        excelReader.readAllSheets(it, listOf("매매일지", "해외매매일지", "전체거래내역"))
                    }

                    if (sheetData.isEmpty()) {
                        Toast.makeText(context, "엑셀 데이터를 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    withContext(Dispatchers.IO) {
                        // 국내 매매일지 처리
                        sheetData["매매일지"]?.let { rows ->
                            val entities = rows.map { tradingLogRawViewModel.mapRowToEntity(it) }
                            tradingLogRawRepo.deleteAll()
                            tradingLogRawRepo.insertAll(entities)
                            stockViewModel.ensureStocksExist(entities.map { it.stockName }, onComplete = {
                                // 해외 매매일지 데이터 준비
                                val overseasRows = sheetData["해외매매일지"] ?: emptyList()
                                val overseasEntities = overseasRows.map { overseasTradingLogRawViewModel.mapRowToEntity(it) }

                                // 해외 종목 정보 확인 후 나머지 작업(해외 매매일지 처리, 전체거래내역 처리, 동기화) 수행
                                stockViewModel.ensureOverseasStocksExist(
                                    overseasInfos = overseasEntities.map { Triple(it.stockNumber, it.stockName, it.currency) },
                                    onComplete = {
                                        scope.launch(Dispatchers.IO) {
                                            // 해외 매매일지 처리 (DB 반영)
                                            if (overseasEntities.isNotEmpty()) {
                                                overseasTradingLogRawRepo.deleteAll()
                                                overseasTradingLogRawRepo.insertAll(overseasEntities)
                                            }

                                            // 전체거래내역 처리
                                            sheetData["전체거래내역"]?.let { rows ->
                                                val entities = rows.map { transactionRawViewModel.mapRowToEntity(it) }
                                                transactionRawRepo.deleteAll()
                                                transactionRawRepo.insertAll(entities)
                                            }

                                            // 모든 데이터 로드 완료 후 동기화 수행
                                            transactionViewModel.syncFromRawData {
                                                Toast.makeText(context, "모든 데이터 로드 및 동기화 완료", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )
                            })
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "가져오기 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var rawSubTabSelected by remember { mutableIntStateOf(0) }
    val rawTabs = listOf("전체거래내역", "매매일지", "해외매매일지")

    Scaffold(
        topBar = {
            StockTopAppBar(
                title = "거래내역상세",
                onOpenDrawer = onOpenDrawer,
                actions = {
                    TextButton(onClick = { excelPickerLauncher.launch("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") }) {
                        Text("엑셀 가져오기", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = rawSubTabSelected) {
                rawTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = rawSubTabSelected == index,
                        onClick = { rawSubTabSelected = index },
                        text = { Text(title) }
                    )
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (rawSubTabSelected) {
                    0 -> TransactionRawTab(transactionRawViewModel)
                    1 -> TradingLogRawTab(tradingLogRawViewModel)
                    2 -> OverseasTradingLogRawTab(overseasTradingLogRawViewModel)
                }
            }
        }
    }
}

@Composable
fun TransactionRawTab(viewModel: TransactionRawViewModel) {
    val data by viewModel.transactionRawList.collectAsState(initial = emptyList())
    StockTable(
        headers = listOf(
            "계좌", "거래일자", "번호", "거래종류", "거래명", "수량", "단가", "거래금액",
            "입출금액", "예수금", "유가잔고", "수수료", "제세금합", "외화거래금액", "외화입출금액",
            "외화예수금", "외화유가증권", "미수발생금액", "미수변제금액", "통화코드",
            "상대기관", "상대고객명", "상대계좌번호", "받는분표시", "내계좌표시"
        ),
        items = data,
        cellContent = { item ->
            listOf(
                item.account, formatDate(item.transactionDate), item.transactionNo.toString(), item.type, formatStockName(item.transactionName),
                item.quantity.toString(), formatDouble(item.price), formatDouble(item.amount),
                formatLong(item.depositWithdrawalAmount), formatLong(item.balance), formatLong(item.stockBalance),
                formatDouble(item.fee), formatDouble(item.tax), formatDouble(item.foreignAmount),
                formatDouble(item.foreignDWAmount), formatDouble(item.foreignBalance), formatDouble(item.foreignStock),
                formatLong(item.uncollectedAmount), formatLong(item.repaidAmount), item.currencyCode,
                item.relativeAgency, item.relativeClientName, item.relativeAccountNumber,
                item.recipientDisplay, item.myAccountDisplay
            )
        }
    )
}

@Composable
fun TradingLogRawTab(viewModel: TradingLogRawViewModel) {
    val data by viewModel.tradingLogRawList.collectAsState(initial = emptyList())
    StockTable(
        headers = listOf("계좌", "매매일자", "종목명", "매수량", "매수평균단가", "매수금액", "매도량", "매도평균단가", "매도금액", "매매비용", "손익금액", "수익률"),
        items = data,
        cellContent = { item ->
            listOf(
                item.account, formatDate(item.tradeDate), formatStockName(item.stockName),
                item.buyQuantity.toString(), formatDouble(item.buyPrice), formatLong(item.buyAmount),
                item.sellQuantity.toString(), formatDouble(item.sellPrice), formatLong(item.sellAmount),
                formatLong(item.tradeFee), formatLong(item.profitLoss), "${item.yield}%"
            )
        }
    )
}

@Composable
fun OverseasTradingLogRawTab(viewModel: OverseasTradingLogRawViewModel) {
    val data by viewModel.overseasTradingLogRawList.collectAsState(initial = emptyList())
    StockTable(
        headers = listOf(
            "계좌", "매매일자", "통화", "종목번호", "종목명", "잔고량", "매입평균환율", "매매환율",
            "매수량", "매수단가", "매수금액", "원화매수금액", "매도량", "매도단가", "매도금액",
            "원화매도금액", "수수료", "세금", "원화총비용", "원매수평균단가", "매매손익",
            "원화매매손익", "환차손익", "총평가손익", "손익률", "환산손익률"
        ),
        items = data,
        cellContent = { item ->
            listOf(
                item.account, formatDate(item.tradeDate), item.currency, item.stockNumber, formatStockName(item.stockName),
                item.balanceQuantity.toString(), formatDouble(item.buyAverageExchangeRate), formatDouble(item.tradingExchangeRate),
                item.buyQuantity.toString(), formatDouble(item.buyPrice), formatDouble(item.buyAmount), formatLong(item.wonBuyAmount),
                item.sellQuantity.toString(), formatDouble(item.sellPrice), formatDouble(item.sellAmount), formatLong(item.wonSellAmount),
                formatDouble(item.fee), formatDouble(item.tax), formatLong(item.wonTotalCost),
                formatDouble(item.originalBuyAveragePrice), formatDouble(item.tradingProfit), formatLong(item.wonTradingProfit),
                formatDouble(item.exchangeProfit), formatDouble(item.totalEvaluationProfit), "${item.yield}%", "${item.convertedYield}%"
            )
        }
    )
}
