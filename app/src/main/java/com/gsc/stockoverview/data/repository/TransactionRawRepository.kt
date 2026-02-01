package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.TransactionRawDao
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import kotlinx.coroutines.flow.Flow

class TransactionRawRepository(private val transactionRawDao: TransactionRawDao) {
    val allTransactionRawList: Flow<List<TransactionRawEntity>> = transactionRawDao.getAllTransactionRaw()

    suspend fun insertAll(transactions: List<TransactionRawEntity>) {
        transactionRawDao.insertAll(transactions)
    }

    suspend fun deleteAll() {
        transactionRawDao.deleteAll()
    }
}
