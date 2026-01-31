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
        SELECT * FROM `transaction` 
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
            account ASC,
            id DESC
    """)
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM `transaction`")
    suspend fun deleteAll()
}
