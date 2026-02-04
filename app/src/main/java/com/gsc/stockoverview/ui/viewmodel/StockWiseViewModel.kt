package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.entity.TransactionEntity
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
    val currentPrice: Double = 0.0,
    val holdVolume: Double = 0.0,
    val buyAmount: Double = 0.0,
    val buyAverage: Double = 0.0,
    val evaluationAmount: Double = 0.0,
    val evaluationProfit: Double = 0.0,
    val yield: Double = 0.0,
    val currency: String = "KRW"
)

class StockWiseViewModel(
    private val transactionRepository: TransactionRepository,
    private val naverApi: NaverStockApiService,
    private val yahooApi: YahooStockApiService
) : ViewModel() {

    private val _selectedTab = MutableStateFlow("전체")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _stockPrices = MutableStateFlow<Map<String, Double>>(emptyMap())
    private val _exchangeRate = MutableStateFlow(1450.0) // 기본 환율
    
    val stockWiseList = combine(
        transactionRepository.allTransactions,
        _selectedTab,
        _stockPrices,
        _exchangeRate
    ) { transactions, tab, prices, rate ->
        calculateStockWise(transactions, tab, prices, rate)
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun refreshPrices() {
        viewModelScope.launch {
            // 모든 거래 데이터에서 종목 리스트 추출
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
        transactions: List<TransactionEntity>,
        tab: String,
        prices: Map<String, Double>,
        exchangeRate: Double
    ): List<StockWiseItem> {
        val filtered = if (tab == "전체") transactions else transactions.filter { it.account == tab }
        
        return filtered.groupBy { it.stockCode }
            .filter { it.key.isNotBlank() }
            .mapNotNull { (code, trans) ->
                val firstTrans = trans.first()
                val name = firstTrans.transactionName
                val currency = firstTrans.currencyCode
                val rate = if (currency == "USD") exchangeRate else 1.0
                
                var volume = 0.0
                var totalBuyAmountKrw = 0.0
                
                trans.sortedBy { it.tradeDate }.forEach { t ->
                    val tRate = if (t.currencyCode == "USD") exchangeRate else 1.0
                    when (t.type) {
                        "매수", "입금" -> {
                            volume += t.volume
                            totalBuyAmountKrw += (t.amount * tRate)
                        }
                        "매도", "출금" -> {
                            if (volume > 0) {
                                val avgKrw = totalBuyAmountKrw / volume
                                volume -= t.volume
                                totalBuyAmountKrw -= t.volume * avgKrw
                            }
                        }
                    }
                }

                if (volume <= 0.001) return@mapNotNull null // 아주 작은 잔고는 제외

                val currentPrice = prices[code] ?: 0.0
                // currentPrice가 USD인 경우 KRW로 환산
                val currentPriceKrw = currentPrice * rate
                val evaluationAmountKrw = volume * currentPriceKrw
                val evaluationProfitKrw = evaluationAmountKrw - totalBuyAmountKrw
                val yield = if (totalBuyAmountKrw != 0.0) (evaluationProfitKrw / totalBuyAmountKrw) * 100 else 0.0

                StockWiseItem(
                    stockName = name,
                    stockCode = code,
                    currentPrice = currentPriceKrw, // 화면에는 원화로 표시
                    holdVolume = volume,
                    buyAmount = totalBuyAmountKrw,
                    buyAverage = totalBuyAmountKrw / volume,
                    evaluationAmount = evaluationAmountKrw,
                    evaluationProfit = evaluationProfitKrw,
                    yield = yield,
                    currency = currency
                )
            }
            .sortedByDescending { it.evaluationAmount }
    }
}

class StockWiseViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val naverApi: NaverStockApiService,
    private val yahooApi: YahooStockApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return StockWiseViewModel(transactionRepository, naverApi, yahooApi) as T
    }
}
