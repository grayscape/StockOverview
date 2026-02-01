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
    val deposit: Double = 0.0            // 예수금
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

        // 1. 예수금 및 총 원금 계산
        var totalDeposit = 0.0
        var totalPrincipal = 0.0
        var depositInterestProfit = 0.0 // 예금투자내역 (이자수익)

        allTransactions.forEach { t ->
            when {
                t.typeDetail == "이체입금" -> {
                    totalDeposit += t.amount
                    totalPrincipal += t.amount
                }
                t.typeDetail == "이체출금" -> {
                    totalDeposit -= t.amount
                    totalPrincipal -= t.amount
                }
                t.type == "매수" -> {
                    totalDeposit -= (t.amount + t.fee + t.tax)
                }
                t.type == "매도" -> {
                    totalDeposit += (t.amount - t.fee - t.tax)
                }
                t.typeDetail == "배당금입금" || t.typeDetail == "외화배당금입금" -> {
                    totalDeposit += (t.amount - t.fee - t.tax)
                }
                t.typeDetail == "예탁금이용료입금" || t.typeDetail == "외화예탁금이용료입금" -> {
                    val netInterest = t.amount - t.fee - t.tax
                    totalDeposit += netInterest
                    depositInterestProfit += netInterest
                }
            }
        }

        // 2. 종목별 통계 계산 (운용금액, 수량, 실현손익)
        val stockStatsMap = mutableMapOf<String, StockCalculation>()
        allTransactions.forEach { t ->
            if (t.type == "매수" || t.type == "매도") {
                val calc = stockStatsMap.getOrPut(t.stockCode) { StockCalculation() }
                if (t.type == "매수") {
                    val totalCost = t.amount + t.fee + t.tax
                    calc.volume += t.volume
                    calc.investedAmount += totalCost
                } else if (t.type == "매도") {
                    // 평균단가 기반 실현손익 계산 (이동평균법)
                    val avgCost = if (calc.volume > 0) calc.investedAmount / calc.volume else 0.0
                    val sellNet = t.amount - t.fee - t.tax
                    val realized = sellNet - (avgCost * t.volume)

                    calc.realizedProfit += realized
                    calc.investedAmount -= (avgCost * t.volume)
                    calc.volume -= t.volume

                    if (calc.volume <= 0.000001) {
                        calc.volume = 0.0
                        calc.investedAmount = 0.0
                    }
                }
            } else if (t.typeDetail.contains("배당금")) {
                val calc = stockStatsMap.getOrPut(t.stockCode) { StockCalculation() }
                calc.realizedProfit += (t.amount - t.fee - t.tax)
            }
        }

        // 3. 카테고리별 합산 (분산 vs 개별)
        var distOp = 0.0; var distEval = 0.0; var distRealized = 0.0
        var indOp = 0.0; var indEval = 0.0; var indRealized = 0.0

        stockStatsMap.forEach { (code, calc) ->
            if (code.isBlank()) return@forEach
            val stock = allStocks.find { it.stockCode == code }
            val currentPrice = if (calc.volume > 0) {
                fetchCurrentPrice(code, stock)
            } else 0.0

            val evalAmt = currentPrice * calc.volume

            if (portfolioCodes.contains(code)) {
                distOp += calc.investedAmount
                distEval += evalAmt
                distRealized += calc.realizedProfit
            } else {
                indOp += calc.investedAmount
                indEval += evalAmt
                indRealized += calc.realizedProfit
            }
        }

        // 4. 결과 리스트 생성
        val distStats = OverallStats(
            title = "분산투자내역",
            principal = distOp,
            operatingAmount = distOp,
            evaluatedAmount = distEval,
            evaluatedProfit = distEval - distOp,
            realizedProfit = distRealized,
            evaluatedAssets = distEval,
            deposit = 0.0
        )

        val indStats = OverallStats(
            title = "개별투자내역",
            principal = indOp,
            operatingAmount = indOp,
            evaluatedAmount = indEval,
            evaluatedProfit = indEval - indOp,
            realizedProfit = indRealized,
            evaluatedAssets = indEval,
            deposit = 0.0
        )

        val stockStats = OverallStats(
            title = "주식투자내역",
            principal = distOp + indOp,
            operatingAmount = distOp + indOp,
            evaluatedAmount = distEval + indEval,
            evaluatedProfit = (distEval + indEval) - (distOp + indOp),
            realizedProfit = distRealized + indRealized,
            evaluatedAssets = distEval + indEval,
            deposit = 0.0
        )

        val depositInvStats = OverallStats(
            title = "예금투자내역",
            principal = totalPrincipal - (distOp + indOp),
            realizedProfit = depositInterestProfit,
            evaluatedAssets = (totalPrincipal - (distOp + indOp)) + depositInterestProfit, // 원금 + 이자
            operatingAmount = 0.0,
            evaluatedAmount = 0.0,
            evaluatedProfit = 0.0,
            deposit = totalDeposit
        )

        val totalStats = OverallStats(
            title = "총투자내역",
            principal = totalPrincipal,
            operatingAmount = distOp + indOp,
            evaluatedAmount = distEval + indEval,
            evaluatedProfit = (distEval + indEval) - (distOp + indOp),
            realizedProfit = distRealized + indRealized + depositInterestProfit,
            deposit = totalDeposit,
            evaluatedAssets = (distEval + indEval) + totalDeposit
        )

        listOf(totalStats, stockStats, distStats, indStats, depositInvStats)
    }

    private suspend fun fetchCurrentPrice(stockCode: String, stock: StockEntity?): Double {
        if (stock == null) return 0.0
        return try {
            val updated = if (stock.currency == "KRW") {
                naverApi.fetchDomesticStockDetails(stockCode, stock.marketType)
            } else {
                yahooApi.fetchOverseasStockDetails(stockCode, stock.stockName, stock.currency)
            }
            updated?.currentPrice ?: stock.currentPrice
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
