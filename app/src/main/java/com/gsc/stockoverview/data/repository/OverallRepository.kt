package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.api.NaverStockApiService
import com.gsc.stockoverview.data.api.YahooStockApiService
import com.gsc.stockoverview.data.dao.PortfolioDao
import com.gsc.stockoverview.data.dao.StockDao
import com.gsc.stockoverview.data.dao.TransactionDao
import com.gsc.stockoverview.data.entity.StockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class OverallStats(
    val title: String,
    val principal: Double = 0.0,         // 원금 (매수원금 또는 순입금액)
    val evaluatedAssets: Double = 0.0,   // 평가자산 (평가금액 + 예수금)
    val operatingAmount: Double = 0.0,   // 운용금액 (현재 보유 종목의 매수 원금 합계)
    val evaluatedAmount: Double = 0.0,   // 평가금액 (현재가 * 수량)
    val evaluatedProfit: Double = 0.0,   // 평가수익 (평가금액 - 운용금액)
    val realizedProfit: Double = 0.0,    // 실현손익 (매도손익 + 배당 + 이자)
    val deposit: Double = 0.0,           // 예수금 (원화 환산 합계)
    
    // 기타내역용 추가 필드
    val krwDeposit: Double = 0.0,
    val usdDeposit: Double = 0.0,
    val krwDividend: Double = 0.0,
    val usdDividend: Double = 0.0,
    val krwInterest: Double = 0.0,
    val usdInterest: Double = 0.0
)

class OverallRepository(
    private val transactionDao: TransactionDao,
    private val portfolioDao: PortfolioDao,
    private val stockDao: StockDao,
    private val naverApi: NaverStockApiService,
    private val yahooApi: YahooStockApiService
) {
    suspend fun getOverallData(): List<OverallStats> = withContext(Dispatchers.IO) {
        val allTransactions = transactionDao.getAllTransactions().first().sortedBy { it.tradeDate }
        val portfolioCodes = portfolioDao.getAllPortfolioItems().first().map { it.stockCode }.toSet()
        val allStocks = stockDao.getAllStocks().first()
        
        // 환율 정보 가져오기
        val exchangeRate = naverApi.fetchExchangeRate().let { if (it <= 0.0) 1450.0 else it }

        // 1. 예수금, 원금, 기타내역 계산
        var totalDepositKrw = 0.0
        var totalPrincipalKrw = 0.0

        //원화 예수금
        var krwDeposit = 0.0
        //달러 예수금
        var usdDeposit = 0.0
        //원화 배당금
        var krwDividend = 0.0
        //달러 배당금
        var usdDividend = 0.0
        //원화 이자
        var krwInterest = 0.0
        //외화 이자
        var usdInterest = 0.0

        allTransactions.forEach { t ->
            val rate = if (t.currencyCode == "USD") exchangeRate else 1.0
            val amountInKrw = t.amount * rate
            val feeInKrw = t.fee * rate
            val taxInKrw = t.tax * rate
            val netAmountInKrw = (t.amount - t.fee - t.tax) * rate
            val netAmountOriginal = t.amount - t.fee - t.tax

            when {
                t.typeDetail == "이체입금" -> {
                    totalDepositKrw += amountInKrw
                    totalPrincipalKrw += amountInKrw
                    if (t.currencyCode == "USD") usdDeposit += t.amount else krwDeposit += t.amount
                }
                t.typeDetail == "이체송금" -> {
                    totalDepositKrw -= amountInKrw
                    totalPrincipalKrw -= amountInKrw
                    if (t.currencyCode == "USD") usdDeposit -= t.amount else krwDeposit -= t.amount
                }
                t.type == "매수" -> {
                    val cost = amountInKrw + feeInKrw + taxInKrw
                    totalDepositKrw -= cost
                    if (t.currencyCode == "USD") usdDeposit -= (t.amount + t.fee + t.tax) else krwDeposit -= (t.amount + t.fee + t.tax)
                }
                t.type == "매도" -> {
                    totalDepositKrw += netAmountInKrw
                    if (t.currencyCode == "USD") usdDeposit += netAmountOriginal else krwDeposit += netAmountOriginal
                }
                // 배당금 처리 (사용자 지정 상세항목 적용)
                t.typeDetail == "ETF/상장클래스 분배금입금" -> {
                    totalDepositKrw += netAmountInKrw
                    krwDeposit += netAmountOriginal
                    krwDividend += netAmountOriginal
                }
                t.typeDetail == "배당금외화입금" -> {
                    totalDepositKrw += netAmountInKrw
                    usdDeposit += netAmountOriginal
                    usdDividend += netAmountOriginal
                }
                // 이자 처리 (사용자 지정 상세항목 적용)
                t.typeDetail == "예탁금이용료입금" -> {
                    totalDepositKrw += netAmountInKrw
                    krwDeposit += netAmountOriginal
                    krwInterest += netAmountOriginal
                }
                t.typeDetail == "외화예탁금이용료입금" -> {
                    totalDepositKrw += netAmountInKrw
                    usdDeposit += netAmountOriginal
                    usdInterest += netAmountOriginal
                }
                // 그 외 배당/이자성 항목 처리 (누락 방지)
                t.typeDetail.contains("배당금") || t.typeDetail.contains("분배금") -> {
                    totalDepositKrw += netAmountInKrw
                    if (t.currencyCode == "USD") {
                        usdDeposit += netAmountOriginal
                        usdDividend += netAmountOriginal
                    } else {
                        krwDeposit += netAmountOriginal
                        krwDividend += netAmountOriginal
                    }
                }
                t.typeDetail.contains("예탁금이용료") -> {
                    totalDepositKrw += netAmountInKrw
                    if (t.currencyCode == "USD") {
                        usdDeposit += netAmountOriginal
                        usdInterest += netAmountOriginal
                    } else {
                        krwDeposit += netAmountOriginal
                        krwInterest += netAmountOriginal
                    }
                }
            }
        }

        // 2. 종목별 통계 계산 (운용금액, 수량, 실현손익)
        val stockStatsMap = mutableMapOf<String, StockCalculation>()
        allTransactions.forEach { t ->
            val rate = if (t.currencyCode == "USD") exchangeRate else 1.0
            if (t.type == "매수" || t.type == "매도") {
                val calc = stockStatsMap.getOrPut(t.stockCode) { StockCalculation() }
                if (t.type == "매수") {
                    val totalCostKrw = (t.amount + t.fee + t.tax) * rate
                    calc.volume += t.volume
                    calc.investedAmount += totalCostKrw
                } else if (t.type == "매도") {
                    val avgCostKrw = if (calc.volume > 0) calc.investedAmount / calc.volume else 0.0
                    val sellNetKrw = (t.amount - t.fee - t.tax) * rate
                    val realizedKrw = sellNetKrw - (avgCostKrw * t.volume)

                    calc.realizedProfit += realizedKrw
                    calc.investedAmount -= (avgCostKrw * t.volume)
                    calc.volume -= t.volume

                    if (calc.volume <= 0.000001) {
                        calc.volume = 0.0
                        calc.investedAmount = 0.0
                    }
                }
            } else if (t.typeDetail.contains("배당금") || t.typeDetail.contains("분배금")) {
                val calc = stockStatsMap.getOrPut(t.stockCode) { StockCalculation() }
                calc.realizedProfit += (t.amount - t.fee - t.tax) * rate
            }
        }

        // 3. 카테고리별 합산
        var distOp = 0.0; var distEval = 0.0; var distRealized = 0.0
        var indOp = 0.0; var indEval = 0.0; var indRealized = 0.0

        stockStatsMap.forEach { (code, calc) ->
            if (code.isBlank()) return@forEach
            val stock = allStocks.find { it.stockCode == code }
            val currentPrice = if (calc.volume > 0) {
                fetchCurrentPrice(code, stock)
            } else 0.0
            
            val currency = stock?.currency ?: allTransactions.find { it.stockCode == code }?.currencyCode ?: "KRW"
            val currentRate = if (currency == "USD") exchangeRate else 1.0
            val evalAmtKrw = (currentPrice * currentRate) * calc.volume

            if (portfolioCodes.contains(code)) {
                distOp += calc.investedAmount
                distEval += evalAmtKrw
                distRealized += calc.realizedProfit
            } else {
                indOp += calc.investedAmount
                indEval += evalAmtKrw
                indRealized += calc.realizedProfit
            }
        }

        // 4. 결과 리스트 생성
        val distStats = OverallStats(
            title = "분산투자내역",
            operatingAmount = distOp,
            evaluatedAmount = distEval,
            evaluatedProfit = distEval - distOp,
            realizedProfit = distRealized,
            evaluatedAssets = distEval
        )

        val indStats = OverallStats(
            title = "개별투자내역",
            operatingAmount = indOp,
            evaluatedAmount = indEval,
            evaluatedProfit = indEval - indOp,
            realizedProfit = indRealized,
            evaluatedAssets = indEval
        )

        val stockStats = OverallStats(
            title = "주식투자내역",
            operatingAmount = distOp + indOp,
            evaluatedAmount = distEval + indEval,
            evaluatedProfit = (distEval + indEval) - (distOp + indOp),
            realizedProfit = distRealized + indRealized,
            evaluatedAssets = distEval + indEval
        )

        val otherStats = OverallStats(
            title = "기타내역",
            krwDeposit = krwDeposit,
            usdDeposit = usdDeposit,
            krwDividend = krwDividend,
            usdDividend = usdDividend,
            krwInterest = krwInterest,
            usdInterest = usdInterest,
            evaluatedAssets = totalDepositKrw
        )

        val totalStats = OverallStats(
            title = "총투자내역",
            principal = totalPrincipalKrw,
            operatingAmount = distOp + indOp,
            evaluatedAmount = distEval + indEval,
            evaluatedProfit = (distEval + indEval) - (distOp + indOp),
            realizedProfit = distRealized + indRealized + (krwDividend + usdDividend * exchangeRate) + (krwInterest + usdInterest * exchangeRate),
            deposit = totalDepositKrw,
            evaluatedAssets = (distEval + indEval) + totalDepositKrw
        )

        listOf(totalStats, stockStats, distStats, indStats, otherStats)
    }

    private suspend fun fetchCurrentPrice(stockCode: String, stock: StockEntity?): Double {
        if (stock == null) return 0.0
        return try {
            val updatedPrice = when {
                stock.marketType == "METALS" -> naverApi.fetchGoldPrice()
                stock.currency == "KRW" -> naverApi.fetchDomesticStockDetails(stockCode, stock.marketType)?.currentPrice
                else -> yahooApi.fetchOverseasStockDetails(stockCode, stock.stockName, stock.currency)?.currentPrice
            }
            updatedPrice ?: stock.currentPrice
        } catch (e: Exception) {
            stock.currentPrice
        }
    }

    private class StockCalculation {
        var volume: Double = 0.0
        var investedAmount: Double = 0.0
        var realizedProfit: Double = 0.0
    }
}
