package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.AccountStockStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountStockStatusDao {
    @Query("SELECT * FROM account_stock_status")
    fun getAllAccountStockStatus(): Flow<List<AccountStockStatusEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountStockStatusList(list: List<AccountStockStatusEntity>)

    @Query("DELETE FROM account_stock_status")
    suspend fun deleteAll()
}