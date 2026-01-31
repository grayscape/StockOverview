package com.gsc.stockoverview.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import com.gsc.stockoverview.data.repository.TradingLogRawRepository
import com.gsc.stockoverview.utils.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TradingLogRawViewModel(
    private val repository: TradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    val tradingLogRawList: Flow<List<TradingLogRawEntity>> = repository.allTradingLogRawList

    fun importTradingLogRawList(uri: Uri, onResult: (List<TradingLogRawEntity>) -> Unit) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "매매일지") { reader -> mapRowToEntity(reader) }
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
     * 외부(Screen 등)에서 일괄 처리 시 호출할 수 있도록 public으로 공개
     */
    fun mapRowToEntity(reader: ExcelReader.RowReader): TradingLogRawEntity {
        return TradingLogRawEntity(
            account = reader.getString(0),
            tradeDate = reader.getString(1),
            stockName = reader.getString(2),
            buyQuantity = reader.getLong(3),
            buyPrice = reader.getDouble(4),
            buyAmount = reader.getLong(5),
            sellQuantity = reader.getLong(6),
            sellPrice = reader.getDouble(7),
            sellAmount = reader.getLong(8),
            tradeFee = reader.getLong(9),
            profitLoss = reader.getLong(10),
            yield = reader.getDouble(11)
        )
    }
}

class TradingLogRawViewModelFactory(
    private val repository: TradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TradingLogRawViewModel(repository, excelReader) as T
    }
}
