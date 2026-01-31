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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.math.abs

class TransactionViewModel(
    private val repository: TransactionRepository,
    private val transactionRawRepository: TransactionRawRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository,
    private val stockRepository: StockRepository
) : ViewModel() {

    val transactionList: Flow<List<TransactionEntity>> = repository.allTransactions

    fun syncFromRawData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val rawData = transactionRawRepository.allTransactionRawList.first()
            if (rawData.isEmpty()) return@launch

            withContext(Dispatchers.IO) {
                val context = loadCorrectionContext()

                val correctedData = rawData
                    .map { it.correct(context) }
                    .sortedWith(
                        compareBy<TransactionRawEntity> { it.account }
                            .thenBy { it.transactionDate }
                            .thenBy { it.transactionNo.toLongOrNull() ?: 0L }
                    )

                val transactions = correctedData
                    .filter { TYPE_MAPPING.containsKey(it.type) }
                    .map { it.toTransactionEntity(correctedData, context) }

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

    private fun TransactionRawEntity.correct(context: CorrectionContext): TransactionRawEntity {
        val correctedName = findCorrectStockName(transactionName, context.namePool) ?: transactionName
        val correctedDate = findCorrectDate(account, correctedName, transactionDate, context) ?: transactionDate
        return copy(transactionName = correctedName, transactionDate = correctedDate)
    }

    private fun TransactionRawEntity.toTransactionEntity(
        allData: List<TransactionRawEntity>,
        context: CorrectionContext
    ): TransactionEntity {
        //주식매도출고, 주식매수입고, 해외주식매도출고, 해외주식매수입고, 금현물매수입고, ETF/상장클래스 분배금입금 의 거래명을 정확한 종목명을 찾아서 적용
        val finalTransactionName = if (type in STOCK_CORRECTION_TYPES || type == "ETF/상장클래스 분배금입금") {
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

        var finalPrice = if (type == "외화매수원화출금" || type == "외화매수원화출금(미수)") {
            // 매매일 평균 이틀 후 거래일의 실제 환율 값을 구한다.
            computeEffectiveFxRate(allData, account, transactionDate, amount)
        } else if (type == "배당금외화입금") {
            // 배당금외화입금 단가의 경우 환율 금액으로 보이며 그냥 0으로 등록
            0.0
        } else {
            price
        }

        return TransactionEntity(
            account = account,
            tradeDate = finalTransactionDate,
            type = TYPE_MAPPING[type] ?: type,
            typeDetail = type,
            stockCode = context.stockCodeMap[transactionName] ?: "",
            transactionName = finalTransactionName,
            price = finalPrice,
            quantity = quantity,
            fee = fee,
            tax = tax,
            amount = finalAmount,
            profitLoss = 0.0,
            yield = 0.0,
            currencyCode = if (currencyCode.isBlank()) "KRW" else currencyCode
        )
    }

    private fun computeEffectiveFxRate(allData: List<TransactionRawEntity>, account: String, date: String, foreignBuyAmount: Double): Double {
        val related = allData.filter { it.account == account && it.transactionDate == date }

        val foreignAmount = related.find { it.type == "외화매수외화입금" }?.foreignAmount?:0.0
        val fxDifferenceAmount = related.find { it.type.startsWith("선환전차액") }?.amount?:0.0
        var sumAmount = foreignBuyAmount + fxDifferenceAmount
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
        val normTarget = normalize(target)
        pool.find { normalize(it) == normTarget }?.let { return it }
        val targetTokens = tokenize(target)
        return pool.asSequence()
            .map { it to calculateOverlap(targetTokens, it) }
            .filter { it.second >= 0.6 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun calculateOverlap(targetTokens: List<String>, poolName: String): Double {
        val poolTokens = tokenize(poolName)
        if (poolTokens.isEmpty()) return 0.0
        val matches = poolTokens.count { p -> targetTokens.any { it.contains(p) || p.contains(it) } }
        return matches.toDouble() / poolTokens.size
    }

    private fun normalize(name: String) = name.replace(Regex("[^a-zA-Z0-9가-힣]"), "").uppercase()

    private fun tokenize(name: String): List<String> {
        val tokens = mutableListOf<String>()
        val matcher = TOKEN_PATTERN.matcher(name)
        while (matcher.find()) {
            matcher.group().uppercase().takeIf { it.isNotEmpty() }?.let { tokens.add(it) }
        }
        return tokens
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
            "이체입금" to "입금", "해외주식매도출고" to "매도", "계좌대체입금" to "입금", "외화매도외화출금" to "매도", "외화매도외화출금(미수)" to "매도",
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
