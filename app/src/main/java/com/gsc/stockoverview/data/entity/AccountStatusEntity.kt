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
    @ColumnInfo(name = "krw_profit_loss_amount")
    val krwProfitLossAmount: Double, // 원화손익금액
    @ColumnInfo(name = "krw_profit_loss_rate")
    val krwProfitLossRate: Float, // 원화손익률
    @ColumnInfo(name = "usd_profit_loss_amount")
    val usdProfitLossAmount: Double, // 달러손익금액
    @ColumnInfo(name = "usd_profit_loss_rate")
    val usdProfitLossRate: Float, // 달러손익률
    @ColumnInfo(name = "krw_purchase_amount")
    val krwPurchaseAmount: Double, // 원화매입금액
    @ColumnInfo(name = "usd_purchase_amount")
    val usdPurchaseAmount: Double, // 달러매입금액
    @ColumnInfo(name = "krw_deposit")
    val krwDeposit: Double, // 원화예수금
    @ColumnInfo(name = "usd_deposit")
    val usdDeposit: Double // 달러예수금
)
