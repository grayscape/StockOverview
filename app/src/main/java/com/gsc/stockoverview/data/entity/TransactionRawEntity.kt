package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "transaction_raw",
    primaryKeys = ["account", "transaction_date", "transaction_no"],
    indices = [Index(value = ["account", "transaction_date", "transaction_no"], unique = true)]
)
data class TransactionRawEntity(
    @ColumnInfo(name = "account")
    val account: String,                // A: 계좌

    @ColumnInfo(name = "transaction_date")
    val transactionDate: String,        // C: 거래일자
    
    @ColumnInfo(name = "transaction_no")
    val transactionNo: String,          // D: 거래번호
    
    @ColumnInfo(name = "original_no")
    val originalNo: String,             // E: 원번호
    
    @ColumnInfo(name = "type")
    val type: String,                   // F: 거래종류

    @ColumnInfo(name = "transaction_name")
    val transactionName: String,        // G: 거래명
    
    @ColumnInfo(name = "quantity")
    val quantity: Long,                 // I: 수량
    
    @ColumnInfo(name = "price")
    val price: Double,                  // J: 단가
    
    @ColumnInfo(name = "amount")
    val amount: Double,                   // K: 거래금액 (원화 또는 외화 일부)
    
    @ColumnInfo(name = "deposit_withdrawal_amount")
    val depositWithdrawalAmount: Long,  // L: 입출금액
    
    @ColumnInfo(name = "balance")
    val balance: Long,                  // M: 예수금
    
    @ColumnInfo(name = "stock_balance")
    val stockBalance: Long,             // N: 유가잔고
    
    @ColumnInfo(name = "fee")
    val fee: Double,                    // O: 수수료 (해외의 경우 외화일 수 있음)
    
    @ColumnInfo(name = "tax")
    val tax: Double,                    // P: 제세금합 (해외의 경우 외화일 수 있음)
    
    @ColumnInfo(name = "foreign_amount")
    val foreignAmount: Double,          // Q: 외화거래금액
    
    @ColumnInfo(name = "foreign_dw_amount")
    val foreignDWAmount: Double,        // R: 외화입출금액
    
    @ColumnInfo(name = "foreign_balance")
    val foreignBalance: Double,         // S: 외화예수금
    
    @ColumnInfo(name = "foreign_stock")
    val foreignStock: Double,           // T: 외화유가증권
    
    @ColumnInfo(name = "uncollected_amount")
    val uncollectedAmount: Long,        // U: 미수발생금액
    
    @ColumnInfo(name = "repaid_amount")
    val repaidAmount: Long,             // V: 미수변제금액
    
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,           // W: 통화코드
    
    @ColumnInfo(name = "relative_agency")
    val relativeAgency: String,         // X: 상대기관
    
    @ColumnInfo(name = "relative_client_name")
    val relativeClientName: String,     // Y: 상대고객명
    
    @ColumnInfo(name = "relative_account_number")
    val relativeAccountNumber: String,  // Z: 상대계좌번호
    
    @ColumnInfo(name = "recipient_display")
    val recipientDisplay: String,       // AA: 받는분표시
    
    @ColumnInfo(name = "my_account_display")
    val myAccountDisplay: String        // AB: 내계좌표시
)
