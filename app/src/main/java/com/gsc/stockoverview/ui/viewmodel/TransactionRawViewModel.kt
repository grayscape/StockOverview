package com.gsc.stockoverview.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.entity.TransactionRawEntity
import com.gsc.stockoverview.data.repository.OverseasTradingLogRawRepository
import com.gsc.stockoverview.data.repository.TradingLogRawRepository
import com.gsc.stockoverview.data.repository.TransactionRawRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import com.gsc.stockoverview.utils.ExcelReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionRawViewModel(
    private val repository: TransactionRawRepository,
    private val transactionRepository: TransactionRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModel() {

    val transactionRawList: Flow<List<TransactionRawEntity>> = repository.allTransactionRawList

    private val typeMapping = mapOf(
        "외화예탁금이용료입금" to "이자",
        "예탁금이용료입금" to "이자",
        "해외주식매수입고" to "매수",
        "ETF/상장클래스 분배금입금" to "이자",
        "계좌대체출금" to "출금",
        "주식매도출고" to "매도",
        "주식매수입고" to "매수",
        "배당금외화입금" to "이자",
        "배당세출금" to "세금",
        "이체입금" to "입금",
        "해외주식매도출고" to "매도",
        "계좌대체입금" to "입금",
        "금현물매수입고" to "매수",
        "금현물보관수수료세금" to "세금",
        "금현물보관수수료" to "수수료",
        "이체송금" to "출금"
    )

    private val stockCorrectionTypes = listOf(
        "주식매도출고", "주식매수입고", "해외주식매도출고", "해외주식매수입고", "금현물매수입고", "ETF/상장클래스 분배금입금"
    )

    fun importTransactionRawList(uri: Uri, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val rawData = withContext(Dispatchers.IO) {
                excelReader.readExcelSheet(uri, "전체거래내역") { reader ->
                    mapToEntity(reader)
                }
            }
            if (rawData.isNotEmpty()) {
                val tradingLogs = tradingLogRawRepository.allTradingLogRawList.first()
                val overseasTradingLogs = overseasTradingLogRawRepository.allOverseasTradingLogRawList.first()
                
                val correctNamesPool = (tradingLogs.map { it.stockName } + overseasTradingLogs.map { it.stockName })
                    .filter { it.isNotBlank() }
                    .distinct()

                val correctedRawData = rawData.map { raw ->
                    // 1. 종목명 교정
                    val correctName = if (raw.type in stockCorrectionTypes) {
                        findCorrectStockName(raw.transactionName, correctNamesPool) ?: raw.transactionName
                    } else {
                        raw.transactionName
                    }

                    // 2. 일자 교정 (결제일 -> 실제 매매일)
                    val correctDate = if (raw.type in stockCorrectionTypes) {
                        findCorrectDate(raw.account, correctName, raw.transactionDate, tradingLogs, overseasTradingLogs) ?: raw.transactionDate
                    } else {
                        raw.transactionDate
                    }

                    raw.copy(stockName = correctName, tradeDate = correctDate)
                }

                repository.deleteAll()
                repository.insertAll(correctedRawData)

                // importTransactionRawList 내부, correctedRawData 만든 직후에
                // 정렬된 raw를 만들어 "환전 직전 잔고"를 안정적으로 찾을 수 있게 함
                val sortedRaw = correctedRawData.sortedWith(
                    compareBy<TransactionRawEntity>({ it.transactionDate }, { it.transactionNo.toLongOrNull() ?: 0L })
                )

                val filteredTransactions = correctedRawData
                    .filter { it.type in typeMapping.keys }
                    .map { raw ->
                        val isOverseas = raw.type.contains("해외") || raw.type.contains("외화")
                        var exchangeRate = 0.0
                        var finalAmount = if (isOverseas && raw.foreignAmount != 0.0) raw.foreignAmount else raw.amount.toDouble()
                        var finalPrice = raw.price

                        // 해외주식(매수/매도/배당 등) 중 "USD 환율이 필요한 케이스"를 넓게 잡고,
                        // 특히 매수입고에 대해 확정 적용
                        if (raw.type == "해외주식매수입고") {
                            val currency = (raw.currencyCode.ifBlank { "USD" }).uppercase()
                            val rate = computeEffectiveFxRateForDate(
                                sortedRaw = sortedRaw,
                                account = raw.account,
                                date = raw.transactionDate,
                                currency = currency
                            )

                            if (rate > 0.0) {
                                exchangeRate = rate
                                // raw.price: USD 단가 -> KRW 단가
                                finalPrice = raw.price * exchangeRate
                                // raw.foreignAmount: USD 거래금액(대개 단가*수량) -> KRW 거래금액
                                finalAmount = raw.foreignAmount * exchangeRate
                            }
                        }

                        TransactionEntity(
                            account = raw.account,
                            tradeDate = raw.tradeDate,
                            type = typeMapping[raw.type] ?: raw.type,
                            typeDetail = raw.type,
                            stockName = raw.stockName,
                            price = finalPrice,
                            quantity = raw.quantity,
                            fee = raw.fee,
                            tax = raw.tax,
                            amount = finalAmount,
                            profitLoss = 0L,
                            yield = 0.0,
                            exchangeRate = exchangeRate,
                            exchangeProfitLoss = 0L
                        )
                    }
                
                if (filteredTransactions.isNotEmpty()) {
                    transactionRepository.deleteAll()
                    transactionRepository.insertAll(filteredTransactions)
                }

                onResult(rawData.size)
            }
        }
    }

    private fun computeEffectiveFxRateForDate(
        sortedRaw: List<TransactionRawEntity>,
        account: String,
        date: String,
        currency: String
    ): Double {
        // 1) 같은 날짜/계좌의 관련 환전 내역만 취합
        val related = sortedRaw.filter {
            it.account == account &&
                    it.transactionDate == date &&
                    it.currencyCode.equals(currency, ignoreCase = true)
        }

        // 원화 지출 = 외화매수원화출금 + 선환전차액출금 - 선환전차액입금
        val wonWithdrawal = related
            .filter { it.type == "외화매수원화출금" }
            .sumOf { it.amount }

        val diffWithdrawal = related
            .filter { it.type == "선환전차액출금" }
            .sumOf { it.amount }

        val diffDeposit = related
            .filter { it.type == "선환전차액입금" }
            .sumOf { it.amount }

        val totalWonSpent = (wonWithdrawal + diffWithdrawal - diffDeposit).toDouble()
        if (totalWonSpent <= 0.0) return 0.0

        // 외화 입금(USD) = 외화매수외화입금의 외화입출금액 합(foreignDWAmount)
        val foreignDeposit = related
            .filter { it.type == "외화매수외화입금" }
            .sumOf { it.foreignDWAmount }

        if (foreignDeposit <= 0.0) return 0.0

        // 2) 환전 직전(같은 계좌/통화) 외화예수금 잔고를 "이전 거래"에서 찾아오기
        val startingForeignBalance = findPreviousForeignBalance(
            sortedRaw = sortedRaw,
            account = account,
            date = date,
            currency = currency
        )

        // 3) 너가 원한 방식: (환전으로 산 달러)에서 (기존 외화예수금) 차감
        val netBoughtUsd = foreignDeposit - startingForeignBalance
        if (netBoughtUsd <= 0.0) return 0.0

        return totalWonSpent / netBoughtUsd
    }

    private fun findPreviousForeignBalance(
        sortedRaw: List<TransactionRawEntity>,
        account: String,
        date: String,
        currency: String
    ): Double {
        // 해당 date 이전의 마지막 외화잔고(foreignBalance)를 가져온다.
        // transactionNo 정렬까지 되어 있으므로 "마지막 거래"가 안정적.
        val prev = sortedRaw.asSequence()
            .filter { it.account == account }
            .filter { it.currencyCode.equals(currency, ignoreCase = true) }
            .filter { it.transactionDate < date }
            .lastOrNull { it.foreignBalance != 0.0 }

        return prev?.foreignBalance ?: 0.0
    }

    private fun findCorrectDate(
        account: String,
        stockName: String,
        settlementDate: String,
        tradingLogs: List<com.gsc.stockoverview.data.entity.TradingLogRawEntity>,
        overseasTradingLogs: List<com.gsc.stockoverview.data.entity.OverseasTradingLogRawEntity>
    ): String? {
        val domesticDate = tradingLogs
            .filter { it.account == account && it.stockName == stockName && it.tradeDate <= settlementDate }
            .maxByOrNull { it.tradeDate }?.tradeDate

        if (domesticDate != null) return domesticDate

        val overseasDate = overseasTradingLogs
            .filter { it.account == account && it.stockName == stockName && it.tradeDate <= settlementDate }
            .maxByOrNull { it.tradeDate }?.tradeDate

        return overseasDate
    }

    private fun findCorrectStockName(targetName: String, pool: List<String>): String? {
        val normalizedTarget = normalize(targetName)
        if (normalizedTarget.isEmpty()) return null

        pool.find { normalize(it) == normalizedTarget }?.let { return it }

        val targetTokens = tokenize(targetName)
        var bestMatch: String? = null
        var maxOverlapScore = 0.0

        for (poolName in pool) {
            val normalizedPool = normalize(poolName)
            if (normalizedPool.isEmpty()) continue

            if (normalizedTarget.contains(normalizedPool) || normalizedPool.contains(normalizedTarget)) {
                return poolName
            }

            val poolTokens = tokenize(poolName)
            if (poolTokens.isEmpty()) continue

            var matchCount = 0
            for (pToken in poolTokens) {
                if (targetTokens.any { it.contains(pToken) || pToken.contains(it) }) {
                    matchCount++
                }
            }

            val overlapScore = matchCount.toDouble() / poolTokens.size
            if (overlapScore > maxOverlapScore) {
                maxOverlapScore = overlapScore
                bestMatch = poolName
            }
        }

        if (maxOverlapScore >= 0.6) {
            return bestMatch
        }

        return null
    }

    private fun normalize(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9가-힣]"), "").uppercase()
    }

    private fun tokenize(name: String): List<String> {
        val tokens = mutableListOf<String>()
        val matcher = java.util.regex.Pattern.compile("[가-힣]+|[a-zA-Z]+|[0-9]+").matcher(name)
        while (matcher.find()) {
            val token = matcher.group().uppercase()
            if (token.length >= 1) tokens.add(token)
        }
        return tokens
    }

    private fun mapToEntity(reader: ExcelReader.RowReader): TransactionRawEntity {
        val rawDate = reader.getString(1)
        val rawStockName = reader.getString(5)
        return TransactionRawEntity(
            account = reader.getString(0),
            tradeDate = rawDate,             // 초기값으로 할당 (추후 교정)
            transactionDate = rawDate,       // 원본 일자 저장
            transactionNo = reader.getString(2),
            originalNo = reader.getString(3),
            type = reader.getString(4),
            transactionName = rawStockName,  // 원본 종목명 저장
            stockName = rawStockName,        // 초기값으로 할당 (추후 교정)
            quantity = reader.getLong(6),
            price = reader.getDouble(7),
            amount = reader.getLong(8),
            depositWithdrawalAmount = reader.getLong(9),
            balance = reader.getLong(10),
            stockBalance = reader.getLong(11),
            fee = reader.getLong(12),
            tax = reader.getLong(13),
            foreignAmount = reader.getDouble(14),
            foreignDWAmount = reader.getDouble(15),
            foreignBalance = reader.getDouble(16),
            foreignStock = reader.getDouble(17),
            uncollectedAmount = reader.getLong(18),
            repaidAmount = reader.getLong(19),
            currencyCode = reader.getString(20),
            relativeAgency = reader.getString(21),
            relativeClientName = reader.getString(22),
            relativeAccountNumber = reader.getString(23),
            recipientDisplay = reader.getString(24),
            myAccountDisplay = reader.getString(25)
        )
    }
}

class TransactionRawViewModelFactory(
    private val repository: TransactionRawRepository,
    private val transactionRepository: TransactionRepository,
    private val tradingLogRawRepository: TradingLogRawRepository,
    private val overseasTradingLogRawRepository: OverseasTradingLogRawRepository,
    private val excelReader: ExcelReader
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class <T>): T {
        if (modelClass.isAssignableFrom(TransactionRawViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionRawViewModel(repository, transactionRepository, tradingLogRawRepository, overseasTradingLogRawRepository, excelReader) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
