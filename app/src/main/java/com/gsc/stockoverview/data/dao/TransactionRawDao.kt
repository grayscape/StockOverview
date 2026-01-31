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
        SELECT * FROM transaction_raw 
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
            transaction_date DESC,
            transaction_no DESC
    """)
    fun getAllTransactionRawList(): Flow<List<TransactionRawEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionRawEntity>)

    @Query("DELETE FROM transaction_raw")
    suspend fun deleteAll()
}
