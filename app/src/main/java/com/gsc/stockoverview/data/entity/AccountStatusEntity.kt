package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "account_status")
data class AccountStatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "account")
    val account: String, // 계좌
    @ColumnInfo(name = "total_deposit")
    val totalDeposit: Double, // 총 입금액
    @ColumnInfo(name = "total_withdrawal")
    val totalWithdrawal: Double, // 총 출금액
    @ColumnInfo(name = "principal")
    val principal: Double, // 원금 (입금 - 출금)
    @ColumnInfo(name = "krw_realized_profit_loss")
    val krwRealizedProfitLoss: Double, // 원화실현손익
    @ColumnInfo(name = "krw_realized_profit_loss_rate")
    val krwRealizedProfitLossRate: Float, // 원화실현손익률
    @ColumnInfo(name = "usd_realized_profit_loss")
    val usdRealizedProfitLoss: Double, // 달러실현손익
    @ColumnInfo(name = "usd_realized_profit_loss_rate")
    val usdRealizedProfitLossRate: Float, // 달러실현손익률
    @ColumnInfo(name = "krw_purchase_amount")
    val krwPurchaseAmount: Double, // 원화매입금액
    @ColumnInfo(name = "usd_purchase_amount")
    val usdPurchaseAmount: Double, // 달러매입금액
    @ColumnInfo(name = "krw_deposit")
    val krwDeposit: Double, // 원화예수금
    @ColumnInfo(name = "usd_deposit")
    val usdDeposit: Double // 달러예수금
)
