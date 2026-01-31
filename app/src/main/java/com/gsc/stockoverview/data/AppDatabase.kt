package com.gsc.stockoverview.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gsc.stockoverview.data.dao.OverseasTradingLogRawDao
import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.dao.TradingLogRawDao
import com.gsc.stockoverview.data.dao.TransactionDao
import com.gsc.stockoverview.data.dao.TransactionRawDao
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.entity.TransactionRawEntity

@Database(
    entities = [
        TransactionRawEntity::class,
        TradingLogRawEntity::class,
        OverseasTradingLogRawEntity::class,
        TransactionEntity::class,
        StockEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionRawDao(): TransactionRawDao
    abstract fun tradingLogRawDao(): TradingLogRawDao
    abstract fun overseasTradingLogRawDao(): OverseasTradingLogRawDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockDao(): StockDao

    /**
     * 모든 테이블의 데이터를 삭제하여 DB를 초기화합니다.
     */
    suspend fun clearAllData() {
        transactionRawDao().deleteAll()
        tradingLogRawDao().deleteAll()
        overseasTradingLogRawDao().deleteAll()
        transactionDao().deleteAll()
        stockDao().deleteAll()
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stock_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
