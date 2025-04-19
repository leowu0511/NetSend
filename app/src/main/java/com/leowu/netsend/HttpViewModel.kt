package com.leowu.netsend

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class HttpState(
    val isLoading: Boolean = false,
    val statusCode: Int? = null,
    val error: String? = null,
    val multiThreadProgress: MultiThreadProgress? = null
)

data class MultiThreadProgress(
    val totalRequests: Int,
    val completedRequests: Int,
    val successCount: Int,
    val failureCount: Int
)

class HttpViewModel : ViewModel() {
    private val _httpState = MutableStateFlow(HttpState())
    val httpState: StateFlow<HttpState> = _httpState

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun sendHttpRequest(urlString: String, maxRedirects: Int = 5): Int {
        if (maxRedirects <= 0) {
            throw Exception("超過最大重定向次數")
        }

        val url = URL(urlString)
        
        // 如果是 HTTPS 請求，使用 OkHttp
        if (url.protocol == "https") {
            return sendHttpsRequest(urlString)
        }
        
        // 否則使用 Socket 發送 HTTP 請求
        val host = url.host
        val port = if (url.port == -1) 80 else url.port
        val path = if (url.path.isEmpty()) "/" else url.path
        val query = url.query

        val socket = Socket(host, port)
        socket.soTimeout = 10000 // 10秒超時

        try {
            val writer = OutputStreamWriter(socket.getOutputStream())
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            // 發送 HTTP 請求
            val requestPath = if (query != null) "$path?$query" else path
            writer.write("GET $requestPath HTTP/1.1\r\n")
            writer.write("Host: $host\r\n")
            writer.write("Connection: close\r\n")
            writer.write("\r\n")
            writer.flush()

            // 讀取回應
            var line: String?
            var statusCode = 0
            var isFirstLine = true
            var location: String? = null

            // 只讀取第一行獲取狀態碼
            line = reader.readLine()
            if (line != null) {
                statusCode = line.split(" ").getOrNull(1)?.toIntOrNull() ?: 0
            }

            return statusCode
        } finally {
            socket.close()
        }
    }
    
    private fun sendHttpsRequest(urlString: String): Int {
        val request = Request.Builder()
            .url(urlString)
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            return response.code
        }
    }

    fun sendGetRequest(urlString: String, settings: MultiThreadSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _httpState.value = HttpState(isLoading = true)
                Log.d("HttpViewModel", "開始發送 GET 請求到：$urlString")

                if (settings.isEnabled) {
                    // 多線程模式
                    val totalRequests = settings.threadCount * settings.requestCount
                    var successCount = 0
                    var failureCount = 0
                    
                    coroutineScope {
                        val jobs = (0 until settings.threadCount).map { threadIndex ->
                            async {
                                repeat(settings.requestCount) { requestIndex ->
                                    try {
                                        val statusCode = sendHttpRequest(urlString)
                                        if (statusCode in 200..299) {
                                            successCount++
                                        } else {
                                            failureCount++
                                        }
                                        
                                        // 更新進度
                                        val completedRequests = successCount + failureCount
                                        _httpState.value = HttpState(
                                            isLoading = true,
                                            multiThreadProgress = MultiThreadProgress(
                                                totalRequests = totalRequests,
                                                completedRequests = completedRequests,
                                                successCount = successCount,
                                                failureCount = failureCount
                                            )
                                        )
                                        
                                        // 延遲
                                        if (settings.delayPerRequest > 0) {
                                            delay(settings.delayPerRequest.toLong())
                                        }
                                    } catch (e: Exception) {
                                        failureCount++
                                        Log.e("HttpViewModel", "請求失敗", e)
                                    }
                                }
                            }
                        }
                        
                        // 等待所有線程完成
                        jobs.awaitAll()
                    }
                    
                    // 完成後更新狀態
                    _httpState.value = HttpState(
                        isLoading = false,
                        statusCode = if (successCount > 0) 200 else 500,
                        multiThreadProgress = MultiThreadProgress(
                            totalRequests = totalRequests,
                            completedRequests = totalRequests,
                            successCount = successCount,
                            failureCount = failureCount
                        )
                    )
                } else {
                    // 單線程模式
                    val statusCode = sendHttpRequest(urlString)
                    Log.d("HttpViewModel", "伺服器回應：$statusCode")

                    _httpState.value = HttpState(
                        isLoading = false,
                        statusCode = statusCode
                    )
                }
            } catch (e: SSLException) {
                Log.e("HttpViewModel", "SSL 錯誤", e)
                val errorMessage = if (e.message?.contains("BAD_DECRYPT", ignoreCase = true) == true || 
                                     e.message?.contains("DECRYPTION_FAILED", ignoreCase = true) == true) {
                    "SSL 錯誤：請確認是否使用正確的協定與 Port（HTTP:80 或 HTTPS:443）"
                } else {
                    "SSL 錯誤：${e.message}"
                }
                _httpState.value = HttpState(
                    isLoading = false,
                    error = errorMessage
                )
            } catch (e: UnknownHostException) {
                Log.e("HttpViewModel", "主機名稱錯誤", e)
                _httpState.value = HttpState(
                    isLoading = false,
                    error = "找不到主機：${e.message}"
                )
            } catch (e: Exception) {
                Log.e("HttpViewModel", "發送請求時發生錯誤", e)
                _httpState.value = HttpState(
                    isLoading = false,
                    error = "請求失敗：${e.message}"
                )
            }
        }
    }
} 