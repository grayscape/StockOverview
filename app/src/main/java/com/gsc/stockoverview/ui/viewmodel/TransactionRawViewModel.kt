package com.gsc.stockoverview.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import com.gsc.stockoverview.data.repository.TransactionRawRepository
import com.gsc.stockoverview.utils.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionRawViewModel(
    private val repository: TransactionRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    val transactionRawList: Flow<List<TransactionRawEntity>> = repository.allTransactionRawList

    fun importTransactionRawList(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val rawData = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "전체거래내역") { reader -> mapRowToEntity(reader) }
            }

            if (rawData.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.deleteAll()
                    repository.insertAll(rawData)
                }
                onResult(rawData.size)
            }
        }
    }

    /**
     * 이미지 분석 기반 엑셀 인덱스 최종 매핑
     * 0:계좌, 1:일자, 2:번호, 4:거래종류, 5:종목명, 6:수량, 7:단가, 8:원화금액, 11:수수료, 12:세금, 13:외화금액, 21:통화
     */
    fun mapRowToEntity(reader: ExcelReader.RowReader): TransactionRawEntity {
        return TransactionRawEntity(
            account = reader.getString(0),
            transactionDate = reader.getString(1),
            transactionNo = reader.getString(2),
            originalNo = reader.getString(3),
            type = reader.getString(4),
            transactionName = reader.getString(5),
            quantity = reader.getLong(6),
            price = reader.getDouble(7),
            amount = reader.getDouble(8),
            depositWithdrawalAmount = reader.getLong(9),
            balance = reader.getLong(10),
            stockBalance = reader.getLong(11),
            fee = reader.getDouble(12),
            tax = reader.getDouble(13),
            foreignAmount = reader.getDouble(14),
            foreignDWAmount = reader.getDouble(15),
            foreignBalance = reader.getDouble(16),
            foreignStock = reader.getDouble(17),
            uncollectedAmount = reader.getLong(18),
            repaidAmount = reader.getLong(19),
            currencyCode = reader.getString(20),
            relativeAgency = reader.getString(21),
            relativeClientName = reader.getString(22),
            relativeAccountNumber = reader.getString(23),
            recipientDisplay = reader.getString(24),
            myAccountDisplay = reader.getString(25),
        )
    }
}

class TransactionRawViewModelFactory(
    private val repository: TransactionRawRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionRawViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionRawViewModel(repository, excelReader) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
