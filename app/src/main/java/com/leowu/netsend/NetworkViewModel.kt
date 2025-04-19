package com.leowu.netsend

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NetworkState(
    val isLoading: Boolean = false,
    val socketStatus: String? = null,
    val uploadStatus: String? = null,
    val error: String? = null,
    val multiThreadProgress: MultiThreadProgress? = null
)

class NetworkViewModel : ViewModel() {
    private val _networkState = MutableStateFlow(NetworkState())
    val networkState: StateFlow<NetworkState> = _networkState
    
    // 原子計數器
    private val successCounter = AtomicInteger(0)
    private val failureCounter = AtomicInteger(0)
    private val completedCounter = AtomicInteger(0)
    
    // 用於同步的互斥鎖
    private val mutex = Mutex()

    fun checkSocket(ip: String, port: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _networkState.value = NetworkState(isLoading = true)
                Log.d("NetworkViewModel", "開始測試 Socket 連接：$ip:$port")

                withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                    Socket(ip, port).use { socket ->
                        socket.soTimeout = 5000
                        _networkState.value = NetworkState(
                            isLoading = false,
                            socketStatus = "連接成功"
                        )
                    }
                }
            } catch (e: UnknownHostException) {
                Log.e("NetworkViewModel", "主機名稱錯誤", e)
                _networkState.value = NetworkState(
                    isLoading = false,
                    error = "找不到主機：${e.message}"
                )
            } catch (e: Exception) {
                Log.e("NetworkViewModel", "Socket 連接失敗", e)
                _networkState.value = NetworkState(
                    isLoading = false,
                    error = "連接失敗：${e.message}"
                )
            }
        }
    }

    fun sendJsonData(ip: String, port: Int, content: String, settings: MultiThreadSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _networkState.value = NetworkState(isLoading = true)
                Log.d("NetworkViewModel", "開始發送 JSON 數據到：$ip:$port")

                val jsonContent = try {
                    JSONObject(content)
                } catch (e: Exception) {
                    JSONObject().apply {
                        put("message", content)
                        put("timestamp", System.currentTimeMillis())
                    }
                }

                if (settings.isEnabled) {
                    val totalRequests = settings.threadCount * settings.requestCount
                    successCounter.set(0)
                    failureCounter.set(0)
                    completedCounter.set(0)
                    
                    // 啟動進度更新協程
                    val progressJob = launch {
                        while (isActive) {
                            delay(100) // 每 100ms 更新一次進度
                            val completed = completedCounter.get()
                            _networkState.value = NetworkState(
                                isLoading = true,
                                multiThreadProgress = MultiThreadProgress(
                                    totalRequests = totalRequests,
                                    completedRequests = completed,
                                    successCount = successCounter.get(),
                                    failureCount = failureCounter.get()
                                )
                            )
                            if (completed >= totalRequests) break
                        }
                    }

                    // 使用協程作用域來管理所有線程
                    coroutineScope {
                        // 創建指定數量的線程
                        val jobs = List(settings.threadCount) { threadIndex ->
                            async(Dispatchers.IO) {
                                // 每個線程發送指定次數的請求
                                repeat(settings.requestCount) { requestIndex ->
                                    try {
                                        // 為每個請求創建新的 Socket 連接
                                        Socket(ip, port).use { socket ->
                                            socket.soTimeout = 5000
                                            
                                            // 準備 JSON 數據
                                            val requestJson = JSONObject(jsonContent.toString()).apply {
                                                put("timestamp", System.currentTimeMillis())
                                                put("threadIndex", threadIndex)
                                                put("requestIndex", requestIndex)
                                            }
                                            
                                            // 發送數據
                                            val writer = OutputStreamWriter(socket.getOutputStream())
                                            writer.write(requestJson.toString() + "\n")
                                            writer.flush()
                                            
                                            // 讀取回應
                                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                                            if (reader.readLine() != null) {
                                                successCounter.incrementAndGet()
                                            } else {
                                                failureCounter.incrementAndGet()
                                            }
                                        }
                                        
                                        // 增加完成計數
                                        completedCounter.incrementAndGet()
                                        
                                        // 如果需要延遲
                                        if (settings.delayPerRequest > 0) {
                                            delay(settings.delayPerRequest.toLong())
                                        }
                                    } catch (e: Exception) {
                                        failureCounter.incrementAndGet()
                                        completedCounter.incrementAndGet()
                                        Log.e("NetworkViewModel", "JSON 發送失敗: 線程 $threadIndex, 請求 $requestIndex", e)
                                    }
                                }
                            }
                        }
                        
                        // 等待所有線程完成
                        jobs.awaitAll()
                    }
                    
                    // 取消進度更新協程
                    progressJob.cancel()
                    
                    // 更新最終狀態
                    _networkState.value = NetworkState(
                        isLoading = false,
                        uploadStatus = "發送完成：成功 ${successCounter.get()} 次，失敗 ${failureCounter.get()} 次",
                        multiThreadProgress = MultiThreadProgress(
                            totalRequests = totalRequests,
                            completedRequests = totalRequests,
                            successCount = successCounter.get(),
                            failureCount = failureCounter.get()
                        )
                    )
                } else {
                    // 單線程模式
                    withTimeout(TimeUnit.SECONDS.toMillis(10)) {
                        Socket(ip, port).use { socket ->
                            socket.soTimeout = 5000
                            val writer = OutputStreamWriter(socket.getOutputStream())
                            writer.write(jsonContent.toString() + "\n")
                            writer.flush()
                            
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val response = reader.readLine()
                            
                            _networkState.value = NetworkState(
                                isLoading = false,
                                uploadStatus = if (response != null) "發送成功" else "發送失敗"
                            )
                        }
                    }
                }
            } catch (e: UnknownHostException) {
                Log.e("NetworkViewModel", "主機名稱錯誤", e)
                _networkState.value = NetworkState(
                    isLoading = false,
                    error = "找不到主機：${e.message}"
                )
            } catch (e: Exception) {
                Log.e("NetworkViewModel", "JSON 發送失敗", e)
                _networkState.value = NetworkState(
                    isLoading = false,
                    error = "發送失敗：${e.message}"
                )
            }
        }
    }
} 