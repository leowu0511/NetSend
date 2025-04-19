package com.leowu.netsend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

class PacketSender {
    sealed class SendResult {
        data class Success(val message: String) : SendResult()
        data class Error(val message: String) : SendResult()
    }

    enum class SendType {
        RAW_SOCKET, HTTP, HTTPS
    }

    enum class HttpMethod {
        GET, POST
    }

    suspend fun sendTcpPacket(ipAddress: String, port: Int, data: String): SendResult {
        return withContext(Dispatchers.IO) {
            val socket = Socket()
            try {
                // 設置連接超時為 5 秒
                socket.connect(InetSocketAddress(ipAddress, port), 5000)
                
                // 設置數據傳輸超時為 5 秒
                socket.soTimeout = 5000
                
                // 發送數據
                val writer = OutputStreamWriter(socket.getOutputStream())
                writer.write(data)
                writer.flush()

                // 讀取回應
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val response = StringBuilder()
                var line: String?
                
                try {
                    // 嘗試讀取回應，最多等待 5 秒
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line).append("\n")
                        // 如果回應太長或沒有明確結束，我們只讀取一定量的數據
                        if (response.length > 4096) break
                    }
                } catch (e: Exception) {
                    // 如果無法讀取回應，可能是因為沒有回應或者連接已關閉
                    if (response.isEmpty()) {
                        response.append("數據已發送，但沒有收到回應")
                    }
                }
                
                SendResult.Success("成功連線並發送資料\n\n回應：\n${response.toString().trim()}")
            } catch (e: Exception) {
                SendResult.Error("發送失敗：${e.message}")
            } finally {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // 關閉 socket 時發生錯誤，忽略
                }
            }
        }
    }
    
    suspend fun sendHttpRequest(
        url: String, 
        method: HttpMethod, 
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): SendResult {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = method.name
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                // 設置請求頭
                headers.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }
                
                // 發送請求體
                if (method == HttpMethod.POST && !body.isNullOrEmpty()) {
                    connection.doOutput = true
                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(body)
                        writer.flush()
                    }
                }
                
                // 獲取回應
                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                
                val inputStream = if (responseCode >= 400) {
                    connection.errorStream
                } else {
                    connection.inputStream
                }
                
                val response = StringBuilder()
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line).append("\n")
                    }
                }
                
                SendResult.Success("HTTP $responseCode: $responseMessage\n\n${response.toString().trim()}")
            } catch (e: Exception) {
                SendResult.Error("HTTP 請求失敗：${e.message}")
            }
        }
    }
} 