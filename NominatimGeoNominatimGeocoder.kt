package com.example.twaddresswatermark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object NominatimGeocoder {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // 需遵守 OSM 規則：帶上自訂 User-Agent，並設定 accept-language 為 zh-TW
    suspend fun getTaiwanAddress(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json&accept-language=zh-TW"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "TaiwanAddressWatermarkApp/1.0 (contact: your_email@example.com)")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext "地址解析失敗 (${response.code})"
                
                val body = response.body?.string() ?: return@withContext "無回應資料"
                val json = JSONObject(body)
                val addressObj = json.optJSONObject("address")

                if (addressObj != null) {
                    // 組合台灣常見地址格式：縣市 + 鄉鎮市區 + 道路 + 門牌
                    val city = addressObj.optString("city", addressObj.optString("county", ""))
                    val district = addressObj.optString("suburb", addressObj.optString("town", addressObj.optString("district", "")))
                    val road = addressObj.optString("road", "")
                    val houseNumber = addressObj.optString("house_number", "")
                    
                    val formatted = "$city$district$road$houseNumber".trim()
                    if (formatted.isNotEmpty()) {
                        return@withContext formatted
                    }
                }
                // 備案：直接回傳 OSM 預設的完整顯示名稱
                return@withContext json.optString("display_name", "未知地點")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "網路或地址解析異常"
        }
    }
}