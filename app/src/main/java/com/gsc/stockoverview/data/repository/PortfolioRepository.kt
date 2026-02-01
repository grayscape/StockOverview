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
import kotlinx.coroutines.withContext

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

    suspend fun getBaseAmount(): Double = withContext(Dispatchers.IO) {
        val allTransactions = transactionDao.getAllTransactions().first()
        allTransactions.filter { it.typeDetail == "이체입금" }.sumOf { it.amount }
    }

    data class StockStats(
        val volume: Double,
        val investedAmount: Double
    )

    suspend fun getStockStats(): Map<String, StockStats> = withContext(Dispatchers.IO) {
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

    suspend fun fetchCurrentPrice(stockCode: String): Double? = withContext(Dispatchers.IO) {
        val stock = stockDao.getStockByCode(stockCode) ?: return@withContext null
        val updatedStock = if (stock.currency == "KRW") {
            naverApi.fetchDomesticStockDetails(stockCode, stock.marketType)
        } else {
            yahooApi.fetchOverseasStockDetails(stockCode, stock.stockName, stock.currency)
        }
        
        updatedStock?.let {
            stockDao.insertStock(it)
            it.currentPrice
        } ?: stock.currentPrice
    }
    
    suspend fun getAllStocks(): List<StockEntity> = withContext(Dispatchers.IO) {
        stockDao.getAllStocks().first()
    }

    suspend fun initializeDefaultPortfolio() {
        if (portfolioDao.getCount() == 0) {
            val defaults = listOf(
                PortfolioEntity("379800", 20.0),
                PortfolioEntity("283580", 10.0),
                PortfolioEntity("294400", 10.0),
                PortfolioEntity("305080", 6.0),
                PortfolioEntity("456880", 6.0),
                PortfolioEntity("365780", 12.0),
                PortfolioEntity("411060", 16.0),
                PortfolioEntity("357870", 20.0)
            )
            portfolioDao.insertAll(defaults)
        }
    }
}
