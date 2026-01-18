package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.TradingLogRawDao
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import kotlinx.coroutines.flow.Flow

class TradingLogRawRepository(private val tradingLogRawDao: TradingLogRawDao) {
    val allTradingLogRawList: Flow<List<TradingLogRawEntity>> = tradingLogRawDao.getAllTradingLogRawList()

    suspend fun insertAll(logs: List<TradingLogRawEntity>) {
        tradingLogRawDao.insertAll(logs)
    }

    suspend fun deleteAll() {
        tradingLogRawDao.deleteAll()
    }
}
