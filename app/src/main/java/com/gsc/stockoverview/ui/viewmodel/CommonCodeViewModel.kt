package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.gsc.stockoverview.data.entity.CommonCodeEntity
import com.gsc.stockoverview.data.repository.CommonCodeRepository
import kotlinx.coroutines.flow.Flow

class CommonCodeViewModel(private val repository: CommonCodeRepository) : ViewModel() {

    val allCodes: Flow<List<CommonCodeEntity>> = repository.allCodes

    fun getCodesByParent(parentCode: String): Flow<List<CommonCodeEntity>> {
        return repository.getCodesByParent(parentCode)
    }
}

class CommonCodeViewModelFactory(private val repository: CommonCodeRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CommonCodeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CommonCodeViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
