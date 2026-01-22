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

/**
 * 해외 주식 매매 일지(Raw Data)를 관리하는 ViewModel.
 * 엑셀 파일로부터 해외 거래 데이터를 가져와 데이터베이스에 저장하는 역할을 수행합니다.
 *
 * @property repository 해외 매매 일지 데이터에 접근하기 위한 Repository
 * @property excelReader 엑셀 파일을 읽기 위한 유틸리티 클래스
 */
class OverseasTradingLogRawViewModel(
    private val repository: OverseasTradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    /**
     * 전체 해외 매매 일지 목록을 Flow 형태로 제공하여 UI에서 실시간 관찰 가능하도록 함
     */
    val overseasTradingLogRawList: Flow<List<OverseasTradingLogRawEntity>> = repository.allOverseasTradingLogRawList

    /**
     * 선택한 엑셀 파일에서 '해외매매일지' 시트를 읽어와 DB에 저장함
     *
     * @param uri 엑셀 파일의 URI
     * @param onResult 처리 완료 후 읽어온 데이터의 총 개수를 반환하는 콜백
     */
    fun importOverseasTradingLogRawList(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            // 1. 엑셀 파일 읽기 (백그라운드 스레드 실행)
            val data = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "해외매매일지") { reader ->
                    mapToEntity(reader)
                }
            }
            if (data.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    // 2. 기존 데이터를 삭제하고 새로운 데이터로 교체 (덮어쓰기 방식)
                    repository.deleteAll()
                    repository.insertAll(data)
                }
                // 처리 완료 알림
                onResult(data.size)
            }
        }
    }

    /**
     * ExcelReader의 행 데이터를 OverseasTradingLogRawEntity 객체로 변환
     * 엑셀 컬럼 순서: 계좌, 매매일자, 통화, 종목번호, 종목명, 잔고수량, 매수평균환율, 매매환율, 매수수량, 매수단가, 매수금액, 
     * 원화매수금액, 매도수량, 매도단가, 매도금액, 원화매도금액, 제비용, 세금, 원화총비용, 원래매수평균가, 
     * 매매손익, 원화매매손익, 환차익, 총평가손익, 수익률, 환산수익률
     */
    private fun mapToEntity(reader: ExcelReader.RowReader): OverseasTradingLogRawEntity {
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

/**
 * ViewModel 인스턴스 생성을 위한 Factory 클래스
 * Repository와 유틸리티 객체들을 주입하여 ViewModel을 생성함
 */
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
