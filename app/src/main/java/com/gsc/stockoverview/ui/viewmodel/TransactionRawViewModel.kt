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

/**
 * 증권사 원본 거래 내역(Raw Data)의 수집 및 저장을 담당하는 ViewModel
 */
class TransactionRawViewModel(
    private val repository: TransactionRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    val transactionRawList: Flow<List<TransactionRawEntity>> = repository.allTransactionRawList

    /**
     * 엑셀에서 원본 데이터를 읽어 DB를 갱신함
     */
    fun importTransactionRawList(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val rawData = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "전체거래내역") { reader -> mapToEntity(reader) }
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

    private fun mapToEntity(reader: ExcelReader.RowReader): TransactionRawEntity {
        val rawDate = reader.getString(1)
        val rawStockName = reader.getString(5)
        return TransactionRawEntity(
            account = reader.getString(0),
            tradeDate = rawDate,
            transactionDate = rawDate,
            transactionNo = reader.getString(2),
            originalNo = reader.getString(3),
            type = reader.getString(4),
            transactionName = rawStockName,
            stockName = rawStockName,
            quantity = reader.getLong(6),
            price = reader.getDouble(7),
            amount = reader.getLong(8),
            depositWithdrawalAmount = reader.getLong(9),
            balance = reader.getLong(10),
            stockBalance = reader.getLong(11),
            fee = reader.getLong(12),
            tax = reader.getLong(13),
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
            myAccountDisplay = reader.getString(25)
        )
    }
}

class TransactionRawViewModelFactory(
    private val repository: TransactionRawRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TransactionRawViewModel(repository, excelReader) as T
    }
}