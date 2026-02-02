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

    init {
        // 기본 종목(금현물 등) 등록 확인
        viewModelScope.launch {
            stockRepository.ensureDefaultStocks()
        }
    }

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
                            // 하드코딩된 약어명 매핑 적용
                            val finalStockInfo = SHORT_NAME_MAP[stockName]?.let { shortName ->
                                stockInfo.copy(stockShortName = shortName)
                            } ?: stockInfo
                            stockRepository.insertStock(finalStockInfo)
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
                            // 하드코딩된 약어명 매핑 적용
                            val finalStockInfo = SHORT_NAME_MAP[stockName]?.let { shortName ->
                                stockInfo.copy(stockShortName = shortName)
                            } ?: stockInfo
                            stockRepository.insertStock(finalStockInfo)
                        }
                    }
                }
            }
            onComplete()
        }
    }

    companion object {
        private val SHORT_NAME_MAP = mapOf(
            "슈어소프트테크" to "슈어",
            "KIWOOM 200TR" to "키움",
            "에코프로" to "에코",
            "ACE KRX금현물" to "KRX금",
            "TIGER CD금리투자KIS(합성)" to "T-CD",
            "ACE 미국달러SOFR금리(합성)" to "SOFR",
            "TIGER 미국채10년선물" to "미10",
            "ACE 국고채10년" to "국10",
            "TIGER 코리아휴머노이드로봇산업" to "T-로봇",
            "1Q 미국우주항공테크" to "미국우주",
            "KODEX 미국S&P500" to "S&P500",
            "KODEX 차이나CSI300" to "차이나",
            "퀀텀사이" to "퀀텀",
            "아이온큐" to "아이온큐",
            "유나이티드헬스 그룹" to "유나이티드",
            "테슬라" to "테슬라",
            "팔란티어 테크" to "팔란티어"
        )
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
