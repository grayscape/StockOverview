package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.repository.OverallRepository
import com.gsc.stockoverview.data.repository.OverallStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OverallViewModel(private val repository: OverallRepository) : ViewModel() {

    private val _overallData = MutableStateFlow<List<OverallStats>>(emptyList())
    val overallData: StateFlow<List<OverallStats>> = _overallData.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _overallData.value = repository.getOverallData()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class OverallViewModelFactory(private val repository: OverallRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OverallViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OverallViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
