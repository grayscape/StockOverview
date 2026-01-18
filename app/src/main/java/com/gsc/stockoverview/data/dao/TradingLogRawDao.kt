package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TradingLogRawDao {
    @Query("""
        SELECT * FROM trading_log_raw 
        ORDER BY 
            CASE account 
                WHEN '일반' THEN 1 
                WHEN '연금' THEN 2 
                WHEN 'ISA' THEN 3 
                WHEN 'IRP' THEN 4 
                WHEN '퇴직IRP' THEN 5 
                WHEN '금통장' THEN 6 
                WHEN 'CMA' THEN 7 
                ELSE 8 
            END ASC, 
            trade_date DESC,
            stock_name ASC
    """)
    fun getAllTradingLogRawList(): Flow<List<TradingLogRawEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<TradingLogRawEntity>)

    @Query("DELETE FROM trading_log_raw")
    suspend fun deleteAll()
}
