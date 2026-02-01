package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionRawDao {
    @Query("""
        SELECT 
            CASE WHEN cc.name IS NOT NULL THEN cc.name ELSE tr.account END as account,
            tr.transaction_date, tr.transaction_no, tr.original_no, tr.type, tr.transaction_name,
            tr.quantity, tr.price, tr.amount, tr.deposit_withdrawal_amount, tr.balance,
            tr.stock_balance, tr.fee, tr.tax, tr.foreign_amount, tr.foreign_dw_amount,
            tr.foreign_balance, tr.foreign_stock, tr.uncollected_amount, tr.repaid_amount,
            tr.currency_code, tr.relative_agency, tr.relative_client_name,
            tr.relative_account_number, tr.recipient_display, tr.my_account_display
        FROM transaction_raw tr
        LEFT JOIN common_code cc ON tr.account = cc.code AND cc.parent_code = 'ACC_ROOT'
    """)
    fun getAllTransactionRaw(): Flow<List<TransactionRawEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionRawEntity>)

    @Query("DELETE FROM transaction_raw")
    suspend fun deleteAll()
}
