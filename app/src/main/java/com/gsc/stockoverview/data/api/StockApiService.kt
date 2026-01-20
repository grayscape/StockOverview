package com.gsc.stockoverview.data.api

import android.util.Log
import com.gsc.stockoverview.data.entity.StockEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StockApiService {
    private val apiKey = "d4f59a5b124cecd58b7cc87eb2bc7f4317ba658b647dc379ad0983ea2edf0709"
    private val baseUrl = "http://apis.data.go.kr/1160100/service/GetStockSecuritiesInfoService/getStockPriceInfo"

    fun fetchStockInfo(stockName: String): StockEntity? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "$baseUrl?serviceKey=$apiKey&resultType=json&itmsNm=${URLEncoder.encode(stockName, "UTF-8")}&numOfRows=1"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                parseStockResponse(response)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("StockApiService", "Error fetching stock info: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseStockResponse(jsonString: String): StockEntity? {
        return try {
            val root = JSONObject(jsonString)
            val body = root.getJSONObject("response").getJSONObject("body")
            val totalCount = body.getInt("totalCount")
            
            if (totalCount > 0) {
                val items = body.getJSONObject("items").getJSONArray("item")
                val item = items.getJSONObject(0)
                
                StockEntity(
                    stockCode = item.getString("srtnCd"), // 단축코드
                    stockName = item.getString("itmsNm"), // 종목명
                    stockType = "KOREA", // 한국 주식으로 가정
                    currentPrice = item.getString("clpr").toDoubleOrNull() ?: 0.0,
                    marketType = item.getString("mrktCtg"), // 시장구분 (KOSPI/KOSDAQ)
                    currency = "KRW"
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("StockApiService", "Error parsing stock response: ${e.message}")
            null
        }
    }
}
