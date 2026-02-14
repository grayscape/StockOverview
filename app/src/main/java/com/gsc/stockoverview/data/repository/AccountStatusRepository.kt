package com.gsc.stockoverview.data.repository

import com.gsc.stockoverview.data.dao.AccountStatusDao
import com.gsc.stockoverview.data.entity.AccountStatusEntity
import com.gsc.stockoverview.data.entity.AccountStockStatusEntity
import com.gsc.stockoverview.data.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

class AccountStatusRepository(
    private val accountStatusDao: AccountStatusDao
) {
    val latestAccountStatusEntity: Flow<AccountStatusEntity?> = accountStatusDao.getLatestAccountStatus()

    suspend fun insertAccountStatus(accountStatusEntity: AccountStatusEntity) {
        accountStatusDao.insertAccountStatus(accountStatusEntity)
    }

    suspend fun insertAccountStatusList(list: List<AccountStatusEntity>) {
        list.forEach { insertAccountStatus(it) }
    }

    suspend fun clearAccountData() {
        accountStatusDao.deleteAll()
    }

    /**
     * 거래 내역과 계산된 종목 현황을 바탕으로 계좌별 상태(원금, 실현손익, 예수금 등)를 계산하여 저장합니다.
     */
    suspend fun refreshAccountStatus(
        transactions: List<TransactionEntity>,
        stockStatusList: List<AccountStockStatusEntity>
    ) {
        if (transactions.isEmpty()) return

        // 1. 계좌별 입금, 출금액 계산
        val statsByAccount = transactions.groupBy { it.account }.mapValues { (_, txs) ->
            val deposit = txs.filter { it.type == "입금" }.sumOf { it.amount }
            val withdrawal = txs.filter { it.type == "출금" }.sumOf { it.amount }
            object {
                val totalDeposit = deposit
                val totalWithdrawal = withdrawal
                val principal = deposit - withdrawal
            }
        }

        // 2. 계좌별 통화별 예수금 변동액 계산
        val depositByAccountAndCurrency = transactions
            .groupBy { it.account to (it.currencyCode ?: "KRW") }
            .mapValues { (_, txs) ->
                txs.sumOf { t ->
                    when (t.type) {
                        "입금", "매도" -> t.amount
                        "이자" -> (t.amount - t.tax)
                        "출금" -> -t.amount
                        "매수" -> -t.amount
                        else -> 0.0
                    }
                }
            }

        // 3. 계좌별 종목 현황 데이터 합산 (원화/달러 분리)
        val stockMetricsByAccount = stockStatusList.groupBy { it.account }.mapValues { (_, list) ->
            val krwStocks = list.filter { it.currencyCode == "KRW" }
            val usdStocks = list.filter { it.currencyCode == "USD" }
            object {
                val krwRealizedProfitLoss = krwStocks.sumOf { it.realizedProfitLoss }
                val krwSumRealizedProfitLossRate = krwStocks.sumOf { it.realizedProfitLossRate.toDouble() }.toFloat()
                val krwPurchaseAmount = krwStocks.sumOf { it.purchaseAmount }
                val usdRealizedProfitLoss = usdStocks.sumOf { it.realizedProfitLoss }
                val usdSumRealizedProfitLossRate = usdStocks.sumOf { it.realizedProfitLossRate.toDouble() }.toFloat()
                val usdPurchaseAmount = usdStocks.sumOf { it.purchaseAmount }
            }
        }

        val allAccounts = (statsByAccount.keys + stockMetricsByAccount.keys).distinct()

        val accountStatusEntities = allAccounts.map { account ->
            val stats = statsByAccount[account]
            val totalDeposit = stats?.totalDeposit ?: 0.0
            val totalWithdrawal = stats?.totalWithdrawal ?: 0.0
            val principal = stats?.principal ?: 0.0

            val krwDeposit = depositByAccountAndCurrency[account to "KRW"] ?: 0.0
            val usdDeposit = depositByAccountAndCurrency[account to "USD"] ?: 0.0

            val metrics = stockMetricsByAccount[account]
            
            AccountStatusEntity(
                account = account,
                totalDeposit = totalDeposit,
                totalWithdrawal = totalWithdrawal,
                principal = principal,
                krwRealizedProfitLoss = metrics?.krwRealizedProfitLoss ?: 0.0,
                krwRealizedProfitLossRate = metrics?.krwSumRealizedProfitLossRate ?: 0f,
                usdRealizedProfitLoss = metrics?.usdRealizedProfitLoss ?: 0.0,
                usdRealizedProfitLossRate = metrics?.usdSumRealizedProfitLossRate ?: 0f,
                krwPurchaseAmount = metrics?.krwPurchaseAmount ?: 0.0,
                usdPurchaseAmount = metrics?.usdPurchaseAmount ?: 0.0,
                krwDeposit = krwDeposit,
                usdDeposit = usdDeposit
            )
        }

        // 전체 합계 데이터 추가
        if (accountStatusEntities.isNotEmpty()) {
            val totalDeposit = transactions.filter { it.typeDetail == "이체입금" }.sumOf { it.amount }
            val totalWithdrawal = transactions.filter { it.typeDetail == "이체송금" }.sumOf { it.amount }
            val totalPrincipal = totalDeposit - totalWithdrawal
            
            val totalKrwRealizedProfitLoss = accountStatusEntities.sumOf { it.krwRealizedProfitLoss }
            val totalKrwRealizedProfitLossRate = accountStatusEntities.sumOf { it.krwRealizedProfitLossRate.toDouble() }.toFloat()
            val totalUsdRealizedProfitLoss = accountStatusEntities.sumOf { it.usdRealizedProfitLoss }
            val totalUsdRealizedProfitLossRate = accountStatusEntities.sumOf { it.usdRealizedProfitLossRate.toDouble() }.toFloat()

            val totalKrwPurchaseAmount = accountStatusEntities.sumOf { it.krwPurchaseAmount }
            val totalUsdPurchaseAmount = accountStatusEntities.sumOf { it.usdPurchaseAmount }
            
            val totalKrwDeposit = accountStatusEntities.sumOf { it.krwDeposit }
            val totalUsdDeposit = accountStatusEntities.sumOf { it.usdDeposit }

            val totalEntity = AccountStatusEntity(
                account = "전체",
                totalDeposit = totalDeposit,
                totalWithdrawal = totalWithdrawal,
                principal = totalPrincipal,
                krwRealizedProfitLoss = totalKrwRealizedProfitLoss,
                krwRealizedProfitLossRate = totalKrwRealizedProfitLossRate,
                usdRealizedProfitLoss = totalUsdRealizedProfitLoss,
                usdRealizedProfitLossRate = totalUsdRealizedProfitLossRate,
                krwPurchaseAmount = totalKrwPurchaseAmount,
                usdPurchaseAmount = totalUsdPurchaseAmount,
                krwDeposit = totalKrwDeposit,
                usdDeposit = totalUsdDeposit
            )

            clearAccountData()
            insertAccountStatusList(accountStatusEntities + totalEntity)
        }
    }
}
