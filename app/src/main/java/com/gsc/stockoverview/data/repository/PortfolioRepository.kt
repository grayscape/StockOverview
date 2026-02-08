package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.dao.PortfolioDao
import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.dao.TransactionDao
import com.gsc.stockoverview.data.entity.PortfolioEntity
import com.gsc.stockoverview.data.entity.StockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class PortfolioDomainModel(
    val items: List<PortfolioItemDomainModel>,
    val totalBaseAmount: Double,
    val totalEvaluationAmount: Double,
    val totalInvestedAmount: Double,
    val totalTargetWeight: Double,
    val allStocks: List<StockEntity>
)

data class PortfolioItemDomainModel(
    val stockCode: String,
    val stockName: String,
    val targetWeight: Double,
    val targetAmount: Double,
    val evaluationAmount: Double,
    val currentWeight: Double,
    val investedAmount: Double,
    val adjustmentAmount: Double,
    val adjustmentRate: Double,
    val currentPrice: Double,
    val volume: Double,
    val currency: String
)

class PortfolioRepository(
    private val portfolioDao: PortfolioDao,
    private val stockDao: StockDao,
    private val transactionDao: TransactionDao,
    private val naverApi: NaverStockApiService,
    private val yahooApi: YahooStockApiService
) {
    fun getPortfolioItems(): Flow<List<PortfolioEntity>> = portfolioDao.getAllPortfolioItems()

    suspend fun updateWeight(stockCode: String, weight: Double) {
        portfolioDao.insertPortfolioItem(PortfolioEntity(stockCode, weight))
    }

    suspend fun addStock(stockCode: String) {
        portfolioDao.insertPortfolioItem(PortfolioEntity(stockCode, 0.0))
    }

    suspend fun deleteStock(stockCode: String) {
        portfolioDao.deleteByStockCode(stockCode)
    }

    suspend fun getPortfolioDomainModel(): Flow<PortfolioDomainModel> {
        val baseAmount = getBaseAmount()
        val stats = getStockStats()
        val allStocks = getAllStocks()

        return getPortfolioItems().map { portfolioEntities ->
            val items = portfolioEntities.map { entity ->
                val stock = allStocks.find { it.stockCode == entity.stockCode }
                val currentPrice = fetchCurrentPrice(entity.stockCode) ?: stock?.currentPrice ?: 0.0
                val stat = stats[entity.stockCode]
                val vol = stat?.volume ?: 0.0
                val evalAmount = currentPrice * vol
                val targetAmt = baseAmount * (entity.targetWeight / 100.0)
                
                val adjAmt = targetAmt - evalAmount
                val adjRate = if (evalAmount != 0.0) (adjAmt / evalAmount) * 100.0 else 0.0
                
                PortfolioItemDomainModel(
                    stockCode = entity.stockCode,
                    stockName = stock?.stockShortName ?: stock?.stockName ?: entity.stockCode,
                    targetWeight = entity.targetWeight,
                    targetAmount = targetAmt,
                    evaluationAmount = evalAmount,
                    currentWeight = 0.0,
                    investedAmount = stat?.investedAmount ?: 0.0,
                    adjustmentAmount = adjAmt,
                    adjustmentRate = adjRate,
                    currentPrice = currentPrice,
                    volume = vol,
                    currency = stock?.currency ?: "KRW"
                )
            }
            
            val totalEval = items.sumOf { it.evaluationAmount }
            val totalInvested = items.sumOf { it.investedAmount }
            val totalTargetW = items.sumOf { it.targetWeight }
            
            val itemsWithWeight = items.map { 
                it.copy(currentWeight = if (totalEval != 0.0) (it.evaluationAmount / totalEval) * 100.0 else 0.0)
            }
            
            PortfolioDomainModel(
                items = itemsWithWeight,
                totalBaseAmount = baseAmount,
                totalEvaluationAmount = totalEval,
                totalInvestedAmount = totalInvested,
                totalTargetWeight = totalTargetW,
                allStocks = allStocks
            )
        }
    }

    private suspend fun getBaseAmount(): Double = withContext(Dispatchers.IO) {
        val allTransactions = transactionDao.getAllTransactions().first()
        allTransactions.filter { it.typeDetail == "이체입금" }.sumOf { it.amount }
    }

    private data class StockStats(
        val volume: Double,
        val investedAmount: Double
    )

    private suspend fun getStockStats(): Map<String, StockStats> = withContext(Dispatchers.IO) {
        val allTransactions = transactionDao.getAllTransactions().first()
        allTransactions.groupBy { it.stockCode }
            .mapValues { (_, transactions) ->
                var vol = 0.0
                var invested = 0.0
                transactions.sortedBy { it.tradeDate }.forEach { t ->
                    when (t.type) {
                        "매수" -> {
                            vol += t.volume
                            invested += t.amount + t.fee + t.tax
                        }
                        "매도" -> {
                            val avgCost = if (vol > 0) invested / vol else 0.0
                            vol -= t.volume
                            invested -= t.volume * avgCost
                        }
                    }
                }
                StockStats(vol, invested)
            }
    }

    private suspend fun fetchCurrentPrice(stockCode: String): Double? = withContext(Dispatchers.IO) {
        val stock = stockDao.getStockByCode(stockCode) ?: return@withContext null
        
        val updatedPrice = when {
            stock.marketType == "METALS" -> naverApi.fetchGoldPrice()
            stock.currency == "KRW" -> naverApi.fetchDomesticStockDetails(stockCode, stock.marketType)?.currentPrice
            else -> yahooApi.fetchOverseasStockDetails(stockCode, stock.stockName, stock.currency)?.currentPrice
        }
        
        updatedPrice?.let {
            val updatedStock = stock.copy(currentPrice = it)
            stockDao.insertStock(updatedStock)
            it
        } ?: stock.currentPrice
    }
    
    private suspend fun getAllStocks(): List<StockEntity> = withContext(Dispatchers.IO) {
        stockDao.getAllStocks().first()
    }
}
