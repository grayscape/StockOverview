package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.TransactionDao
import com.gsc.stockoverview.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun getTransactionsByAccount(account: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccount(account)
    }

    suspend fun insertAll(transactions: List<TransactionEntity>) {
        transactionDao.insertAll(transactions)
    }

    suspend fun deleteAll() {
        transactionDao.deleteAll()
    }
}
