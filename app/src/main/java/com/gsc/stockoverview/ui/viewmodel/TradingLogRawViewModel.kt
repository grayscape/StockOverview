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

/**
 * 국내 주식 매매 일지(Raw Data)를 관리하는 ViewModel.
 * 엑셀 파일로부터 데이터를 가져오고, 누락된 종목 정보를 API를 통해 보완하여 저장합니다.
 *
 * @property repository 원본 매매 일지 데이터에 접근하기 위한 Repository
 * @property stockRepository 종목 정보(마스터 데이터) 관리를 위한 Repository
 * @property excelReader 엑셀 파일을 읽기 위한 유틸리티 클래스
 */
class TradingLogRawViewModel(
    private val repository: TradingLogRawRepository,
    private val stockRepository: StockRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    // 외부 API 호출을 위한 서비스 (종목 코드/정보 조회용)
    private val stockApiService = StockApiService()
    
    /**
     * 전체 원본 매매 일지 목록을 Flow 형태로 제공하여 UI에서 실시간 관찰 가능하도록 함
     */
    val tradingLogRawList: Flow<List<TradingLogRawEntity>> = repository.allTradingLogRawList

    /**
     * 선택한 엑셀 파일에서 '매매일지' 시트를 읽어와 DB에 저장하고, 
     * 데이터에 포함된 종목 중 DB에 없는 종목은 API를 통해 정보를 조회하여 등록함
     *
     * @param uri 엑셀 파일의 URI
     * @param onResult 처리 완료 후 읽어온 데이터의 총 개수를 반환하는 콜백
     */
    fun importTradingLogRawList(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            // 1. 엑셀 파일 읽기 (백그라운드 스레드 실행)
            val data = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "매매일지") { reader ->
                    mapToEntity(reader)
                }
            }
            
            if (data.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    // 2. 기존 원본 데이터를 모두 삭제 후 새로운 데이터로 교체 (일관성 유지)
                    repository.deleteAll()
                    repository.insertAll(data)
                    
                    // 3. 종목 마스터 데이터 관리: 중복되지 않는 종목명 리스트 추출
                    data.map { it.stockName }.distinct().forEach { stockName ->
                        // 3-1. 이미 등록된 종목인지 확인
                        val existingStock = stockRepository.getStockByName(stockName)
                        if (existingStock == null) {
                            // 3-2. 등록되지 않은 종목인 경우 외부 API에서 종목 정보(코드 등) 검색
                            val stockInfo = stockApiService.fetchStockInfo(stockName)
                            if (stockInfo != null) {
                                // 3-3. 검색된 정보가 있으면 종목 테이블에 저장
                                stockRepository.insertStock(stockInfo)
                            }
                        }
                    }
                }
                // 처리 완료 알림
                onResult(data.size)
            }
        }
    }

    /**
     * ExcelReader의 행 데이터를 TradingLogRawEntity 객체로 변환
     * 엑셀 컬럼 순서: 계좌, 매매일자, 종목명, 매수수량, 매수단가, 매수금액, 매도수량, 매도단가, 매도금액, 제비용, 실현손익, 수익률
     */
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

/**
 * ViewModel 인스턴스 생성을 위한 Factory 클래스
 * Repository와 유틸리티 객체들을 주입하여 ViewModel을 생성함
 */
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
