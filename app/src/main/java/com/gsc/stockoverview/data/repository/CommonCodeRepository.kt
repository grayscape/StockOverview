package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.CommonCodeDao
import com.gsc.stockoverview.data.entity.CommonCodeEntity
import kotlinx.coroutines.flow.Flow

class CommonCodeRepository(private val commonCodeDao: CommonCodeDao) {
    val allCodes: Flow<List<CommonCodeEntity>> = commonCodeDao.getAllCodes()

    fun getCodesByParent(parentCode: String): Flow<List<CommonCodeEntity>> {
        return commonCodeDao.getCodesByParent(parentCode)
    }

    suspend fun getCode(code: String): CommonCodeEntity? {
        return commonCodeDao.getCode(code)
    }

    suspend fun insert(code: CommonCodeEntity) {
        commonCodeDao.insert(code)
    }

    suspend fun insertAll(codes: List<CommonCodeEntity>) {
        commonCodeDao.insertAll(codes)
    }

    suspend fun delete(code: String) {
        commonCodeDao.delete(code)
    }

    suspend fun deleteAll() {
        commonCodeDao.deleteAll()
    }
}
