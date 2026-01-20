package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.StockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {
    @Query("SELECT * FROM stock")
    fun getAllStocks(): Flow<List<StockEntity>>

    @Query("SELECT * FROM stock WHERE stock_code = :stockCode")
    suspend fun getStockByCode(stockCode: String): StockEntity?

    @Query("SELECT * FROM stock WHERE stock_name = :stockName LIMIT 1")
    suspend fun getStockByName(stockName: String): StockEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStock(stock: StockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stocks: List<StockEntity>)

    @Query("DELETE FROM stock")
    suspend fun deleteAll()
}
