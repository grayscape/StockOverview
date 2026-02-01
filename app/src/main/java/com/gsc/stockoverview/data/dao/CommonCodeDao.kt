package com.gsc.stockoverview.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.gsc.stockoverview.data.entity.CommonCodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommonCodeDao {
    @Query("SELECT * FROM common_code ORDER BY parent_code ASC, sort_order ASC")
    fun getAllCodes(): Flow<List<CommonCodeEntity>>

    @Query("SELECT * FROM common_code WHERE parent_code = :parentCode AND is_active = 1 ORDER BY sort_order ASC")
    fun getCodesByParent(parentCode: String): Flow<List<CommonCodeEntity>>

    @Query("SELECT * FROM common_code WHERE code = :code LIMIT 1")
    suspend fun getCode(code: String): CommonCodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(code: CommonCodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(codes: List<CommonCodeEntity>)

    @Query("DELETE FROM common_code WHERE code = :code")
    suspend fun delete(code: String)

    @Query("DELETE FROM common_code")
    suspend fun deleteAll()
}
