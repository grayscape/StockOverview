package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.entity.StockEntity
import kotlinx.coroutines.flow.Flow

class StockRepository(private val stockDao: StockDao) {
    val allStocks: Flow<List<StockEntity>> = stockDao.getAllStocks()

    suspend fun getStockByCode(code: String): StockEntity? = stockDao.getStockByCode(code)

    suspend fun getStockByName(name: String): StockEntity? = stockDao.getStockByName(name)

    suspend fun insertStock(stock: StockEntity) {
        stockDao.insertStock(stock)
    }

    suspend fun deleteAll() {
        stockDao.deleteAll()
    }

    /**
     * 기본적으로 등록되어야 할 종목들을 확인하고 없으면 추가합니다.
     */
    suspend fun ensureDefaultStocks() {
        val goldStockCode = "M04020000"
        val existingGold = stockDao.getStockByCode(goldStockCode)
        if (existingGold == null) {
            val goldStock = StockEntity(
                stockCode = goldStockCode,
                stockName = "금현물 99.99_1Kg",
                stockShortName = "금현물",
                stockType = "KOREA",
                marketType = "METALS", // 금 시세 조회를 위한 시장 구분
                currency = "KRW",
                currentPrice = 0.0
            )
            stockDao.insertStock(goldStock)
        }
    }
}
