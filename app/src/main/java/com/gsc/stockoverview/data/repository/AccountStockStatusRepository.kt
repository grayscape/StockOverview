package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.AccountStockStatusDao
import com.gsc.stockoverview.data.entity.AccountStockStatusEntity
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class AccountStockStatusRepository(
    private val accountStockStatusDao: AccountStockStatusDao
) {
    val allAccountStockStatusEntity: Flow<List<AccountStockStatusEntity>> = accountStockStatusDao.getAllAccountStockStatus()

    suspend fun insertAccountStockStatusList(list: List<AccountStockStatusEntity>) {
        accountStockStatusDao.insertAccountStockStatusList(list)
    }

    suspend fun deleteAll() {
        accountStockStatusDao.deleteAll()
    }

    /**
     * 거래 내역을 바탕으로 계좌별 종목 현황을 재계산하여 갱신합니다.
     * @return 갱신된 종목 현황 리스트
     */
    suspend fun refreshStatus(transactions: List<TransactionEntity>, allStocks: List<StockEntity>): List<AccountStockStatusEntity> {
        val stockStatusList = transactions
            .filter { it.stockCode.isNotBlank() }
            .groupBy { it.account to it.stockCode }
            .map { (key, stockTransactions) ->
                val (account, stockCode) = key
                var currentQuantity = 0.0
                var totalCost = 0.0
                var totalRealizedProfitLoss = 0.0
                var totalWeightedYield = 0.0
                var totalSaleCost = 0.0
                
                // 해당 종목의 통화코드 (첫 번째 거래 내역에서 가져오거나 기본값 설정)
                val currencyCode = stockTransactions.firstOrNull()?.currencyCode ?: "KRW"

                // 날짜와 순서에 따라 정렬하여 순차 계산
                stockTransactions.sortedWith(compareBy<TransactionEntity> { it.tradeDate }.thenBy { it.transactionOrder })
                    .forEach { tx ->
                        when (tx.type) {
                            "매수" -> {
                                currentQuantity += tx.volume
                                //증권 프로그램과 매수 금액을 동일하게 표시하기 위해 수수료 차감을 하지 않는다.
                                totalCost += tx.amount
                            }
                            "매도" -> {
                                if (currentQuantity > 0) {
                                    val avgPrice = totalCost / currentQuantity
                                    val soldCost = tx.volume * avgPrice
                                    totalCost -= soldCost
                                }
                                currentQuantity -= tx.volume
                                
                                // TransactionEntity의 profitLoss와 yield를 직접 사용
                                totalRealizedProfitLoss += tx.profitLoss
                                val saleCost = tx.amount - tx.profitLoss
                                totalWeightedYield += tx.yield * saleCost
                                totalSaleCost += saleCost
                            }
                        }
                    }

                val avgPrice = if (currentQuantity > 0) totalCost / currentQuantity else 0.0
                val purchaseAmount = totalCost
                // 전체 수익률은 개별 매도 건의 yield를 매도 원가(saleCost) 기준으로 가중 평균하여 계산
                val realizedProfitLossRate = if (totalSaleCost > 0) (totalWeightedYield / totalSaleCost).toFloat() else 0f

                AccountStockStatusEntity(
                    account = account,
                    stockCode = stockCode,
                    quantity = currentQuantity,
                    averagePrice = avgPrice,
                    purchaseAmount = purchaseAmount,
                    realizedProfitLoss = totalRealizedProfitLoss,
                    realizedProfitLossRate = realizedProfitLossRate,
                    currencyCode = currencyCode
                )
            }
            .filter { it.quantity != 0.0 || it.realizedProfitLoss != 0.0 }

        deleteAll()
        insertAccountStockStatusList(stockStatusList)
        return stockStatusList
    }
}
