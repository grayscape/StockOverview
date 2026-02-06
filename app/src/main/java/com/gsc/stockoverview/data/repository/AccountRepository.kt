package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.AccountStatusDao
import com.gsc.stockoverview.data.dao.AccountStockStatusDao
import com.gsc.stockoverview.data.entity.AccountStatusEntity
import com.gsc.stockoverview.data.entity.AccountStockStatusEntity
import kotlinx.coroutines.flow.Flow

class AccountRepository(
    private val accountStatusDao: AccountStatusDao,
    private val accountStockStatusDao: AccountStockStatusDao
) {
    val latestAccountStatusEntity: Flow<AccountStatusEntity?> = accountStatusDao.getLatestAccountStatus()
    val allAccountStockStatusEntity: Flow<List<AccountStockStatusEntity>> = accountStockStatusDao.getAllAccountStockStatus()

    suspend fun insertAccountStatus(accountStatusEntity: AccountStatusEntity) {
        accountStatusDao.insertAccountStatus(accountStatusEntity)
    }

    suspend fun insertAccountStockStatusList(list: List<AccountStockStatusEntity>) {
        accountStockStatusDao.insertAccountStockStatusList(list)
    }

    suspend fun clearAccountData() {
        accountStatusDao.deleteAll()
        accountStockStatusDao.deleteAll()
    }
}