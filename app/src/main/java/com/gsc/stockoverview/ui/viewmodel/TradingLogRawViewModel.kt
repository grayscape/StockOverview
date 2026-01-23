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

/**
 * 국내 주식 매매 일지(Raw Data)를 관리하는 ViewModel.
 * 엑셀 파일로부터 데이터를 가져와 DB에 저장하는 역할을 수행합니다.
 *
 * @property repository 원본 매매 일지 데이터에 접근하기 위한 Repository
 * @property excelReader 엑셀 파일을 읽기 위한 유틸리티 클래스
 */
class TradingLogRawViewModel(
    private val repository: TradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    /**
     * 전체 원본 매매 일지 목록을 Flow 형태로 제공하여 UI에서 실시간 관찰 가능하도록 함
     */
    val tradingLogRawList: Flow<List<TradingLogRawEntity>> = repository.allTradingLogRawList

    /**
     * 선택한 엑셀 파일에서 '매매일지' 시트를 읽어와 DB에 저장함.
     * 종목 정보 보완 로직은 StockViewModel에서 별도로 처리합니다.
     *
     * @param uri 엑셀 파일의 URI
     * @param onResult 처리 완료 후 읽어온 데이터 목록을 반환하는 콜백
     */
    fun importTradingLogRawList(uri: Uri, onResult: (List<TradingLogRawEntity>) -> Unit) {
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
                }
                // 처리 완료 알림 (수집된 데이터를 반환하여 필요 시 후속 처리 유도)
                onResult(data)
            }
        }
    }

    /**
     * ExcelReader의 행 데이터를 TradingLogRawEntity 객체로 변환
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
 */
class TradingLogRawViewModelFactory(
    private val repository: TradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TradingLogRawViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TradingLogRawViewModel(repository, excelReader) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
