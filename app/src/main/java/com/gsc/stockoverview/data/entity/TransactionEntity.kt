package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transaction")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "account")
    val account: String,            // 계좌

    @ColumnInfo(name = "trade_date")
    val tradeDate: String,          // 매매일자

    @ColumnInfo(name = "type")
    val type: String,               // 거래종류

    @ColumnInfo(name = "type_detail")
    val typeDetail: String,         // 거래종류상세

    @ColumnInfo(name = "stock_name")
    val stockName: String,          // 종목명

    @ColumnInfo(name = "price")
    val price: Double,              // 단가

    @ColumnInfo(name = "quantity")
    val quantity: Long,             // 수량

    @ColumnInfo(name = "fee")
    val fee: Long,                  // 수수료

    @ColumnInfo(name = "tax")
    val tax: Long,                  // 세금

    @ColumnInfo(name = "amount")
    val amount: Long,               // 거래금액

    @ColumnInfo(name = "profit_loss")
    val profitLoss: Long,           // 손익금액

    @ColumnInfo(name = "yield")
    val yield: Double,              // 수익률

    @ColumnInfo(name = "exchange_rate")
    val exchangeRate: Double,       // 환율

    @ColumnInfo(name = "exchange_profit_loss")
    val exchangeProfitLoss: Long    // 환차손익
)
