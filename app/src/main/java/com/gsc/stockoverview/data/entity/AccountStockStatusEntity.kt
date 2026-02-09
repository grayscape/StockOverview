package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "account_stock_status", primaryKeys = ["account", "stock_code"])
data class AccountStockStatusEntity(
    @ColumnInfo(name = "account")
    val account: String, // 계좌
    @ColumnInfo(name = "stock_code")
    val stockCode: String, // 종목코드
    @ColumnInfo(name = "quantity")
    val quantity: Double, // 수량
    @ColumnInfo(name = "average_purchase_price")
    val averagePurchasePrice: Double, // 매입평균액
    @ColumnInfo(name = "investment_amount")
    val investmentAmount: Double, // 투자금액
    @ColumnInfo(name = "realized_profit_loss")
    val realizedProfitLoss: Double, // 실현손익
    @ColumnInfo(name = "realized_profit_loss_rate")
    val realizedProfitLossRate: Float, // 실현손익률
    @ColumnInfo(name = "currency_code")
    val currencyCode: String // 통화코드
)
