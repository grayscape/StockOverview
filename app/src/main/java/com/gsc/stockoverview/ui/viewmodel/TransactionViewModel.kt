package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import com.gsc.stockoverview.data.repository.OverseasTradingLogRawRepository
import com.gsc.stockoverview.data.repository.StockRepository
import com.gsc.stockoverview.data.repository.TradingLogRawRepository
import com.gsc.stockoverview.data.repository.TransactionRawRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.temporal.TemporalAmount
import java.util.regex.Pattern
import kotlin.math.abs

class TransactionViewModel(
    private val repository: TransactionRepository,
    private val transactionRawRepository: TransactionRawRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository,
    private val stockRepository: StockRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow("전체")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactionList: Flow<List<TransactionEntity>> = _selectedTab.flatMapLatest { tab ->
        if (tab == "전체") {
            repository.allTransactions
        } else {
            repository.getTransactionsByAccount(tab)
        }
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun syncFromRawData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val rawData = transactionRawRepository.allTransactionRawList.first()
            if (rawData.isEmpty()) return@launch

            withContext(Dispatchers.IO) {
                val context = loadCorrectionContext()

                val correctedData = rawData
                    .sortedWith(
                        compareBy<TransactionRawEntity> { it.account }
                            .thenBy { it.transactionDate }
                            .thenBy { it.transactionNo }
                    )

                val transactions = correctedData
                    .filter { TYPE_MAPPING.containsKey(it.type) }
                    .map { entity -> entity.toTransactionEntity(correctedData, context) }
                    .sortedWith(
                        compareBy<TransactionEntity> { it.tradeDate }
                            .thenBy { it.transactionOrder }
                    )

                repository.deleteAll()
                repository.insertAll(transactions)
            }
            onComplete()
        }
    }

    private suspend fun loadCorrectionContext(): CorrectionContext {
        val tradingLogs = tradingLogRawRepository.allTradingLogRawList.first()
        val overseasLogs = overseasTradingLogRawRepository.allOverseasTradingLogRawList.first()

        // StockRepository (DB)에서 모든 종목 정보 로드
        val allStocks = stockRepository.allStocks.first()
        
        // 종목명 -> 종목코드 맵핑 생성 (종목 테이블 기준)
        val stockCodeMap = allStocks
            .filter { it.stockName.isNotBlank() }
            .associateBy({ it.stockName }, { it.stockCode })

        // 보정용 이름 풀 (종목 테이블 기준)
        val namePool = allStocks.map { it.stockName }
            .filter { it.isNotBlank() }
            .distinct()
            
        return CorrectionContext(tradingLogs, overseasLogs, namePool, stockCodeMap)
    }

    private fun TransactionRawEntity.toTransactionEntity(
        allData: List<TransactionRawEntity>,
        context: CorrectionContext
    ): TransactionEntity {
        //주식매도출고, 주식매수입고, 해외주식매도출고, 해외주식매수입고, 금현물매수입고, ETF/상장클래스 분배금입금 의 거래명을 정확한 종목명을 찾아서 적용
        var finalTransactionName = if (type in STOCK_CORRECTION_TYPES || type == "ETF/상장클래스 분배금입금") {
            findCorrectStockName(transactionName, context.namePool) ?: transactionName
        } else {
            transactionName
        }

        //주식매도출고, 주식매수입고, 해외주식매도출고, 해외주식매수입고, 금현물매수입고는 매매 2틀 후의 거래일이라 실제 매매일자로 찾아서 적용
        val finalTransactionDate = if (type in STOCK_CORRECTION_TYPES) {
            findCorrectDate(account, finalTransactionName, transactionDate, context) ?: transactionDate
        } else {
            transactionDate
        }

        // 해외주식매수입고, 해외주식매도출고 외화거래금액 항목을 거래금액으로 사용
        var finalAmount = if (type == "해외주식매수입고" || type == "해외주식매도출고") {
            foreignAmount
        }
        // 외화예탁금이용료입금, 배당금외화입금은 "외하거래금-제세금=외화입금출금" 임으로 외화입금출금 항목을 거래금액으로 사용
        else if (type == "외화예탁금이용료입금" || type == "배당금외화입금") {
            foreignDWAmount
        } else {
            amount
        }

        var finalPrice = if (type == "외화매수원화출금" || type == "외화매수원화출금(미수)" || type == "외화매도원화입금") {
            // 매매일 평균 이틀 후 거래일의 실제 환율 값(매수,매도 단가)을 구한다.
            computeEffectiveFxRate(allData, account, transactionDate, amount)
        } else if (type == "배당금외화입금") {
            // 배당금외화입금 단가의 경우 환율 금액으로 보이며 그냥 0으로 등록
            0.0
        } else {
            price
        }

        var finalVolume = quantity.toDouble()

        if (type == "외화매도원화입금") {
            val related = allData.filter { it.account == account && it.transactionDate == transactionDate }
            val fxInRow = related.find { it.type == "외화매도외화출금" }

            if(fxInRow != null) {
                finalVolume = fxInRow.foreignAmount
                finalTransactionName = fxInRow.transactionName
            }
        }

        return TransactionEntity(
            account = account,
            tradeDate = finalTransactionDate,
            type = TYPE_MAPPING[type] ?: type,
            typeDetail = type,
            stockCode = context.stockCodeMap[finalTransactionName] ?: "",
            transactionName = finalTransactionName,
            price = finalPrice,
            volume = finalVolume,
            fee = fee,
            tax = tax,
            amount = finalAmount,
            profitLoss = 0.0,
            yield = 0.0,
            currencyCode = if (currencyCode.isBlank()) "KRW" else currencyCode,
            transactionOrder = transactionNo
        )
    }

    //해외주식은 매매일의 환율이 아니라 2틀후의 실제 거래일 환율이 맞는 금액이라 실제 거래일 환율 값을 구한다.
    private fun computeEffectiveFxRate(allData: List<TransactionRawEntity>, account: String, date: String, amount: Double): Double {
        //TransactionRawEntity의 전체 데이터에서 외화매수, 외화매도 날짜에 거래 된 데이터들만 추출
        val related = allData.filter { it.account == account && it.transactionDate == date }
        //매수된 외화거래금액 또는 매도한 외화거래금액을 추출 
        val foreignAmount = related.find { it.type == "외화매수외화입금" || it.type == "외화매도외화출금" }?.foreignAmount?:0.0
        //매매일 환율과 실제 거래일 환율의 차액 만큼 한화로 출금 또는 입금 되는 선환전차액출금 또는 선환전차액입금 값을 추출
        val fxDifferenceAmount = related.find { it.type.startsWith("선환전차액") }?.amount?:0.0
        //한화로 거래된 거래금액과 차액을 더한다.
        var sumAmount = amount + fxDifferenceAmount
        //합산한 최종 거래금액을 외화거래금액으로 나누면 정확한 환율이 나온다.
        var exchangeRate = sumAmount / foreignAmount

        return exchangeRate
    }

    private fun findCorrectDate(account: String, stockName: String, settlementDate: String, context: CorrectionContext): String? {
        val tDate = context.tradingLogs
            .filter { it.account == account && it.stockName == stockName && it.tradeDate <= settlementDate }
            .maxByOrNull { it.tradeDate }?.tradeDate
        if (tDate != null) return tDate
        return context.overseasLogs
            .filter { it.account == account && it.stockName == stockName && it.tradeDate <= settlementDate }
            .maxByOrNull { it.tradeDate }?.tradeDate
    }

    private fun findCorrectStockName(target: String, pool: List<String>): String? {
        // 1. 정확히 일치하는 경우
        pool.find { it == target }?.let { return it }

        // 2. target에서 의미있는 토큰들을 추출
        val targetTokens = extractTokens(target)

        // 3. 각 pool 항목에 대해 매칭 스코어 계산
        val scored = pool.map { candidate ->
            val candidateTokens = extractTokens(candidate)
            val score = calculateMatchScore(targetTokens, candidateTokens)
            candidate to score
        }

        // 4. 가장 높은 스코어를 가진 항목 반환 (임계값 이상인 경우만)
        val bestMatch = scored.maxByOrNull { it.second }
        return if (bestMatch != null && bestMatch.second > 0.3) {
            bestMatch.first
        } else {
            null
        }
    }

    private fun extractTokens(text: String): Set<String> {
        val tokens = mutableSetOf<String>()

        // 영문 토큰 추출 (대소문자 구분 없이)
        val englishPattern = Regex("[A-Za-z]+")
        tokens.addAll(englishPattern.findAll(text).map { it.value.uppercase() })

        // 숫자 토큰 추출
        val numberPattern = Regex("\\d+")
        tokens.addAll(numberPattern.findAll(text).map { it.value })

        // 한글 토큰 추출 (2글자 이상)
        val koreanPattern = Regex("[가-힣]{2,}")
        tokens.addAll(koreanPattern.findAll(text).map { it.value })

        return tokens
    }

    private fun calculateMatchScore(targetTokens: Set<String>, candidateTokens: Set<String>): Double {
        if (targetTokens.isEmpty()) return 0.0

        // 교집합 크기 기반 스코어
        val intersection = targetTokens.intersect(candidateTokens)
        val baseScore = intersection.size.toDouble() / targetTokens.size

        // 부분 문자열 매칭 보너스
        var bonusScore = 0.0
        for (targetToken in targetTokens) {
            for (candidateToken in candidateTokens) {
                if (targetToken.length >= 3 && candidateToken.contains(targetToken)) {
                    bonusScore += 0.1
                } else if (candidateToken.length >= 3 && targetToken.contains(candidateToken)) {
                    bonusScore += 0.1
                }
            }
        }

        return (baseScore + bonusScore).coerceAtMost(1.0)
    }

    private data class CorrectionContext(
        val tradingLogs: List<TradingLogRawEntity>,
        val overseasLogs: List<OverseasTradingLogRawEntity>,
        val namePool: List<String>,
        val stockCodeMap: Map<String, String>
    )

    companion object {
        private val TOKEN_PATTERN = Pattern.compile("[가-힣]+|[a-zA-Z]+|[0-9]+")
        private val TYPE_MAPPING = mapOf(
            "외화예탁금이용료입금" to "이자", "예탁금이용료입금" to "이자", "해외주식매수입고" to "매수",
            "ETF/상장클래스 분배금입금" to "이자", "계좌대체출금" to "출금", "주식매도출고" to "매도",
            "주식매수입고" to "매수", "배당금외화입금" to "이자", "배당세출금" to "세금", "해외매수원화출금" to "매수", "해외매수원화출금(미수)" to "매수",
            "이체입금" to "입금", "해외주식매도출고" to "매도", "계좌대체입금" to "입금", "외화매도원화입금" to "매도", "외화매도원화입금(미수)" to "매도",
            "금현물매수입고" to "매수", "금현물보관수수료세금" to "세금", "금현물보관수수료" to "수수료", "이체송금" to "출금"
        )
        private val STOCK_CORRECTION_TYPES = listOf(
            "주식매도출고", "주식매수입고", "해외주식매도출고", "해외주식매수입고", "금현물매수입고"
        )
    }
}

class TransactionViewModelFactory(
    private val repository: TransactionRepository,
    private val transactionRawRepository: TransactionRawRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository,
    private val stockRepository: StockRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository, transactionRawRepository, tradingLogRawRepository, overseasTradingLogRawRepository, stockRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
