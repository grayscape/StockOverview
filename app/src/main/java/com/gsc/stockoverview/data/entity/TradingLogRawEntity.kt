package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "trading_log_raw",
    primaryKeys = ["account", "trade_date", "stock_name"],
    indices = [Index(value = ["account", "trade_date", "stock_name"], unique = true)]
)
data class TradingLogRawEntity(
    @ColumnInfo(name = "account")
    val account: String,        // 계좌
    
    @ColumnInfo(name = "trade_date")
    val tradeDate: String,      // 매매일자
    
    @ColumnInfo(name = "stock_name")
    val stockName: String,      // 종목명
    
    @ColumnInfo(name = "buy_quantity")
    val buyQuantity: Long,      // 매수수량
    
    @ColumnInfo(name = "buy_price")
    val buyPrice: Double,       // 매수평균단가
    
    @ColumnInfo(name = "buy_amount")
    val buyAmount: Long,        // 매수금액
    
    @ColumnInfo(name = "sell_quantity")
    val sellQuantity: Long,     // 매도수량
    
    @ColumnInfo(name = "sell_price")
    val sellPrice: Double,      // 매도평균단가
    
    @ColumnInfo(name = "sell_amount")
    val sellAmount: Long,       // 매도금액
    
    @ColumnInfo(name = "trade_fee")
    val tradeFee: Long,         // 매매비용
    
    @ColumnInfo(name = "profit_loss")
    val profitLoss: Long,       // 손익금액
    
    @ColumnInfo(name = "yield")
    val yield: Double           // 수익률
)
