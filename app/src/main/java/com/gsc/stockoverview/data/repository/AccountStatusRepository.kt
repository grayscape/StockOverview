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
     * 거래 내역과 계산된 종목 현황을 바탕으로 계좌별 상태(운용자금, 실현손익, 예수금 등)를 계산하여 저장합니다.
     */
    suspend fun refreshAccountStatus(
        transactions: List<TransactionEntity>,
        stockStatusList: List<AccountStockStatusEntity>
    ) {
        if (transactions.isEmpty()) return

        // 1. 계좌별 통화별 예수금 변동액 계산 (입금, 출금, 이자, 매수, 매도, 세금)
        // TransactionEntity의 amount는 거래금액
        // 배당은 type이 이자로 되어 있어 별도 포함하지 않는다.
        // 세금은 type이 이자인 경우에만 세금이 있다.
        // 매수, 매도는 amount에 이미 수수료/세금을 뺀 금액으로 반영 되어 있다.
        // 예수금 = (입금 - 출금 + 이자 + 배당 + 매도대금) - (매수대금 + 세금)
        val depositByAccountAndCurrency = transactions
            .groupBy { it.account to (it.currencyCode ?: "KRW") }
            .mapValues { (_, txs) ->
                txs.sumOf { t ->
                    when (t.type) {
                        "입금", "매도" -> t.amount
                        "이자" -> (t.amount - t.tax)
                        "출금" -> -t.amount
                        "매수" -> -t.amount
                        else -> {
                            0.0
                        }
                    }
                }
            }

        // 2. 계좌별 종목 현황 데이터 합산 (실현손익, 총 투자금액, 실현손익률 합계)
        val stockMetricsByAccount = stockStatusList.groupBy { it.account }.mapValues { (_, list) ->
            object {
                val totalRealizedPnL = list.sumOf { it.realizedProfitLoss }
                val totalInvestment = list.sumOf { it.investmentAmount }
                val sumRealizedPnLRate = list.sumOf { it.realizedProfitLossRate.toDouble() }.toFloat()
            }
        }

        val allAccounts = (depositByAccountAndCurrency.keys.map { it.first } + stockMetricsByAccount.keys).distinct()

        val accountStatusEntities = allAccounts.map { account ->
            val krwDeposit = depositByAccountAndCurrency[account to "KRW"] ?: 0.0
            val usdDeposit = depositByAccountAndCurrency[account to "USD"] ?: 0.0

            val metrics = stockMetricsByAccount[account]
            val realizedPnL = metrics?.totalRealizedPnL ?: 0.0
            val totalInvestment = metrics?.totalInvestment ?: 0.0
            val realizedPnLRate = metrics?.sumRealizedPnLRate ?: 0f

            // 운용자금 = 현재 종목들에 투자된 금액의 합계
            val operatingFunds = totalInvestment

            AccountStatusEntity(
                account = account,
                operatingFunds = operatingFunds,
                realizedProfitLoss = realizedPnL,
                realizedProfitLossRate = realizedPnLRate,
                krwDeposit = krwDeposit,
                usdDeposit = usdDeposit
            )
        }

        // 전체 합계 데이터 추가
        if (accountStatusEntities.isNotEmpty()) {
            val totalRealizedPnL = accountStatusEntities.sumOf { it.realizedProfitLoss }
            val totalKrwDeposit = accountStatusEntities.sumOf { it.krwDeposit }
            val totalUsdDeposit = accountStatusEntities.sumOf { it.usdDeposit }
            val totalOperatingFunds = accountStatusEntities.sumOf { it.operatingFunds }
            val totalRealizedPnLRate = accountStatusEntities.sumOf { it.realizedProfitLossRate.toDouble() }.toFloat()

            val totalEntity = AccountStatusEntity(
                account = "전체",
                operatingFunds = totalOperatingFunds,
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
