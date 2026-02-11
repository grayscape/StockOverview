package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.OverseasTradingLogRawDao
import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.dao.TradingLogRawDao
import com.gsc.stockoverview.data.dao.TransactionDao
import com.gsc.stockoverview.data.dao.TransactionRawDao
import com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.entity.TradingLogRawEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import com.gsc.stockoverview.utils.round
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val transactionRawDao: TransactionRawDao,
    private val tradingLogRawDao: TradingLogRawDao,
    private val overseasTradingLogRawDao: OverseasTradingLogRawDao,
    private val stockDao: StockDao
) {
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()

    fun getTransactionsByAccount(account: String): Flow<List<TransactionEntity>> {
        return transactionDao.getTransactionsByAccount(account)
    }

    /**
     * Raw 데이터를 가공하여 TransactionEntity를 생성하고 DB에 저장합니다.
     * @return 저장된 거래내역 리스트와 모든 종목 정보 리스트를 반환합니다.
     */
    suspend fun syncFromRawData(): Pair<List<TransactionEntity>, List<StockEntity>> {
        val rawData = transactionRawDao.getAllTransactionRaw().first()
        if (rawData.isEmpty()) return emptyList<TransactionEntity>() to emptyList<StockEntity>()

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

        transactionDao.deleteAll()
        transactionDao.insertAll(transactions)
        return transactions to context.allStocks
    }

    private suspend fun loadCorrectionContext(): CorrectionContext {
        val tradingLogs = tradingLogRawDao.getAllTradingLogRawList().first()
        val overseasLogs = overseasTradingLogRawDao.getAllOverseasTradingLogRawList().first()
        val allStocks = stockDao.getAllStocks().first()
        val stockCodeMap = allStocks
            .filter { it.stockName.isNotBlank() }
            .associateBy({ it.stockName }, { it.stockCode })
        val namePool = allStocks.map { it.stockName }
            .filter { it.isNotBlank() }
            .distinct()

        return CorrectionContext(tradingLogs, overseasLogs, namePool, stockCodeMap, allStocks)
    }

    private fun TransactionRawEntity.toTransactionEntity(
        allData: List<TransactionRawEntity>,
        context: CorrectionContext
    ): TransactionEntity {
        val related = allData.filter { it.account == account && it.transactionDate == transactionDate }

        val finalType = if (type == "외화매수원화출금(미수)") {
            "외화매수원화출금"
        } else {
            type
        }

        val finalTransactionName = if (type in STOCK_CORRECTION_TYPES || type == "ETF/상장클래스 분배금입금") {
            findCorrectStockName(transactionName, context.namePool) ?: transactionName
        } else if (finalType == "외화매수원화출금") {
            (related.find { it.type == "외화매수외화입금" }?.transactionName ?: transactionName) + "매수"
        } else if (finalType == "외화매도원화입금") {
            (related.find { it.type == "외화매도외화출금" }?.transactionName ?: transactionName) + "매도"
        } else {
            transactionName
        }

        val finalTransactionDate = if (type in STOCK_CORRECTION_TYPES) {
            findCorrectDate(account, finalTransactionName, transactionDate, context) ?: transactionDate
        } else {
            transactionDate
        }

        val finalAmount = when (type) {
            "해외주식매수입고", "해외주식매도출고", "외화매수외화입금", "외화매도외화출금" -> foreignAmount
            "외화예탁금이용료입금", "배당금외화입금" -> foreignDWAmount
            else -> amount
        }

        val finalPrice = if (finalType == "외화매수원화출금" || finalType == "외화매도외화출금") {
            val argAmount = related.find { it.type == "외화매도원화입금" }?.amount ?: amount
            computeEffectiveFxRate(allData, account, transactionDate, argAmount).round(2)
        } else if (finalType in listOf("외화매수외화입금", "외화매도원화입금")) {
            0.0
        } else {
            price
        }

        var finalVolume = quantity.toDouble()
        if (finalType == "외화매도원화입금") {
            related.find { it.type == "외화매도외화출금" }?.let { finalVolume = it.foreignAmount }
        }

        var finalProfitLoss = 0.0
        var finalYield = 0.0

        if (type in STOCK_CORRECTION_TYPES) {
            val isKRW = currencyCode.isBlank() || currencyCode == "KRW"
            if (isKRW) {
                context.tradingLogs.find {
                    it.account == account && it.stockName == finalTransactionName && it.tradeDate == finalTransactionDate
                }?.let {
                    finalProfitLoss = it.profitLoss.toDouble()
                    finalYield = it.yield
                }
            } else {
                context.overseasLogs.find {
                    it.account == account && it.stockName == finalTransactionName && it.tradeDate == finalTransactionDate
                }?.let {
                    finalProfitLoss = it.tradingProfit
                    finalYield = it.yield
                }
            }
        }

        return TransactionEntity(
            account = account,
            tradeDate = finalTransactionDate,
            type = TYPE_MAPPING[finalType] ?: finalType,
            typeDetail = finalType,
            stockCode = context.stockCodeMap[finalTransactionName] ?: "",
            transactionName = finalTransactionName,
            price = finalPrice,
            volume = finalVolume,
            fee = fee,
            tax = tax,
            amount = finalAmount,
            profitLoss = finalProfitLoss,
            yield = finalYield,
            currencyCode = currencyCode.ifBlank { "KRW" },
            transactionOrder = transactionNo
        )
    }

    private fun computeEffectiveFxRate(allData: List<TransactionRawEntity>, account: String, date: String, amount: Double): Double {
        val related = allData.filter { it.account == account && it.transactionDate == date }
        val foreignAmount = related.find { it.type == "외화매수외화입금" || it.type == "외화매도외화출금" }?.foreignAmount ?: 0.0
        val fxDifferenceAmount = related.find { it.type.startsWith("선환전차액") }?.amount ?: 0.0
        if (foreignAmount == 0.0) return 0.0
        return (amount + fxDifferenceAmount) / foreignAmount
    }

    private fun findCorrectDate(account: String, stockName: String, settlementDate: String, context: CorrectionContext): String? {
        return context.tradingLogs
            .filter { it.account == account && it.stockName == stockName && it.tradeDate <= settlementDate }
            .maxByOrNull { it.tradeDate }?.tradeDate
            ?: context.overseasLogs
                .filter { it.account == account && it.stockName == stockName && it.tradeDate <= settlementDate }
                .maxByOrNull { it.tradeDate }?.tradeDate
    }

    private fun findCorrectStockName(target: String, pool: List<String>): String? {
        pool.find { it == target }?.let { return it }
        val targetTokens = extractTokens(target)
        val bestMatch = pool.map { candidate ->
            val candidateTokens = extractTokens(candidate)
            val score = calculateMatchScore(targetTokens, candidateTokens)
            candidate to score
        }.maxByOrNull { it.second }
        return if (bestMatch != null && bestMatch.second > 0.3) bestMatch.first else null
    }

    private fun extractTokens(text: String): Set<String> {
        val tokens = mutableSetOf<String>()
        tokens.addAll(Regex("[A-Za-z]+").findAll(text).map { it.value.uppercase() })
        tokens.addAll(Regex("\\d+").findAll(text).map { it.value })
        tokens.addAll(Regex("[가-힣]{2,}").findAll(text).map { it.value })
        return tokens
    }

    private fun calculateMatchScore(targetTokens: Set<String>, candidateTokens: Set<String>): Double {
        if (targetTokens.isEmpty()) return 0.0

        var totalScore = 0.0

        for (targetToken in targetTokens) {
            var maxTokenScore = 0.0
            for (candidateToken in candidateTokens) {
                val score = when {
                    // 1. 완전 일치
                    targetToken == candidateToken -> 1.0

                    // 2. 부분 일치 (길이 비율 반영)
                    targetToken.contains(candidateToken) || candidateToken.contains(targetToken) -> {
                        val shorter = minOf(targetToken.length, candidateToken.length).toDouble()
                        val longer = maxOf(targetToken.length, candidateToken.length).toDouble()
                        // 단순히 0.1을 더하는 게 아니라, 얼마나 겹치는지 비율을 계산 (최대 0.8까지)
                        (shorter / longer).coerceAtMost(0.8)
                    }
                    else -> 0.0
                }
                if (score > maxTokenScore) maxTokenScore = score
            }
            totalScore += maxTokenScore
        }

        return totalScore / targetTokens.size
    }

    private data class CorrectionContext(
        val tradingLogs: List<TradingLogRawEntity>,
        val overseasLogs: List<OverseasTradingLogRawEntity>,
        val namePool: List<String>,
        val stockCodeMap: Map<String, String>,
        val allStocks: List<StockEntity>
    )

    companion object {
        private val TYPE_MAPPING = mapOf(
            "외화예탁금이용료입금" to "이자", "예탁금이용료입금" to "이자", "해외주식매수입고" to "매수",
            "ETF/상장클래스 분배금입금" to "이자", "계좌대체출금" to "출금", "주식매도출고" to "매도", "주식매수입고" to "매수",
            "배당금외화입금" to "이자", "배당세출금" to "세금", "외화매수원화출금" to "매수", "외화매수원화출금(미수)" to "매수",
            "외화매수외화입금" to "입금", "이체입금" to "입금", "해외주식매도출고" to "매도", "계좌대체입금" to "입금",
            "외화매도외화출금" to "매도", "외화매도원화입금" to "입금", "금현물매수입고" to "매수",
            "금현물보관수수료세금" to "세금", "금현물보관수수료" to "수수료", "이체송금" to "출금"
        )
        private val STOCK_CORRECTION_TYPES = listOf(
            "주식매도출고", "주식매수입고", "해외주식매도출고", "해외주식매수입고", "금현물매수입고"
        )
    }
}
