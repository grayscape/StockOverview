package com.gsc.stockoverview.data.api

import android.util.Log
import com.gsc.stockoverview.data.entity.StockEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

/**
 * 네이버 증권 API를 사용하여 국내 종목 정보를 조회하는 서비스 클래스
 */
class NaverStockApiService {

    /**
     * 종목명으로 국내 종목 정보를 검색하여 StockEntity를 반환합니다.
     * (매매일지의 종목명 -> 종목코드 변환 및 상세 정보 조회)
     */
    fun fetchDomesticStockInfo(stockName: String): StockEntity? {
        val searchResult = searchDomesticStockCode(stockName) ?: return null
        val stockCode = searchResult.first
        val marketType = searchResult.second
        
        return fetchDomesticStockDetails(stockCode, marketType)
    }

    /**
     * 네이버 금시세 API를 사용하여 국내 금 시세(원/g)를 조회합니다.
     * POST 방식으로 변경되었으며, Payload에 reutersCodes를 포함해야 합니다.
     */
    fun fetchGoldPrice(): Double {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://m.stock.naver.com/front-api/realTime/marketIndex/metals"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            // JSON Payload 설정
            val payload = "{\"reutersCodes\":[\"M04020000\"]}"
            connection.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = readStream(connection, "UTF-8")
                val json = JSONObject(response)
                
                // result가 JSONArray가 아닌 JSONObject임
                val result = json.optJSONObject("result")
                val metals = result?.optJSONObject("metals")
                
                // reutersCodes로 요청한 "M04020000" 키를 사용하여 데이터를 가져옴
                val goldData = metals?.optJSONObject("M04020000")
                
                if (goldData != null) {
                    val priceStr = goldData.optString("closePrice", "0")
                    return priceStr.replace(",", "").toDoubleOrNull() ?: 0.0
                }
            }
            0.0
        } catch (e: Exception) {
            Log.e("NaverStockApiService", "Error fetching gold price: ${e.message}")
            0.0
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 네이버 검색 API를 사용하여 원/달러 환율을 조회합니다.
     */
    fun fetchExchangeRate(): Double {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://ac.search.naver.com/nx/ac?q=%EB%8B%AC%EB%9F%AC%20%ED%99%98%EC%9C%A8&con=0&frm=nx&ans=2&r_format=json&r_enc=UTF-8&r_unicode=0&t_koreng=1&run=2&rev=4&q_enc=UTF-8&st=100&ackey=j21mjqpc&_callback=_jsonp_11"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                var response = readStream(connection, "UTF-8")
                
                // JSONP 응답 처리 (예: _jsonp_11({...}) 에서 괄호 안의 JSON만 추출)
                if (response.contains("(")) {
                    response = response.substringAfter("(").substringBeforeLast(")")
                }
                
                val json = JSONObject(response)
                val answerArray = json.optJSONArray("answer")
                if (answerArray != null && answerArray.length() > 0) {
                    // answer[0] 배열의 6번째 인덱스에 환율 정보가 있음 ("1,451.00")
                    val infoArray = answerArray.getJSONArray(0)
                    if (infoArray.length() > 6) {
                        val rateStr = infoArray.getString(6)
                        return rateStr.replace(",", "").toDoubleOrNull() ?: 0.0
                    }
                }
            }
            0.0
        } catch (e: Exception) {
            Log.e("NaverStockApiService", "Error fetching exchange rate: ${e.message}")
            0.0
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 네이버 자동완성 API를 사용하여 국내 종목 코드를 검색합니다.
     */
    private fun searchDomesticStockCode(stockName: String): Pair<String, String>? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://ac.stock.naver.com/ac?q=${URLEncoder.encode(stockName, "UTF-8")}&target=stock%2Cipo%2Cindex%2Cmarketindicator"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
            Log.e("NaverStockApiService", "Error searching domestic stock code: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 종목 코드를 사용하여 국내 주식 실시간 상세 정보를 조회합니다.
     */
    fun fetchDomesticStockDetails(stockCode: String, marketType: String = "KOREA"): StockEntity? {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://polling.finance.naver.com/api/realtime?query=SERVICE_ITEM:$stockCode"
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
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
                            val changeRate = data.optDouble("cr", 0.0)
                            
                            return StockEntity(
                                stockCode = stockCode,
                                stockName = name,
                                stockType = "KOREA",
                                currentPrice = currentPrice,
                                changeRate = changeRate,
                                marketType = marketType,
                                currency = "KRW"
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("NaverStockApiService", "Error fetching domestic stock details: ${e.message}")
            null
        } finally {
            connection?.disconnect()
        }
    }

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
