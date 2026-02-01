package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OverseasTradingLogRawDao {
    @Query("""
        SELECT 
            CASE WHEN cc.name IS NOT NULL THEN cc.name ELSE tr.account END as account,
            tr.trade_date, tr.currency, tr.stock_number, tr.stock_name, tr.balance_quantity,
            tr.buy_average_exchange_rate, tr.trading_exchange_rate, tr.buy_quantity,
            tr.buy_price, tr.buy_amount, tr.won_buy_amount, tr.sell_quantity, tr.sell_price,
            tr.sell_amount, tr.won_sell_amount, tr.fee, tr.tax, tr.won_total_cost,
            tr.original_buy_average_price, tr.trading_profit, tr.won_trading_profit,
            tr.exchange_profit, tr.total_evaluation_profit, tr.yield, tr.converted_yield
        FROM overseas_trading_log_raw tr
        LEFT JOIN common_code cc ON tr.account = cc.code AND cc.parent_code = 'ACC_ROOT'
        ORDER BY 
            IFNULL(cc.sort_order, 999) ASC, 
            tr.trade_date DESC,
            tr.stock_name ASC
    """)
    fun getAllOverseasTradingLogRawList(): Flow<List<OverseasTradingLogRawEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<OverseasTradingLogRawEntity>)

    @Query("DELETE FROM overseas_trading_log_raw")
    suspend fun deleteAll()
}
