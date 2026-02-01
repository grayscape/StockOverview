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
    
    val stockWiseList = combine(
        transactionRepository.allTransactions,
        _selectedTab,
        _stockPrices
    ) { transactions, tab, prices ->
        calculateStockWise(transactions, tab, prices)
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

            val newPrices = withContext(Dispatchers.IO) {
                stocksToFetch.associate { (code, name, currency) ->
                    val price = if (currency == "KRW") {
                        naverApi.fetchDomesticStockDetails(code)?.currentPrice ?: 0.0
                    } else {
                        yahooApi.fetchOverseasStockDetails(code, name)?.currentPrice ?: 0.0
                    }
                    code to price
                }
            }
            _stockPrices.value = newPrices
        }
    }

    private fun calculateStockWise(
        transactions: List<TransactionEntity>,
        tab: String,
        prices: Map<String, Double>
    ): List<StockWiseItem> {
        val filtered = if (tab == "전체") transactions else transactions.filter { it.account == tab }
        
        return filtered.groupBy { it.stockCode }
            .filter { it.key.isNotBlank() }
            .mapNotNull { (code, trans) ->
                val name = trans.first().transactionName
                var volume = 0.0
                var totalBuyAmount = 0.0
                
                trans.sortedBy { it.tradeDate }.forEach { t ->
                    when (t.type) {
                        "매수", "입금" -> {
                            volume += t.volume
                            totalBuyAmount += t.amount
                        }
                        "매도", "출금" -> {
                            if (volume > 0) {
                                val avg = totalBuyAmount / volume
                                volume -= t.volume
                                totalBuyAmount -= t.volume * avg
                            }
                        }
                    }
                }

                if (volume <= 0.001) return@mapNotNull null // 아주 작은 잔고는 제외

                val currentPrice = prices[code] ?: 0.0
                val evaluationAmount = volume * currentPrice
                val evaluationProfit = evaluationAmount - totalBuyAmount
                val yield = if (totalBuyAmount != 0.0) (evaluationProfit / totalBuyAmount) * 100 else 0.0

                StockWiseItem(
                    stockName = name,
                    stockCode = code,
                    currentPrice = currentPrice,
                    holdVolume = volume,
                    buyAmount = totalBuyAmount,
                    buyAverage = totalBuyAmount / volume,
                    evaluationAmount = evaluationAmount,
                    evaluationProfit = evaluationProfit,
                    yield = yield,
                    currency = trans.first().currencyCode
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
