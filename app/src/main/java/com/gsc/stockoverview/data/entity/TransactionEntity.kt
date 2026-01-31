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

    @ColumnInfo(name = "stock_code")
    val stockCode: String,          // 종목코드

    @ColumnInfo(name = "transaction_name")
    val transactionName: String,    // 거래명

    @ColumnInfo(name = "price")
    val price: Double,              // 단가

    @ColumnInfo(name = "quantity")
    val quantity: Long,             // 수량

    @ColumnInfo(name = "fee")
    val fee: Double,                // 수수료 (Double로 변경)

    @ColumnInfo(name = "tax")
    val tax: Double,                // 세금 (Double로 변경)

    @ColumnInfo(name = "amount")
    val amount: Double,             // 거래금액

    @ColumnInfo(name = "profit_loss")
    val profitLoss: Double,         // 손익금액 (Double로 변경)

    @ColumnInfo(name = "yield")
    val yield: Double,              // 수익률

    @ColumnInfo(name = "currency_code")
    val currencyCode: String        // 통화코드 (추가됨)
)
