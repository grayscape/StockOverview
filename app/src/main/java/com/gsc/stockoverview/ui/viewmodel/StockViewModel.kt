package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.repository.StockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 종목 정보를 관리하는 ViewModel.
 * 외부 API를 통해 종목 정보를 조회하고 DB에 저장/관리하는 역할을 수행합니다.
 */
class StockViewModel(
    private val stockRepository: StockRepository
) : ViewModel() {

    private val stockApiService = NaverStockApiService()

    /**
     * 모든 종목 정보 리스트를 Flow 형태로 제공
     */
    val allStocks: Flow<List<StockEntity>> = stockRepository.allStocks

    /**
     * 종목명 리스트를 받아 DB에 없는 종목 정보를 API로 조회하여 저장함
     */
    fun ensureStocksExist(stockNames: List<String>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                stockNames.distinct().forEach { stockName ->
                    val existingStock = stockRepository.getStockByName(stockName)
                    if (existingStock == null) {
                        val stockInfo = stockApiService.fetchStockInfo(stockName)
                        if (stockInfo != null) {
                            stockRepository.insertStock(stockInfo)
                        }
                    }
                }
            }
        }
    }

    /**
     * 단일 종목 정보를 조회하여 저장 (필요 시)
     */
    suspend fun fetchAndSaveStock(stockName: String) {
        val stockInfo = stockApiService.fetchStockInfo(stockName)
        if (stockInfo != null) {
            stockRepository.insertStock(stockInfo)
        }
    }
}

/**
 * StockViewModel 인스턴스 생성을 위한 Factory
 */
class StockViewModelFactory(
    private val stockRepository: StockRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockViewModel(stockRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
