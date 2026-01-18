package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.OverseasTradingLogRawDao
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import kotlinx.coroutines.flow.Flow

class OverseasTradingLogRawRepository(private val overseasTradingLogRawDao: OverseasTradingLogRawDao) {
    val allOverseasTradingLogRawList: Flow<List<OverseasTradingLogRawEntity>> = overseasTradingLogRawDao.getAllOverseasTradingLogRawList()

    suspend fun insertAll(logs: List<OverseasTradingLogRawEntity>) {
        overseasTradingLogRawDao.insertAll(logs)
    }

    suspend fun deleteAll() {
        overseasTradingLogRawDao.deleteAll()
    }
}
