package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_stock_status")
data class AccountStockStatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "account")
    val account: String, // 계좌
    @ColumnInfo(name = "stock_code")
    val stockCode: String, // 종목코드
    @ColumnInfo(name = "quantity")
    val quantity: Int, // 수량
    @ColumnInfo(name = "average_purchase_price")
    val averagePurchasePrice: Double, // 매입평균액
    @ColumnInfo(name = "investment_amount")
    val investmentAmount: Long, // 투자금액
    @ColumnInfo(name = "evaluation_amount")
    val evaluationAmount: Long, // 평가금액
    @ColumnInfo(name = "evaluation_profit")
    val evaluationProfit: Long, // 평가수익금
    @ColumnInfo(name = "evaluation_return_rate")
    val evaluationReturnRate: Float, // 평가수익률
    @ColumnInfo(name = "realized_profit_loss")
    val realizedProfitLoss: Long, // 실현손익
    @ColumnInfo(name = "realized_profit_loss_rate")
    val realizedProfitLossRate: Float // 실현손익률
)
