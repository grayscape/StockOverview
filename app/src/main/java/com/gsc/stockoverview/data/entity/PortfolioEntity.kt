package com.gsc.stockoverview.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portfolio")
data class PortfolioEntity(
    @PrimaryKey
    @ColumnInfo(name = "stock_code")
    val stockCode: String,

    @ColumnInfo(name = "target_weight")
    val targetWeight: Double // 비중 (%)
)
