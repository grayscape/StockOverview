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

        // 1. 계좌별 입금, 출금액 계산 (원화 기준 - 현재는 KRW/USD 구분 없이 합산하거나 KRW 위주로 처리할 수 있으나,
        // 일반적으로 '원금'은 투자한 총 금액을 의미하므로 원화 환산이 필요할 수 있음.
        // 여기서는 일단 계좌별로 합산)
        val statsByAccount = transactions.groupBy { it.account }.mapValues { (_, txs) ->
            val deposit = txs.filter { it.type == "입금" }.sumOf { it.amount }
            val withdrawal = txs.filter { it.type == "출금" }.sumOf { it.amount }
            object {
                val totalDeposit = deposit
                val totalWithdrawal = withdrawal
                val principal = deposit - withdrawal
            }
        }

        // 2. 계좌별 통화별 예수금 변동액 계산 (입금, 출금, 이자, 매수, 매도, 세금)
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

        // 3. 계좌별 종목 현황 데이터 합산 (실현손익, 총 투자금액, 실현손익률 합계)
        val stockMetricsByAccount = stockStatusList.groupBy { it.account }.mapValues { (_, list) ->
            object {
                val totalRealizedPnL = list.sumOf { it.realizedProfitLoss }
                val sumRealizedPnLRate = list.sumOf { it.realizedProfitLossRate.toDouble() }.toFloat()
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
            val realizedPnL = metrics?.totalRealizedPnL ?: 0.0
            val realizedPnLRate = metrics?.sumRealizedPnLRate ?: 0f

            AccountStatusEntity(
                account = account,
                totalDeposit = totalDeposit,
                totalWithdrawal = totalWithdrawal,
                principal = principal,
                realizedProfitLoss = realizedPnL,
                realizedProfitLossRate = realizedPnLRate,
                krwDeposit = krwDeposit,
                usdDeposit = usdDeposit
            )
        }

        // 전체 합계 데이터 추가
        if (accountStatusEntities.isNotEmpty()) {
            // "전체" 계산 시 입금, 출금은 typeDetail이 "이체입금", "이체송금"인 거래만 합산하여 계산
            val totalDeposit = transactions.filter { it.typeDetail == "이체입금" }.sumOf { it.amount }
            val totalWithdrawal = transactions.filter { it.typeDetail == "이체송금" }.sumOf { it.amount }
            val totalPrincipal = totalDeposit - totalWithdrawal
            val totalRealizedPnL = accountStatusEntities.sumOf { it.realizedProfitLoss }
            val totalKrwDeposit = accountStatusEntities.sumOf { it.krwDeposit }
            val totalUsdDeposit = accountStatusEntities.sumOf { it.usdDeposit }
            val totalRealizedPnLRate = accountStatusEntities.sumOf { it.realizedProfitLossRate.toDouble() }.toFloat()

            val totalEntity = AccountStatusEntity(
                account = "전체",
                totalDeposit = totalDeposit,
                totalWithdrawal = totalWithdrawal,
                principal = totalPrincipal,
                realizedProfitLoss = totalRealizedPnL,
                realizedProfitLossRate = totalRealizedPnLRate,
                krwDeposit = totalKrwDeposit,
                usdDeposit = totalUsdDeposit
            )

            clearAccountData()
            insertAccountStatusList(accountStatusEntities + totalEntity)
        }
    }
}
