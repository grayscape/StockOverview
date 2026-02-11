package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.entity.AccountStockStatusEntity
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.repository.AccountStockStatusRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StockWiseItem(
    val stockName: String,
    val stockCode: String,
    val averagePrice: Double = 0.0,         // 평균단가
    val evaluationAmount: Double = 0.0,     // 평가금액
    val evaluationProfit: Double = 0.0,     // 평가손익
    val profitLossAmount: Double = 0.0,     // 손익금액
    val holdVolume: Double = 0.0,           // 보유량
    val currentPrice: Double = 0.0,         // 현재시세
    val purchaseAmount: Double = 0.0,       // 매입금액
    val evaluationProfitRate: Double = 0.0, // 평가손익률
    val profitLossRate: Double = 0.0,       // 손익률
    val currency: String = "KRW",
    val exchangeRate: Double = 1.0          // 환율 추가
)

class StockWiseViewModel(
    private val transactionRepository: TransactionRepository,
    private val accountStockStatusRepository: AccountStockStatusRepository,
    private val stockDao: StockDao,
    private val naverApi: NaverStockApiService,
    private val yahooApi: YahooStockApiService
) : ViewModel() {

    private val _selectedTab = MutableStateFlow("전체")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _stockPrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _exchangeRate = MutableStateFlow(1450.0) // 기본 환율
    
    val stockWiseList = combine(
        accountStockStatusRepository.allAccountStockStatusEntity,
        stockDao.getAllStocks(),
        _selectedTab,
        _stockPrices,
        _exchangeRate
    ) { statusList, stocks, tab, prices, rate ->
        calculateStockWise(statusList, stocks, tab, prices, rate)
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun refreshPrices() {
        viewModelScope.launch {
            val transactions = transactionRepository.allTransactions.first()
            if (transactions.isEmpty()) return@launch

            val stocksToFetch = transactions
                .filter { it.stockCode.isNotBlank() }
                .map { Triple(it.stockCode, it.transactionName, it.currencyCode) }
                .distinct()

            val (newPrices, newRate) = withContext(Dispatchers.IO) {
                val prices = stocksToFetch.associate { (code, name, currency) ->
                    val price = when {
                        code == "M04020000" -> naverApi.fetchGoldPrice()
                        currency == "KRW" -> naverApi.fetchDomesticStockDetails(code)?.currentPrice ?: 0.0
                        else -> yahooApi.fetchOverseasStockDetails(code, name)?.currentPrice ?: 0.0
                    }
                    code to price
                }
                val rate = naverApi.fetchExchangeRate()
                prices to (if (rate > 0) rate else 1450.0)
            }
            _stockPrices.value = newPrices
            _exchangeRate.value = newRate
        }
    }

    private fun calculateStockWise(
        statusList: List<AccountStockStatusEntity>,
        stocks: List<StockEntity>,
        tab: String,
        prices: Map<String, Double>,
        exchangeRate: Double
    ): List<StockWiseItem> {
        val stockMap = stocks.associateBy { it.stockCode }
        val filtered = if (tab == "전체") statusList else statusList.filter { it.account == tab }
        
        return filtered.groupBy { it.stockCode }
            .map { (code, statuses) ->
                val stock = stockMap[code]
                val name = stock?.stockShortName ?: (statuses.firstOrNull()?.stockCode ?: "알수없음")
                val currency = statuses.firstOrNull()?.currencyCode ?: "KRW"
                val isUsd = currency == "USD"
                
                // 1) AccountStockStatusEntity 값 참조
                val totalQuantity = statuses.sumOf { it.quantity }
                val totalPurchaseAmount = statuses.sumOf { it.purchaseAmount }
                val totalProfitLossAmount = statuses.sumOf { it.profitLossAmount }
                
                // 손익률 (매입금액 기준 가중평균)
                val totalProfitLossRate = if (totalPurchaseAmount != 0.0) {
                    statuses.sumOf { it.profitLossRate.toDouble() * it.purchaseAmount } / totalPurchaseAmount
                } else 0.0

                // 2) 현재시세 참조
                val currentPrice = prices[code] ?: 0.0
                
                // 3) 평가금액, 평가손익, 평가손익률 계산
                val evalAmount = totalQuantity * currentPrice
                val evalProfit = evalAmount - totalPurchaseAmount
                val evalProfitRate = if (totalPurchaseAmount != 0.0) (evalProfit / totalPurchaseAmount) * 100 else 0.0
                
                StockWiseItem(
                    stockName = name,
                    stockCode = code,
                    averagePrice = (if (totalQuantity > 0) totalPurchaseAmount / totalQuantity else 0.0),
                    evaluationAmount = evalAmount,
                    evaluationProfit = evalProfit,
                    profitLossAmount = totalProfitLossAmount,
                    holdVolume = totalQuantity,
                    currentPrice = currentPrice,
                    purchaseAmount = totalPurchaseAmount,
                    evaluationProfitRate = evalProfitRate,
                    profitLossRate = totalProfitLossRate,
                    currency = currency,
                    exchangeRate = if (isUsd) exchangeRate else 1.0
                )
            }
            .filter { it.holdVolume > 0 || it.profitLossAmount != 0.0 }
            .sortedByDescending { it.evaluationAmount * it.exchangeRate }
    }
}

class StockWiseViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val accountStockStatusRepository: AccountStockStatusRepository,
    private val stockDao: StockDao,
    private val naverApi: NaverStockApiService,
    private val yahooApi: YahooStockApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StockWiseViewModel(transactionRepository, accountStockStatusRepository, stockDao, naverApi, yahooApi) as T
    }
}
