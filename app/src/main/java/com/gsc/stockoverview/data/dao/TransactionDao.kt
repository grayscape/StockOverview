package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("""
        SELECT 
            t.id, t.account, t.trade_date, t.type, t.type_detail, t.stock_code,
            CASE WHEN s.stock_short_name IS NOT NULL THEN s.stock_short_name ELSE t.transaction_name END as transaction_name,
            t.price, t.volume, t.fee, t.tax, t.amount, t.profit_loss, t.yield, t.currency_code, t.transaction_order
        FROM `transaction` t
        LEFT JOIN stock s ON t.stock_code = s.stock_code
        ORDER BY
            t.trade_date DESC,
            t.id DESC
    """)
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("""
        SELECT 
            t.id, t.account, t.trade_date, t.type, t.type_detail, t.stock_code,
            CASE WHEN s.stock_short_name IS NOT NULL THEN s.stock_short_name ELSE t.transaction_name END as transaction_name,
            t.price, t.volume, t.fee, t.tax, t.amount, t.profit_loss, t.yield, t.currency_code, t.transaction_order
        FROM `transaction` t
        LEFT JOIN stock s ON t.stock_code = s.stock_code
        WHERE t.account = :account
        ORDER BY
            t.trade_date DESC,
            t.id DESC
    """)
    fun getTransactionsByAccount(account: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM `transaction`")
    suspend fun deleteAll()
}
