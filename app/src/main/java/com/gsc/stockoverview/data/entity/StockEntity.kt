package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock")
data class StockEntity(
    @PrimaryKey
    @ColumnInfo(name = "stock_code")
    val stockCode: String,          // 종목코드

    @ColumnInfo(name = "stock_name")
    val stockName: String,          // 종목명

    @ColumnInfo(name = "stock_short_name")
    val stockShortName: String = stockName, // 종목약어명 (기본값: 종목명)

    @ColumnInfo(name = "stock_type")
    val stockType: String,          // 종목유형

    @ColumnInfo(name = "current_price")
    val currentPrice: Double,       // 현재가

    @ColumnInfo(name = "market_type")
    val marketType: String,         // 시장구분

    @ColumnInfo(name = "currency")
    val currency: String            // 통화
)
