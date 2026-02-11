package com.gsc.stockoverview.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun <T> StockTable(
    headers: List<String>,
    items: List<T>,
    cellContent: (T) -> List<String>
) {
    val scrollState = rememberScrollState()

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("데이터가 없습니다.")
        }
        return
    }

    // Narrow columns
    val narrowHeaders = listOf("계좌", "구분", "수량", "매수수량", "매도수량", "잔고수량", "번호")
    val feeHeaders = listOf("수수료", "매매비용")
    val dateHeaders = listOf("매매일자", "거래일자")

    Column(modifier = Modifier.horizontalScroll(scrollState)) {
        // Header
        Row(modifier = Modifier.background(Color.LightGray)) {
            headers.forEach { header ->
                val columnWidth = when (header) {
                    in narrowHeaders -> 35.dp
                    in feeHeaders -> 45.dp
                    in dateHeaders -> 60.dp
                    else -> 85.dp
                }
                TableCell(header, true, columnWidth)
            }
        }
        
        // Data Rows
        LazyColumn(modifier = Modifier.fillMaxHeight()) {
            items(items) { item ->
                Row {
                    val cells = cellContent(item)
                    cells.forEachIndexed { index, cell ->
                        val header = headers.getOrNull(index)
                        val columnWidth = when (header) {
                            in narrowHeaders -> 35.dp
                            in feeHeaders -> 45.dp
                            in dateHeaders -> 60.dp
                            else -> 85.dp
                        }
                        TableCell(cell, false, columnWidth)
                    }
                }
                HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun TableCell(text: String, isHeader: Boolean = false, width: Dp = 85.dp) {
    Text(
        text = text,
        modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .width(width),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

fun formatLong(value: Long) = String.format(Locale.getDefault(), "%,d", value)

private val decimalFormat = DecimalFormat("#,###.##")
private val integerFormat = DecimalFormat("#,###")
private val precisionFormat = DecimalFormat("#,###.00")

fun formatDouble(value: Double): String = decimalFormat.format(value)

/**
 * 통화별 포맷팅 규칙 적용
 */
fun formatCurrency(value: Double, currency: String, isRate: Boolean = false): String {
    return if (currency == "KRW") {
        if (isRate) {
            // 수익률, 평가손익률: 소수 2자리 반올림
            precisionFormat.format((value * 100.0).roundToInt() / 100.0)
        } else {
            // 일반 금액: 소수점 없음
            integerFormat.format(value.roundToLong())
        }
    } else if (currency == "USD") {
        if (isRate) {
            precisionFormat.format((value * 100.0).roundToInt() / 100.0)
        } else {
            // USD 금액: 소수 2자리
            precisionFormat.format((value * 100.0).roundToInt() / 100.0)
        }
    } else {
        decimalFormat.format(value)
    }
}

fun formatStockName(name: String) = if (name.length > 6) name.take(6) + ".." else name

fun formatDate(date: String): String {
    return if (date.length >= 4 && (date.startsWith("20") || date.startsWith("19"))) {
        date.substring(2)
    } else {
        date
    }
}
