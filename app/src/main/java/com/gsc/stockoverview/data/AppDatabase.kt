package com.gsc.stockoverview.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gsc.stockoverview.data.dao.*
import com.gsc.stockoverview.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionRawEntity::class,
        TradingLogRawEntity::class,
        OverseasTradingLogRawEntity::class,
        TransactionEntity::class,
        StockEntity::class,
        CommonCodeEntity::class,
        PortfolioEntity::class
    ],
    version = 18,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionRawDao(): TransactionRawDao
    abstract fun tradingLogRawDao(): TradingLogRawDao
    abstract fun overseasTradingLogRawDao(): OverseasTradingLogRawDao
    abstract fun transactionDao(): TransactionDao
    abstract fun stockDao(): StockDao
    abstract fun commonCodeDao(): CommonCodeDao
    abstract fun portfolioDao(): PortfolioDao

    /**
     * 모든 테이블의 데이터를 삭제하여 DB를 초기화합니다.
     */
    suspend fun clearAllData() {
        transactionRawDao().deleteAll()
        tradingLogRawDao().deleteAll()
        overseasTradingLogRawDao().deleteAll()
        transactionDao().deleteAll()
        stockDao().deleteAll()
        commonCodeDao().deleteAll()
        portfolioDao().deleteAll()
        
        // 기본 데이터 다시 삽입
        populateInitialData(this)
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
                .addCallback(DatabaseCallback(context))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(database: AppDatabase) {
            // 1. 기본 공통 코드 삽입
            val commonCodeDao = database.commonCodeDao()
            val defaultCodes = listOf(
                CommonCodeEntity(code = "ACC_ROOT", name = "계좌구분", description = "계좌 구분 루트 코드"),
                CommonCodeEntity(code = "일반", parentCode = "ACC_ROOT", name = "일반", sortOrder = 1),
                CommonCodeEntity(code = "ISA", parentCode = "ACC_ROOT", name = "ISA", sortOrder = 2),
                CommonCodeEntity(code = "연금", parentCode = "ACC_ROOT", name = "연금", sortOrder = 3),
                CommonCodeEntity(code = "IRP", parentCode = "ACC_ROOT", name = "IRP", sortOrder = 4),
                CommonCodeEntity(code = "퇴직IRP", parentCode = "ACC_ROOT", name = "퇴직IRP", sortOrder = 5),
                CommonCodeEntity(code = "금통장", parentCode = "ACC_ROOT", name = "금통장", sortOrder = 6),
                CommonCodeEntity(code = "CMA", parentCode = "ACC_ROOT", name = "CMA", sortOrder = 7)
            )
            commonCodeDao.insertAll(defaultCodes)

            // 2. 기본 종목(금현물) 삽입
            val stockDao = database.stockDao()
            val goldStock = StockEntity(
                stockCode = "M04020000",
                stockName = "금현물 99.99_1Kg",
                stockShortName = "금현물",
                stockType = "KOREA",
                marketType = "METALS",
                currency = "KRW",
                currentPrice = 0.0
            )
            stockDao.insertStock(goldStock)

            // 3. 기본 포트폴리오 삽입
            val portfolioDao = database.portfolioDao()
            val defaultPortfolio = listOf(
                PortfolioEntity("379800", 20.0),
                PortfolioEntity("283580", 10.0),
                PortfolioEntity("294400", 10.0),
                PortfolioEntity("305080", 6.0),
                PortfolioEntity("456880", 6.0),
                PortfolioEntity("365780", 12.0),
                PortfolioEntity("411060", 16.0),
                PortfolioEntity("357870", 20.0)
            )
            portfolioDao.insertAll(defaultPortfolio)
        }

        private class DatabaseCallback(
            private val context: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialData(database)
                    }
                }
            }
        }
    }
}
