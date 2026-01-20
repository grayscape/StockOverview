package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.entity.StockEntity
import kotlinx.coroutines.flow.Flow

class StockRepository(private val stockDao: StockDao) {
    val allStocks: Flow<List<StockEntity>> = stockDao.getAllStocks()

    suspend fun getStockByName(name: String): StockEntity? = stockDao.getStockByName(name)

    suspend fun insertStock(stock: StockEntity) {
        stockDao.insertStock(stock)
    }

    suspend fun deleteAll() {
        stockDao.deleteAll()
    }
}
