package com.leowu.netsend

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.leowu.netsend.ui.theme.NetSendTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// 設定數據類
data class MultiThreadSettings(
    val isEnabled: Boolean = false,
    val threadCount: Int = 1,
    val requestCount: Int = 1,
    val delayPerRequest: Int = 0
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NetSendTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    httpViewModel: HttpViewModel = viewModel(),
    networkViewModel: NetworkViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(0) }
    val httpState by httpViewModel.httpState.collectAsState()
    val networkState by networkViewModel.networkState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    // 設定狀態
    var showSettingsDialog by remember { mutableStateOf(false) }
    var multiThreadSettings by remember { mutableStateOf(MultiThreadSettings()) }

    val copyToClipboard: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("複製內容", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.height(80.dp)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { 
                    Text(
                        "HTTP 狀態",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                modifier = Modifier.height(80.dp)
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { 
                    Text(
                        "Socket 連接",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                modifier = Modifier.height(80.dp)
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { 
                    Text(
                        "JSON 傳送",
                        style = MaterialTheme.typography.titleLarge
                    ) 
                },
                modifier = Modifier.height(80.dp)
            )
        }

        when (selectedTab) {
            0 -> HttpStatusTab(
                httpState, 
                copyToClipboard, 
                multiThreadSettings,
                onShowSettings = { showSettingsDialog = true },
                onSendRequest = { url ->
                    httpViewModel.sendGetRequest(url, multiThreadSettings)
                }
            )
            1 -> SocketTestTab(networkState, copyToClipboard) { ip, port ->
                networkViewModel.checkSocket(ip, port)
            }
            2 -> FileUploadTab(networkState, copyToClipboard) { ip, port, content, multiThreadSettings ->
                networkViewModel.sendJsonData(ip, port, content, multiThreadSettings)
            }
        }
    }
    
    // 設定對話框
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("多線程設定") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 啟用多線程開關
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("啟用多線程")
                        Switch(
                            checked = multiThreadSettings.isEnabled,
                            onCheckedChange = { isEnabled ->
                                multiThreadSettings = multiThreadSettings.copy(isEnabled = isEnabled)
                            }
                        )
                    }
                    
                    // 如果啟用了多線程，顯示其他設定
                    if (multiThreadSettings.isEnabled) {
                        // 線程數
                        OutlinedTextField(
                            value = multiThreadSettings.threadCount.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { count ->
                                    if (count >= 1) {
                                        multiThreadSettings = multiThreadSettings.copy(threadCount = count)
                                    }
                                }
                            },
                            label = { Text("線程數") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // 發送次數
                        OutlinedTextField(
                            value = multiThreadSettings.requestCount.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { count ->
                                    if (count >= 1) {
                                        multiThreadSettings = multiThreadSettings.copy(requestCount = count)
                                    }
                                }
                            },
                            label = { Text("發送次數") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // 每次延遲
                        OutlinedTextField(
                            value = multiThreadSettings.delayPerRequest.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { delay ->
                                    if (delay >= 0) {
                                        multiThreadSettings = multiThreadSettings.copy(delayPerRequest = delay)
                                    }
                                }
                            },
                            label = { Text("每次延遲 (ms)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("確定")
                }
            }
        )
    }
}

@Composable
fun HttpStatusTab(
    httpState: HttpState,
    copyToClipboard: (String) -> Unit,
    multiThreadSettings: MultiThreadSettings,
    onShowSettings: () -> Unit,
    onSendRequest: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("輸入 URL") },
                modifier = Modifier
                    .weight(1f)
                    .height(65.dp),
                singleLine = true
            )
            
            IconButton(
                onClick = onShowSettings,
                modifier = Modifier
                    .height(65.dp)
                    .width(65.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "設定"
                )
            }
        }

        Button(
            onClick = { onSendRequest(url) },
            enabled = url.isNotEmpty() && !httpState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("發送 GET 請求")
        }

        when {
            httpState.isLoading -> {
                CircularProgressIndicator()
            }
            httpState.error != null -> {
                ErrorCard(httpState.error!!, copyToClipboard)
            }
            httpState.statusCode != null -> {
                ResponseCard(httpState.statusCode, copyToClipboard, httpState.multiThreadProgress)
            }
        }
    }
}

@Composable
fun SocketTestTab(
    networkState: NetworkState,
    copyToClipboard: (String) -> Unit,
    onTestSocket: (String, Int) -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("輸入 IP") },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(65.dp),
            singleLine = true
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("輸入 Port") },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(65.dp),
            singleLine = true
        )

        Button(
            onClick = { 
                port.toIntOrNull()?.let { portNum ->
                    onTestSocket(ip, portNum)
                }
            },
            enabled = ip.isNotEmpty() && port.isNotEmpty() && !networkState.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("測試連接")
        }

        when {
            networkState.isLoading -> {
                CircularProgressIndicator()
            }
            networkState.error != null -> {
                ErrorCard(networkState.error!!, copyToClipboard)
            }
            networkState.socketStatus != null -> {
                SuccessCard(networkState.socketStatus!!)
            }
        }
    }
}

@Composable
fun FileUploadTab(
    networkState: NetworkState,
    copyToClipboard: (String) -> Unit,
    onUploadFile: (String, Int, String, MultiThreadSettings) -> Unit
) {
    var ip by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var multiThreadSettings by remember { mutableStateOf(MultiThreadSettings()) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = ip,
            onValueChange = { ip = it },
            label = { Text("輸入 IP") },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(65.dp),
            singleLine = true
        )
        
        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("輸入 Port") },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(65.dp),
            singleLine = true
        )
        
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("輸入 JSON 內容") },
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(200.dp),
            minLines = 5,
            placeholder = {
                Text(
                    text = """例如：
{
    "message": "Hello",
    "data": {
        "key": "value"
    }
}""".trimIndent()
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showSettingsDialog = true },
                modifier = Modifier
                    .weight(0.3f)
                    .height(65.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "設定"
                )
            }
        
        Button(
                onClick = { 
                    port.toIntOrNull()?.let { portNum ->
                        onUploadFile(ip, portNum, content, multiThreadSettings)
                    }
                },
                enabled = ip.isNotEmpty() && port.isNotEmpty() && content.isNotEmpty() && !networkState.isLoading,
                modifier = Modifier
                    .weight(0.7f)
                    .height(65.dp)
            ) {
                Text("上傳 JSON")
            }
        }

        when {
            networkState.isLoading -> {
                CircularProgressIndicator()
            }
            networkState.error != null -> {
                ErrorCard(networkState.error!!, copyToClipboard)
            }
            networkState.uploadStatus != null -> {
                if (networkState.multiThreadProgress != null) {
                    ResponseCard(
                        statusCode = if (networkState.multiThreadProgress.successCount > 0) 200 else 500,
                        copyToClipboard = copyToClipboard,
                        multiThreadProgress = networkState.multiThreadProgress
                    )
                } else {
                    SuccessCard(networkState.uploadStatus!!)
                }
            }
        }
    }

    // 設定對話框
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("多線程設定") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 啟用多線程開關
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("啟用多線程")
                        Switch(
                            checked = multiThreadSettings.isEnabled,
                            onCheckedChange = { isEnabled ->
                                multiThreadSettings = multiThreadSettings.copy(isEnabled = isEnabled)
                            }
                        )
                    }
                    
                    // 如果啟用了多線程，顯示其他設定
                    if (multiThreadSettings.isEnabled) {
                        // 線程數
                        OutlinedTextField(
                            value = multiThreadSettings.threadCount.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { count ->
                                    if (count >= 1) {
                                        multiThreadSettings = multiThreadSettings.copy(threadCount = count)
                                    }
                                }
                            },
                            label = { Text("線程數") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // 發送次數
                        OutlinedTextField(
                            value = multiThreadSettings.requestCount.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { count ->
                                    if (count >= 1) {
                                        multiThreadSettings = multiThreadSettings.copy(requestCount = count)
                                    }
                                }
                            },
                            label = { Text("發送次數") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        // 每次延遲
                        OutlinedTextField(
                            value = multiThreadSettings.delayPerRequest.toString(),
                            onValueChange = { value ->
                                value.toIntOrNull()?.let { delay ->
                                    if (delay >= 0) {
                                        multiThreadSettings = multiThreadSettings.copy(delayPerRequest = delay)
                                    }
                                }
                            },
                            label = { Text("每次延遲 (ms)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("確定")
                }
            }
        )
    }
}

@Composable
fun ErrorCard(error: String, copyToClipboard: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "錯誤信息",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                IconButton(onClick = { copyToClipboard(error) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "複製錯誤信息",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun ResponseCard(statusCode: Int, copyToClipboard: (String) -> Unit, multiThreadProgress: MultiThreadProgress? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "狀態碼: $statusCode",
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = { copyToClipboard(statusCode.toString()) }) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "複製狀態碼",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (multiThreadProgress != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "多線程進度",
                    style = MaterialTheme.typography.titleMedium
                )
                
                LinearProgressIndicator(
                    progress = { multiThreadProgress.completedRequests.toFloat() / multiThreadProgress.totalRequests },
                modifier = Modifier
                    .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "完成: ${multiThreadProgress.completedRequests}/${multiThreadProgress.totalRequests}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "成功: ${multiThreadProgress.successCount} 失敗: ${multiThreadProgress.failureCount}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = when (statusCode) {
                        200 -> "成功"
                        404 -> "找不到頁面"
                        500 -> "伺服器錯誤"
                        else -> "未知狀態"
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun SuccessCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}