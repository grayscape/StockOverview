package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import com.gsc.stockoverview.data.repository.OverseasTradingLogRawRepository
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

/**
 * 증권사 거래 내역(Raw Data)을 정제하여 사용자용 표준 거래 내역으로 변환하고 관리하는 ViewModel.
 */
class TransactionViewModel(
    private val repository: TransactionRepository,
    private val transactionRawRepository: TransactionRawRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository
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
                    .sortedWith(compareBy({ it.transactionDate }, { it.transactionNo.toLongOrNull() ?: 0L }))

                val transactions = correctedData
                    .filter { TYPE_MAPPING.containsKey(it.type) }
                    .map { it.toTransactionEntity(correctedData) }

                repository.deleteAll()
                repository.insertAll(transactions)
            }
            onComplete()
        }
    }

    private suspend fun loadCorrectionContext(): CorrectionContext {
        val tradingLogs = tradingLogRawRepository.allTradingLogRawList.first()
        val overseasLogs = overseasTradingLogRawRepository.allOverseasTradingLogRawList.first()
        val namePool = (tradingLogs.map { it.stockName } + overseasLogs.map { it.stockName })
            .filter { it.isNotBlank() }
            .distinct()
        return CorrectionContext(tradingLogs, overseasLogs, namePool)
    }

    private fun TransactionRawEntity.correct(context: CorrectionContext): TransactionRawEntity {
        if (type !in STOCK_CORRECTION_TYPES) return this
        val correctedName = findCorrectStockName(transactionName, context.namePool) ?: transactionName
        val correctedDate = findCorrectDate(account, correctedName, transactionDate, context) ?: transactionDate
        return copy(stockName = correctedName, tradeDate = correctedDate)
    }

    private fun TransactionRawEntity.toTransactionEntity(allData: List<TransactionRawEntity>): TransactionEntity {
        val isOverseas = type.contains("해외") || type.contains("외화")
        var exchangeRate = 0.0

        // 기본 금액 설정 (해외 거래는 외화 금액 우선)
        var baseAmount = if (isOverseas && foreignAmount != 0.0) foreignAmount else amount.toDouble()
        var finalPrice = price

        if (isOverseas) {
            // 1. 임의 환율 확인 (동일 행에 원화/외화가 모두 있는 경우 - 통합증거금 등)
            if (amount != 0L && foreignAmount != 0.0) {
                val impliedRate = amount.toDouble() / abs(foreignAmount)
                if (impliedRate > 100.0) { // 엔화 환율(보통 100엔당 900원대) 등을 고려한 최소값
                    exchangeRate = impliedRate
                }
            }

            // 2. 행에 정보가 없으면 당일 환전 내역 추적
            if (exchangeRate == 0.0) {
                exchangeRate = computeEffectiveFxRate(allData, account, transactionDate, currencyCode)
            }

            // 3. 환율이 확인된 경우 원화로 최종 환산
            if (exchangeRate > 0.0) {
                finalPrice = price * exchangeRate
                baseAmount = foreignAmount * exchangeRate
            }
        }

        return TransactionEntity(
            account = account,
            tradeDate = tradeDate,
            type = TYPE_MAPPING[type] ?: type,
            typeDetail = type,
            stockName = stockName,
            price = finalPrice,
            quantity = quantity,
            fee = fee,
            tax = tax,
            amount = baseAmount,
            profitLoss = 0L,
            yield = 0.0,
            exchangeRate = exchangeRate,
            exchangeProfitLoss = 0L
        )
    }

    private fun computeEffectiveFxRate(allData: List<TransactionRawEntity>, account: String, date: String, currency: String): Double {
        val targetCurrency = currency.ifBlank { "USD" }.uppercase()
        // 해당 날짜/계좌의 모든 거래 로드
        val related = allData.filter { it.account == account && it.transactionDate == date }

        // 매수 시 환전 (원화 -> 외화)
        val wonSpent = related.filter { it.type == "외화매수원화출금" || it.type == "선환전차액출금" }.sumOf { it.amount } -
                related.filter { it.type == "선환전차액입금" }.sumOf { it.amount }
        val foreignBought = related.filter {
            it.type == "외화매수외화입금" && (it.currencyCode.isBlank() || it.currencyCode.equals(targetCurrency, ignoreCase = true))
        }.sumOf { it.foreignDWAmount }

        if (wonSpent > 0L && foreignBought > 0.0) {
            return wonSpent.toDouble() / foreignBought
        }

        // 매도 시 환전 (외화 -> 원화)
        val wonReceived = related.filter { it.type == "외화매도원화입금" }.sumOf { it.amount }
        val foreignWithdrawn = related.filter {
            it.type == "외화매도외화출금" && (it.currencyCode.isBlank() || it.currencyCode.equals(targetCurrency, ignoreCase = true))
        }.sumOf { abs(it.foreignDWAmount) }

        if (wonReceived > 0L && foreignWithdrawn > 0.0) {
            return wonReceived.toDouble() / foreignWithdrawn
        }

        return 0.0
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
        val namePool: List<String>
    )

    companion object {
        private val TOKEN_PATTERN = Pattern.compile("[가-힣]+|[a-zA-Z]+|[0-9]+")
        private val TYPE_MAPPING = mapOf(
            "외화예탁금이용료입금" to "이자", "예탁금이용료입금" to "이자", "해외주식매수입고" to "매수",
            "ETF/상장클래스 분배금입금" to "이자", "계좌대체출금" to "출금", "주식매도출고" to "매도",
            "주식매수입고" to "매수", "배당금외화입금" to "이자", "배당세출금" to "세금",
            "이체입금" to "입금", "해외주식매도출고" to "매도", "계좌대체입금" to "입금",
            "금현물매수입고" to "매수", "금현물보관수수료세금" to "세금", "금현물보관수수료" to "수수료", "이체송금" to "출금"
        )
        private val STOCK_CORRECTION_TYPES = listOf(
            "주식매도출고", "주식매수입고", "해외주식매도출고", "해외주식매수입고", "금현물매수입고", "ETF/상장클래스 분배금입금"
        )
    }
}

class TransactionViewModelFactory(
    private val repository: TransactionRepository,
    private val transactionRawRepository: TransactionRawRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository, transactionRawRepository, tradingLogRawRepository, overseasTradingLogRawRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}