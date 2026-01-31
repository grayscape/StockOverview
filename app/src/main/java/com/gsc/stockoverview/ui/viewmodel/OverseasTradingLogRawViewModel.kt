package com.gsc.stockoverview.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import com.gsc.stockoverview.data.repository.OverseasTradingLogRawRepository
import com.gsc.stockoverview.utils.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverseasTradingLogRawViewModel(
    private val repository: OverseasTradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    val overseasTradingLogRawList: Flow<List<OverseasTradingLogRawEntity>> = repository.allOverseasTradingLogRawList

    fun importOverseasTradingLogRawList(uri: Uri, onResult: (List<OverseasTradingLogRawEntity>) -> Unit) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "해외매매일지") { reader -> mapRowToEntity(reader) }
            }
            if (data.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.deleteAll()
                    repository.insertAll(data)
                }
                onResult(data)
            }
        }
    }

    /**
     * 외부에서 호출 가능하도록 public으로 공개
     */
    fun mapRowToEntity(reader: ExcelReader.RowReader): OverseasTradingLogRawEntity {
        return OverseasTradingLogRawEntity(
            account = reader.getString(0),
            tradeDate = reader.getString(1),
            currency = reader.getString(2),
            stockNumber = reader.getString(3),
            stockName = reader.getString(4),
            balanceQuantity = reader.getLong(5),
            buyAverageExchangeRate = reader.getDouble(6),
            tradingExchangeRate = reader.getDouble(7),
            buyQuantity = reader.getLong(8),
            buyPrice = reader.getDouble(9),
            buyAmount = reader.getDouble(10),
            wonBuyAmount = reader.getLong(11),
            sellQuantity = reader.getLong(12),
            sellPrice = reader.getDouble(13),
            sellAmount = reader.getDouble(14),
            wonSellAmount = reader.getLong(15),
            fee = reader.getDouble(16),
            tax = reader.getDouble(17),
            wonTotalCost = reader.getLong(18),
            originalBuyAveragePrice = reader.getDouble(19),
            tradingProfit = reader.getDouble(20),
            wonTradingProfit = reader.getLong(21),
            exchangeProfit = reader.getDouble(22),
            totalEvaluationProfit = reader.getDouble(23),
            yield = reader.getDouble(24),
            convertedYield = reader.getDouble(25)
        )
    }
}

class OverseasTradingLogRawViewModelFactory(
    private val repository: OverseasTradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OverseasTradingLogRawViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OverseasTradingLogRawViewModel(repository, excelReader) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
