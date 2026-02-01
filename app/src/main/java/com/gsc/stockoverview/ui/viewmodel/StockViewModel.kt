package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
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

    private val naverStockApiService = NaverStockApiService()
    private val yahooStockApiService = YahooStockApiService()

    /**
     * 모든 종목 정보 리스트를 Flow 형태로 제공
     */
    val allStocks: Flow<List<StockEntity>> = stockRepository.allStocks

    /**
     * 국내 종목명 리스트를 받아 DB에 없는 경우 네이버 API로 조회하여 저장함
     * (매매일지 수입 시 호출)
     */
    fun ensureStocksExist(stockNames: List<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                stockNames.distinct().forEach { stockName ->
                    val existingStock = stockRepository.getStockByName(stockName)
                    if (existingStock == null) {
                        val stockInfo = naverStockApiService.fetchDomesticStockInfo(stockName)
                        if (stockInfo != null) {
                            stockRepository.insertStock(stockInfo)
                        }
                    }
                }
            }
            onComplete()
        }
    }

    /**
     * 해외 종목 정보를 코드로 조회하여 저장함
     * (해외매매일지 수입 시 호출)
     */
    fun ensureOverseasStocksExist(overseasInfos: List<Triple<String, String, String>>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                overseasInfos.distinct().forEach { (stockCode, stockName, currency) ->
                    val existingStock = stockRepository.getStockByCode(stockCode)
                    if (existingStock == null) {
                        // 해외 주식은 엑셀에 이미 종목코드와 종목명이 있으므로 이를 사용
                        val stockInfo = yahooStockApiService.fetchOverseasStockDetails(
                            stockCode = stockCode,
                            stockName = stockName,
                            currency = currency
                        )
                        if (stockInfo != null) {
                            stockRepository.insertStock(stockInfo)
                        }
                    }
                }
            }
            onComplete()
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
