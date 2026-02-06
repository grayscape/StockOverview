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
    @ColumnInfo(name = "principal")
    val principal: Long, // 원금
    @ColumnInfo(name = "evaluated_asset")
    val evaluatedAsset: Long, // 평가자산
    @ColumnInfo(name = "asset_return_rate")
    val assetReturnRate: Float, // 자산수익률
    @ColumnInfo(name = "operating_funds")
    val operatingFunds: Long, // 운용자금
    @ColumnInfo(name = "evaluation_amount")
    val evaluationAmount: Long, // 평가금액
    @ColumnInfo(name = "evaluation_profit")
    val evaluationProfit: Long, // 평가수익
    @ColumnInfo(name = "realized_profit_loss")
    val realizedProfitLoss: Long, // 실현손익
    @ColumnInfo(name = "realized_profit_loss_rate")
    val realizedProfitLossRate: Float, // 실현손익률
    @ColumnInfo(name = "deposit")
    val deposit: Long // 예수금
)
