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
     * 거래 내역과 계산된 종목 현황을 바탕으로 계좌별 상태(원금, 손익금액, 예수금 등)를 계산하여 저장합니다.
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
                val krwProfitLossAmount = krwStocks.sumOf { it.profitLossAmount }
                val krwSumProfitLossRate = krwStocks.sumOf { it.profitLossRate.toDouble() }.toFloat()
                val usdProfitLossAmount = usdStocks.sumOf { it.profitLossAmount }
                val usdSumProfitLossRate = usdStocks.sumOf { it.profitLossRate.toDouble() }.toFloat()
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
                krwProfitLossAmount = metrics?.krwProfitLossAmount ?: 0.0,
                krwProfitLossRate = metrics?.krwSumProfitLossRate ?: 0f,
                usdProfitLossAmount = metrics?.usdProfitLossAmount ?: 0.0,
                usdProfitLossRate = metrics?.usdSumProfitLossRate ?: 0f,
                krwDeposit = krwDeposit,
                usdDeposit = usdDeposit
            )
        }

        // 전체 합계 데이터 추가
        if (accountStatusEntities.isNotEmpty()) {
            val totalDeposit = transactions.filter { it.typeDetail == "이체입금" }.sumOf { it.amount }
            val totalWithdrawal = transactions.filter { it.typeDetail == "이체송금" }.sumOf { it.amount }
            val totalPrincipal = totalDeposit - totalWithdrawal
            
            val totalKrwProfitLossAmount = accountStatusEntities.sumOf { it.krwProfitLossAmount }
            val totalKrwProfitLossRate = accountStatusEntities.sumOf { it.krwProfitLossRate.toDouble() }.toFloat()
            val totalUsdProfitLossAmount = accountStatusEntities.sumOf { it.usdProfitLossAmount }
            val totalUsdProfitLossRate = accountStatusEntities.sumOf { it.usdProfitLossRate.toDouble() }.toFloat()
            
            val totalKrwDeposit = accountStatusEntities.sumOf { it.krwDeposit }
            val totalUsdDeposit = accountStatusEntities.sumOf { it.usdDeposit }

            val totalEntity = AccountStatusEntity(
                account = "전체",
                totalDeposit = totalDeposit,
                totalWithdrawal = totalWithdrawal,
                principal = totalPrincipal,
                krwProfitLossAmount = totalKrwProfitLossAmount,
                krwProfitLossRate = totalKrwProfitLossRate,
                usdProfitLossAmount = totalUsdProfitLossAmount,
                usdProfitLossRate = totalUsdProfitLossRate,
                krwDeposit = totalKrwDeposit,
                usdDeposit = totalUsdDeposit
            )

            clearAccountData()
            insertAccountStatusList(accountStatusEntities + totalEntity)
        }
    }
}
