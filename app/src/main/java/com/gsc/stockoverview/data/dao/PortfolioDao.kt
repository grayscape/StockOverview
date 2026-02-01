package com.gsc.stockoverview.data.dao

import androidx.room.*
import com.gsc.stockoverview.data.entity.PortfolioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolio")
    fun getAllPortfolioItems(): Flow<List<PortfolioEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolioItem(item: PortfolioEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PortfolioEntity>)

    @Delete
    suspend fun deletePortfolioItem(item: PortfolioEntity)

    @Query("DELETE FROM portfolio WHERE stock_code = :stockCode")
    suspend fun deleteByStockCode(stockCode: String)

    @Query("DELETE FROM portfolio")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM portfolio")
    suspend fun getCount(): Int
}
