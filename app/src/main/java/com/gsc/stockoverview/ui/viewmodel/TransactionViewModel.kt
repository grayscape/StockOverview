package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gsc.stockoverview.data.entity.TransactionEntity
import com.gsc.stockoverview.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow

class TransactionViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    val transactionList: Flow<List<TransactionEntity>> = repository.allTransactions
}

class TransactionViewModelFactory(
    private val repository: TransactionRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
