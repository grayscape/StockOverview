package com.gsc.stockoverview.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gsc.stockoverview.data.entity.StockEntity
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
        viewModelScope.launch {
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val baseAmount = repository.getBaseAmount()
            val stats = repository.getStockStats()
            val allStocks = repository.getAllStocks()
            
            repository.getPortfolioItems().collectLatest { portfolioEntities ->
                val items = portfolioEntities.map { entity ->
                    val stock = allStocks.find { it.stockCode == entity.stockCode }
                    val currentPrice = repository.fetchCurrentPrice(entity.stockCode) ?: stock?.currentPrice ?: 0.0
                    val stat = stats[entity.stockCode]
                    val vol = stat?.volume ?: 0.0
                    val evalAmount = currentPrice * vol
                    val targetAmt = baseAmount * (entity.targetWeight / 100.0)
                    
                    // 목표비중보다 높으면 음수, 낮으면 양수로 표시하기 위해 (Target - Eval) 수행
                    val adjAmt = targetAmt - evalAmount
                    val adjRate = if (evalAmount != 0.0) (adjAmt / evalAmount) * 100.0 else 0.0
                    
                    PortfolioItemUi(
                        stockCode = entity.stockCode,
                        stockName = stock?.stockShortName ?: stock?.stockName ?: entity.stockCode,
                        targetWeight = entity.targetWeight,
                        targetAmount = targetAmt,
                        evaluationAmount = evalAmount,
                        currentWeight = 0.0, // 아래에서 계산
                        investedAmount = stat?.investedAmount ?: 0.0,
                        adjustmentAmount = adjAmt,
                        adjustmentRate = adjRate,
                        currentPrice = currentPrice,
                        volume = vol,
                        currency = stock?.currency ?: "KRW"
                    )
                }
                
                val totalEval = items.sumOf { it.evaluationAmount }
                val totalInvested = items.sumOf { it.investedAmount }
                val totalTargetW = items.sumOf { it.targetWeight }
                
                val itemsWithWeight = items.map { 
                    it.copy(currentWeight = if (totalEval != 0.0) (it.evaluationAmount / totalEval) * 100.0 else 0.0)
                }
                
                _uiState.update { 
                    it.copy(
                        items = itemsWithWeight,
                        totalBaseAmount = baseAmount,
                        totalEvaluationAmount = totalEval,
                        totalInvestedAmount = totalInvested,
                        totalTargetWeight = totalTargetW,
                        allStocks = allStocks,
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
}
