package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.repository.AccountStatusRepository
import com.gsc.stockoverview.data.repository.AccountStockStatusRepository
import com.gsc.stockoverview.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransactionViewModel(
    private val repository: TransactionRepository,
    private val accountStockStatusRepository: AccountStockStatusRepository,
    private val accountRepository: AccountStatusRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow("전체")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactionList: Flow<List<TransactionEntity>> = _selectedTab.flatMapLatest { tab ->
        if (tab == "전체") {
            repository.allTransactions
        } else {
            repository.getTransactionsByAccount(tab)
        }
    }

    fun selectTab(tab: String) {
        _selectedTab.value = tab
    }

    fun syncFromRawData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // TransactionRepository에서 데이터 동기화
                val (transactions, allStocks) = repository.syncFromRawData()
                if (transactions.isNotEmpty()) {
                    // AccountStockStatusRepository에서 종목별 현황 갱신 및 결과 수신
                    val stockStatusList = accountStockStatusRepository.refreshStatus(transactions, allStocks)
                    // AccountRepository에서 계좌별 상태 갱신 (종목 현황 데이터 사용)
                    accountRepository.refreshAccountStatus(transactions, stockStatusList)
                }
            }
            onComplete()
        }
    }
}

class TransactionViewModelFactory(
    private val repository: TransactionRepository,
    private val accountStockStatusRepository: AccountStockStatusRepository,
    private val accountRepository: AccountStatusRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository, accountStockStatusRepository, accountRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
