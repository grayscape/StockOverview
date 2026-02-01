package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.CommonCodeEntity
import com.gsc.stockoverview.data.repository.CommonCodeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CommonCodeViewModel(private val repository: CommonCodeRepository) : ViewModel() {

    val allCodes: Flow<List<CommonCodeEntity>> = repository.allCodes

    fun getCodesByParent(parentCode: String): Flow<List<CommonCodeEntity>> {
        return repository.getCodesByParent(parentCode)
    }

    fun initDefaultCodes() {
        viewModelScope.launch {
            val existing = repository.allCodes.first()
            if (existing.isEmpty()) {
                val defaultCodes = listOf(
                    CommonCodeEntity(code = "ACC_ROOT", name = "계좌구분", description = "계좌 구분 루트 코드"),
                    CommonCodeEntity(code = "일반", parentCode = "ACC_ROOT", name = "일반", sortOrder = 1),
                    CommonCodeEntity(code = "ISA", parentCode = "ACC_ROOT", name = "ISA", sortOrder = 2),
                    CommonCodeEntity(code = "연금", parentCode = "ACC_ROOT", name = "연금", sortOrder = 3),
                    CommonCodeEntity(code = "IRP", parentCode = "ACC_ROOT", name = "IRP", sortOrder = 4),
                    CommonCodeEntity(code = "퇴직IRP", parentCode = "ACC_ROOT", name = "퇴직IRP", sortOrder = 5),
                    CommonCodeEntity(code = "금통장", parentCode = "ACC_ROOT", name = "금통장", sortOrder = 6),
                    CommonCodeEntity(code = "CMA", parentCode = "ACC_ROOT", name = "CMA", sortOrder = 7)
                )
                repository.insertAll(defaultCodes)
            }
        }
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
