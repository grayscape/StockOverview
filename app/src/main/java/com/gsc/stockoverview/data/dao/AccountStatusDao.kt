package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.AccountStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountStatusDao {
    @Query("SELECT * FROM account_status ORDER BY id DESC LIMIT 1")
    fun getLatestAccountStatus(): Flow<AccountStatusEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountStatus(accountStatusEntity: AccountStatusEntity)

    @Query("DELETE FROM account_status")
    suspend fun deleteAll()
}