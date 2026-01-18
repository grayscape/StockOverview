package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "overseas_trading_log_raw",
    primaryKeys = ["account", "trade_date", "stock_number"],
    indices = [Index(value = ["account", "trade_date", "stock_number"], unique = true)]
)
data class OverseasTradingLogRawEntity(
    @ColumnInfo(name = "account")
    val account: String,                // A: 계좌

    @ColumnInfo(name = "trade_date")
    val tradeDate: String,              // B: 매매일자

    @ColumnInfo(name = "currency")
    val currency: String,               // C: 통화

    @ColumnInfo(name = "stock_number")
    val stockNumber: String,            // D: 종목번호

    @ColumnInfo(name = "stock_name")
    val stockName: String,              // E: 종목명

    @ColumnInfo(name = "balance_quantity")
    val balanceQuantity: Long,          // F: 잔고수량

    @ColumnInfo(name = "buy_average_exchange_rate")
    val buyAverageExchangeRate: Double, // G: 매입평균환율

    @ColumnInfo(name = "trading_exchange_rate")
    val tradingExchangeRate: Double,    // H: 매매환율

    @ColumnInfo(name = "buy_quantity")
    val buyQuantity: Long,              // I: 매수수량

    @ColumnInfo(name = "buy_price")
    val buyPrice: Double,               // J: 매수단가

    @ColumnInfo(name = "buy_amount")
    val buyAmount: Double,              // K: 매수금액

    @ColumnInfo(name = "won_buy_amount")
    val wonBuyAmount: Long,             // L: 원화매수금액

    @ColumnInfo(name = "sell_quantity")
    val sellQuantity: Long,             // M: 매도수량

    @ColumnInfo(name = "sell_price")
    val sellPrice: Double,              // N: 매도단가

    @ColumnInfo(name = "sell_amount")
    val sellAmount: Double,             // O: 매도금액

    @ColumnInfo(name = "won_sell_amount")
    val wonSellAmount: Long,            // P: 원화매도금액

    @ColumnInfo(name = "fee")
    val fee: Double,                    // Q: 수수료

    @ColumnInfo(name = "tax")
    val tax: Double,                    // R: 세금

    @ColumnInfo(name = "won_total_cost")
    val wonTotalCost: Long,             // S: 원화총비용

    @ColumnInfo(name = "original_buy_average_price")
    val originalBuyAveragePrice: Double, // T: 원매수평균단가

    @ColumnInfo(name = "trading_profit")
    val tradingProfit: Double,          // U: 매매손익

    @ColumnInfo(name = "won_trading_profit")
    val wonTradingProfit: Long,         // V: 원화매매손익

    @ColumnInfo(name = "exchange_profit")
    val exchangeProfit: Double,         // W: 환차손익

    @ColumnInfo(name = "total_evaluation_profit")
    val totalEvaluationProfit: Double,  // X: 총평가손익

    @ColumnInfo(name = "yield")
    val yield: Double,                  // Y: 손익률

    @ColumnInfo(name = "converted_yield")
    val convertedYield: Double          // Z: 환산손익률
)
