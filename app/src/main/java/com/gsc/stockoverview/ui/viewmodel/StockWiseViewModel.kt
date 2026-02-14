package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.entity.AccountStockStatusEntity
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
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
    val realizedProfitLoss: Double = 0.0,   // 실현손익
    val holdVolume: Double = 0.0,           // 보유량
    val currentPrice: Double = 0.0,         // 현재시세
    val purchaseAmount: Double = 0.0,       // 매입금액
    val evaluationProfitRate: Double = 0.0, // 평가손익률
    val realizedProfitLossRate: Double = 0.0, // 실현손익률
    val currency: String = "KRW",
    val exchangeRate: Double = 1.0,         // 환율
    
    // 추가 항목 (3행)
    val weight: Double = 0.0,               // 비중
    val changeRate: Double = 0.0,           // 등락률
    val totalProfitLoss: Double = 0.0,      // 총손익
    val totalProfitLossRate: Double = 0.0,  // 총손익률
    val estimatedFee: Double = 0.0,         // 예상수수료
    
    // 추가 항목 (4행 - 직전 매수 정보)
    val lastBuyDate: String = "",           // 직전매수일
    val lastBuyVolume: Double = 0.0,        // 직전매수량
    val lastBuyAmount: Double = 0.0,        // 직전매수액
    val lastBuyProfitLoss: Double = 0.0,    // 직전손익
    val lastBuyProfitLossRate: Double = 0.0 // 직전손익률
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

    private val _stockPrices = MutableStateFlow<Map<String, Pair<Double, Double>>>(emptyMap()) // Price, ChangeRate
    private val _exchangeRate = MutableStateFlow(1450.0)
    
    // combine overload supports up to 5 flows. Use nested combine for 6 flows.
    val stockWiseList = combine(
        combine(
            accountStockStatusRepository.allAccountStockStatusEntity,
            stockDao.getAllStocks(),
            transactionRepository.allTransactions
        ) { statusList, stocks, transactions ->
            Triple(statusList, stocks, transactions)
        },
        combine(
            _selectedTab,
            _stockPrices,
            _exchangeRate
        ) { tab, prices, rate ->
            Triple(tab, prices, rate)
        }
    ) { data, params ->
        calculateStockWise(
            statusList = data.first,
            stocks = data.second,
            allTransactions = data.third,
            tab = params.first,
            prices = params.second,
            exchangeRate = params.third
        )
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
                    val stockInfo = when {
                        code == "M04020000" -> {
                            val p = naverApi.fetchGoldPrice()
                            Pair(p, 0.0)
                        }
                        currency == "KRW" -> {
                            val details = naverApi.fetchDomesticStockDetails(code)
                            Pair(details?.currentPrice ?: 0.0, details?.changeRate ?: 0.0)
                        }
                        else -> {
                            val details = yahooApi.fetchOverseasStockDetails(code, name)
                            Pair(details?.currentPrice ?: 0.0, details?.changeRate ?: 0.0)
                        }
                    }
                    code to stockInfo
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
        allTransactions: List<TransactionEntity>,
        tab: String,
        prices: Map<String, Pair<Double, Double>>,
        exchangeRate: Double
    ): List<StockWiseItem> {
        val stockMap = stocks.associateBy { it.stockCode }
        val filteredStatus = if (tab == "전체") statusList else statusList.filter { it.account == tab }
        val filteredTransactions = if (tab == "전체") allTransactions else allTransactions.filter { it.account == tab }
        
        // 전체 평가금액 합산 (비중 계산용) - 원화 환산 기준
        val totalEvaluationAmountInKRW = filteredStatus.sumOf { status ->
            val priceInfo = prices[status.stockCode] ?: Pair(0.0, 0.0)
            val price = priceInfo.first
            val rate = if (status.currencyCode == "USD") exchangeRate else 1.0
            status.quantity * price * rate
        }

        return filteredStatus.groupBy { it.stockCode }
            .map { (code, statuses) ->
                val stock = stockMap[code]
                val name = stock?.stockShortName ?: (statuses.firstOrNull()?.stockCode ?: "알수없음")
                val currency = statuses.firstOrNull()?.currencyCode ?: "KRW"
                val isUsd = currency == "USD"
                val currentRate = if (isUsd) exchangeRate else 1.0
                
                val totalQuantity = statuses.sumOf { it.quantity }
                val totalPurchaseAmount = statuses.sumOf { it.purchaseAmount }
                val totalRealizedProfitLoss = statuses.sumOf { it.realizedProfitLoss }
                
                val totalRealizedProfitLossRate = if (totalPurchaseAmount != 0.0) {
                    statuses.sumOf { it.realizedProfitLossRate.toDouble() * it.purchaseAmount } / totalPurchaseAmount
                } else 0.0

                val priceInfo = prices[code] ?: Pair(0.0, 0.0)
                val currentPrice = priceInfo.first
                val changeRate = priceInfo.second
                
                val evalAmount = totalQuantity * currentPrice
                val evalProfit = evalAmount - totalPurchaseAmount
                val evalProfitRate = if (totalPurchaseAmount != 0.0) (evalProfit / totalPurchaseAmount) * 100 else 0.0
                
                // 비중 계산
                val weight = if (totalEvaluationAmountInKRW > 0) {
                    (evalAmount * currentRate / totalEvaluationAmountInKRW) * 100
                } else 0.0

                // 총손익 & 총손익률
                val totalProfitLoss = evalProfit + totalRealizedProfitLoss
                val totalProfitLossRate = evalProfitRate + totalRealizedProfitLossRate

                // 예상수수료 계산 (국내 0.20%, 해외 0.25% 가정)
                val feeWeight = if (isUsd) 0.0025 else 0.0020
                val estimatedFee = evalAmount * feeWeight

                // 직전 매수 정보 (해당 종목의 가장 최근 '매수' 거래)
                val lastBuy = filteredTransactions
                    .filter { it.stockCode == code && (it.type == "매수") }
                    .maxByOrNull { it.tradeDate + it.transactionOrder.toString().padStart(5, '0') }

                StockWiseItem(
                    stockName = name,
                    stockCode = code,
                    averagePrice = (if (totalQuantity > 0) totalPurchaseAmount / totalQuantity else 0.0),
                    evaluationAmount = evalAmount,
                    evaluationProfit = evalProfit,
                    realizedProfitLoss = totalRealizedProfitLoss,
                    holdVolume = totalQuantity,
                    currentPrice = currentPrice,
                    purchaseAmount = totalPurchaseAmount,
                    evaluationProfitRate = evalProfitRate,
                    realizedProfitLossRate = totalRealizedProfitLossRate,
                    currency = currency,
                    exchangeRate = currentRate,
                    weight = weight,
                    changeRate = changeRate,
                    totalProfitLoss = totalProfitLoss,
                    totalProfitLossRate = totalProfitLossRate,
                    estimatedFee = estimatedFee,
                    lastBuyDate = lastBuy?.tradeDate ?: "-",
                    lastBuyVolume = lastBuy?.volume ?: 0.0,
                    lastBuyAmount = lastBuy?.amount ?: 0.0,
                    lastBuyProfitLoss = lastBuy?.profitLoss ?: 0.0,
                    lastBuyProfitLossRate = lastBuy?.yield ?: 0.0
                )
            }
            .filter { it.holdVolume > 0 || it.realizedProfitLoss != 0.0 }
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
