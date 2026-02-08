package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.StockEntity
import com.gsc.stockoverview.data.repository.PortfolioItemDomainModel
import com.gsc.stockoverview.data.repository.PortfolioRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PortfolioUiState(
    val items: List<PortfolioItemUi> = emptyList(),
    val totalBaseAmount: Double = 0.0,
    val totalEvaluationAmount: Double = 0.0,
    val totalInvestedAmount: Double = 0.0,
    val totalTargetWeight: Double = 0.0,
    val allStocks: List<StockEntity> = emptyList(),
    val isLoading: Boolean = false
)

data class PortfolioItemUi(
    val stockCode: String,
    val stockName: String,
    val targetWeight: Double,      // 목표비중 (%)
    val targetAmount: Double,      // 비중금액 (TotalBase * targetWeight)
    val evaluationAmount: Double,  // 평가금액 (Price * Vol)
    val currentWeight: Double,     // 평가비중 (%)
    val investedAmount: Double,    // 투자금액 (매수원금)
    val adjustmentAmount: Double,  // 조정금액 (EvalAmount - TargetAmount) -> 목표보다 높으면 음수가 되도록 처리
    val adjustmentRate: Double,    // 조정률 (%)
    val currentPrice: Double,
    val volume: Double,
    val currency: String
)

class PortfolioViewModel(private val repository: PortfolioRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PortfolioUiState())
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            repository.getPortfolioDomainModel().collectLatest { domainModel ->
                _uiState.update { 
                    it.copy(
                        items = domainModel.items.map { item -> item.toUiModel() },
                        totalBaseAmount = domainModel.totalBaseAmount,
                        totalEvaluationAmount = domainModel.totalEvaluationAmount,
                        totalInvestedAmount = domainModel.totalInvestedAmount,
                        totalTargetWeight = domainModel.totalTargetWeight,
                        allStocks = domainModel.allStocks,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun updateWeight(stockCode: String, weight: Double) {
        viewModelScope.launch {
            repository.updateWeight(stockCode, weight)
        }
    }

    fun addStock(stockCode: String) {
        viewModelScope.launch {
            repository.addStock(stockCode)
        }
    }

    fun deleteStock(stockCode: String) {
        viewModelScope.launch {
            repository.deleteStock(stockCode)
        }
    }

    private fun PortfolioItemDomainModel.toUiModel(): PortfolioItemUi {
        return PortfolioItemUi(
            stockCode = stockCode,
            stockName = stockName,
            targetWeight = targetWeight,
            targetAmount = targetAmount,
            evaluationAmount = evaluationAmount,
            currentWeight = currentWeight,
            investedAmount = investedAmount,
            adjustmentAmount = adjustmentAmount,
            adjustmentRate = adjustmentRate,
            currentPrice = currentPrice,
            volume = volume,
            currency = currency
        )
    }
}
