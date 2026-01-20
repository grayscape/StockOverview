package com.gsc.stockoverview.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.api.StockApiService
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import com.gsc.stockoverview.data.repository.StockRepository
import com.gsc.stockoverview.data.repository.TradingLogRawRepository
import com.gsc.stockoverview.utils.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TradingLogRawViewModel(
    private val repository: TradingLogRawRepository,
    private val stockRepository: StockRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    private val stockApiService = StockApiService()
    val tradingLogRawList: Flow<List<TradingLogRawEntity>> = repository.allTradingLogRawList

    fun importTradingLogRawList(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "매매일지") { reader ->
                    mapToEntity(reader)
                }
            }
            if (data.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    repository.deleteAll()
                    repository.insertAll(data)
                    
                    // Register stock if not exists
                    data.map { it.stockName }.distinct().forEach { stockName ->
                        val existingStock = stockRepository.getStockByName(stockName)
                        if (existingStock == null) {
                            val stockInfo = stockApiService.fetchStockInfo(stockName)
                            if (stockInfo != null) {
                                stockRepository.insertStock(stockInfo)
                            }
                        }
                    }
                }
                onResult(data.size)
            }
        }
    }

    private fun mapToEntity(reader: ExcelReader.RowReader): TradingLogRawEntity {
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
    private val stockRepository: StockRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TradingLogRawViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TradingLogRawViewModel(repository, stockRepository, excelReader) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
