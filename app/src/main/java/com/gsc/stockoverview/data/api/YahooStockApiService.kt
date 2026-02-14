package com.gsc.stockoverview.data.api

import android.util.Log
import com.gsc.stockoverview.data.entity.StockEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/**
 * Yahoo Finance API를 사용하여 해외 종목 정보를 조회하는 서비스 클래스
 */
class YahooStockApiService {

    /**
     * Yahoo Finance Chart API를 사용하여 해외 종목 상세 정보를 조회합니다.
     * URL: https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
     * 
     * @param stockCode 종목 코드 (심볼)
     * @param stockName 엑셀 등에서 가져온 종목명 (API에서 이름이 제공되지 않을 경우 사용)
     * @param currency 기본 통화
     */
    fun fetchOverseasStockDetails(stockCode: String, stockName: String, currency: String = "USD"): StockEntity? {
        val yahooSymbol = stockCode

        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooSymbol"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Yahoo Finance는 User-Agent 설정이 필수입니다.
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = readStream(connection, "UTF-8")
                val json = JSONObject(response)
                val chart = json.optJSONObject("chart")
                val resultArr = chart?.optJSONArray("result")
                
                if (resultArr != null && resultArr.length() > 0) {
                    val result = resultArr.getJSONObject(0)
                    val meta = result.optJSONObject("meta")
                    
                    if (meta != null) {
                        val currentPrice = meta.optDouble("regularMarketPrice", 0.0)
                        val previousClose = meta.optDouble("previousClose", 0.0)
                        val changeRate = if (previousClose > 0) {
                            ((currentPrice - previousClose) / previousClose) * 100
                        } else 0.0
                        
                        val currencyResult = meta.optString("currency", currency)
                        val market = meta.optString("fullExchangeName", "OVERSEAS")
                        
                        StockEntity(
                            stockCode = stockCode,
                            stockName = stockName, // 엑셀에서 가져온 종목명을 사용
                            stockType = "OVERSEAS",
                            currentPrice = currentPrice,
                            changeRate = changeRate,
                            marketType = market,
                            currency = currencyResult
                        )
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("YahooStockApiService", "Error fetching from Yahoo Finance Chart API: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readStream(connection: HttpURLConnection, defaultCharset: String): String {
        return try {
            connection.inputStream.bufferedReader(Charset.forName(defaultCharset)).use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader(Charset.forName(defaultCharset))?.use { it.readText() } ?: ""
        }
    }
}
