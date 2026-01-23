package com.gsc.stockoverview.data.api

import android.util.Log
import com.gsc.stockoverview.data.entity.StockEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * 네이버 증권 API를 사용하여 종목 정보를 조회하는 서비스 클래스
 */
class NaverStockApiService {

    /**
     * 종목명으로 종목 정보를 검색합니다.
     */
    fun fetchStockInfo(stockName: String): StockEntity? {
        val searchResult = searchStockCode(stockName) ?: return null
        val stockCode = searchResult.first
        val marketType = searchResult.second
        
        return fetchStockDetails(stockCode, marketType)
    }

    /**
     * 네이버 자동완성 API를 사용하여 종목 코드를 검색합니다.
     * 이 API는 종종 EUC-KR 인코딩을 사용하므로 처리가 필요합니다.
     */
    private fun searchStockCode(stockName: String): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://ac.stock.naver.com/ac?q=${URLEncoder.encode(stockName, "UTF-8")}&target=stock%2Cipo%2Cindex%2Cmarketindicator"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                // ac.stock.naver.com은 주로 EUC-KR을 사용하므로 기본값을 EUC-KR로 설정
                val response = readStream(connection, "EUC-KR")
                val json = JSONObject(response)
                val items = json.getJSONArray("items")
                
                if (items.length() > 0) {
                    val firstItem = items.getJSONObject(0)
                    val code = firstItem.getString("code")
                    val typeName = firstItem.optString("typeName", "KOREA")
                    Pair(code, typeName)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("NaverStockApiService", "Error searching stock code: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 종목 코드를 사용하여 실시간 상세 정보를 조회합니다.
     */
    private fun fetchStockDetails(stockCode: String, marketType: String): StockEntity? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://polling.finance.naver.com/api/realtime?query=SERVICE_ITEM:$stockCode"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                // 실시간 API는 보통 UTF-8을 사용함
                val response = readStream(connection, "UTF-8")
                val json = JSONObject(response)
                
                if (json.getString("resultCode") == "success") {
                    val result = json.getJSONObject("result")
                    val areas = result.getJSONArray("areas")
                    if (areas.length() > 0) {
                        val datas = areas.getJSONObject(0).getJSONArray("datas")
                        if (datas.length() > 0) {
                            val data = datas.getJSONObject(0)
                            val name = data.getString("nm")
                            val currentPrice = data.optDouble("nv", 0.0)
                            
                            StockEntity(
                                stockCode = stockCode,
                                stockName = name,
                                stockType = "KOREA",
                                currentPrice = currentPrice,
                                marketType = marketType,
                                currency = "KRW"
                            )
                        } else null
                    } else null
                } else null
            } else null
        } catch (e: Exception) {
            Log.e("NaverStockApiService", "Error fetching stock details: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * HttpURLConnection의 응답 스트림을 지정된 인코딩으로 읽습니다.
     * Content-Type 헤더에 charset이 명시되어 있으면 해당 인코딩을 우선 사용합니다.
     */
    private fun readStream(connection: HttpURLConnection, defaultCharset: String): String {
        val contentType = connection.contentType
        val charsetName = if (contentType != null && contentType.contains("charset=", ignoreCase = true)) {
            contentType.substringAfter("charset=").substringBefore(";").trim()
        } else {
            defaultCharset
        }
        
        return connection.inputStream.bufferedReader(Charset.forName(charsetName)).use { it.readText() }
    }
}
