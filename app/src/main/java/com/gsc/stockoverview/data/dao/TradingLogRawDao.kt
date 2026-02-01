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
        SELECT 
            CASE WHEN cc.name IS NOT NULL THEN cc.name ELSE tr.account END as account,
            tr.trade_date, tr.stock_name, tr.buy_quantity, tr.buy_price, tr.buy_amount,
            tr.sell_quantity, tr.sell_price, tr.sell_amount, tr.trade_fee, tr.profit_loss, tr.yield
        FROM trading_log_raw tr
        LEFT JOIN common_code cc ON tr.account = cc.code AND cc.parent_code = 'ACC_ROOT'
        ORDER BY 
            IFNULL(cc.sort_order, 999) ASC, 
            tr.trade_date DESC,
            tr.stock_name ASC
    """)
    fun getAllTradingLogRawList(): Flow<List<TradingLogRawEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<TradingLogRawEntity>)

    @Query("DELETE FROM trading_log_raw")
    suspend fun deleteAll()
}
