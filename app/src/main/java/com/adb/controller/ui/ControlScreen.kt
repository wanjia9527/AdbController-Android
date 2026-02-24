package com.adb.controller.ui

import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.adb.controller.scrcpy.ControlMessage
import com.adb.controller.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    viewModel: ControlViewModel,
    host: String,
    port: Int,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var surfaceReady by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.deviceName.ifEmpty { "$host:$port" },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = when (uiState.connectionState) {
                                ConnectionState.CONNECTING -> "连接中..."
                                ConnectionState.CONNECTED -> "${uiState.videoWidth}×${uiState.videoHeight}"
                                ConnectionState.DISCONNECTED -> "已断开"
                                ConnectionState.ERROR -> "连接错误"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.connectionState) {
                                ConnectionState.CONNECTED -> Online
                                ConnectionState.ERROR -> Offline
                                else -> TextSecondary
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardDark.copy(alpha = 0.9f)
                ),
                actions = {
                    // 连接状态指示灯
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (uiState.connectionState) {
                                    ConnectionState.CONNECTED -> Online
                                    ConnectionState.CONNECTING -> Connecting
                                    ConnectionState.ERROR -> Offline
                                    ConnectionState.DISCONNECTED -> TextHint
                                }
                            )
                    )
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState.connectionState) {
                ConnectionState.CONNECTING -> {
                    // 连接中
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在连接到 $host:$port ...", color = TextSecondary)
                    }
                }

                ConnectionState.ERROR -> {
                    // 错误
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Offline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "连接失败",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.errorMessage ?: "未知错误",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("返回")
                        }
                    }
                }

                ConnectionState.CONNECTED, ConnectionState.DISCONNECTED -> {
                    // 视频显示区域 + 控制栏
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 视频区域
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AndroidView(
                                factory = { context ->
                                    SurfaceView(context).apply {
                                        holder.addCallback(object : SurfaceHolder.Callback {
                                            override fun surfaceCreated(holder: SurfaceHolder) {
                                                surfaceReady = true
                                                viewModel.connect(host, port, holder.surface)
                                            }

                                            override fun surfaceChanged(
                                                holder: SurfaceHolder,
                                                format: Int,
                                                width: Int,
                                                height: Int
                                            ) {}

                                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                                surfaceReady = false
                                            }
                                        })

                                        // 触摸事件处理
                                        setOnTouchListener { v, event ->
                                            val state = viewModel.uiState.value
                                            if (state.videoWidth <= 0 || state.videoHeight <= 0) {
                                                return@setOnTouchListener false
                                            }

                                            // 坐标映射
                                            val scaleX =
                                                state.videoWidth.toFloat() / v.width.toFloat()
                                            val scaleY =
                                                state.videoHeight.toFloat() / v.height.toFloat()
                                            val remoteX = (event.x * scaleX).toInt()
                                                .coerceIn(0, state.videoWidth)
                                            val remoteY = (event.y * scaleY).toInt()
                                                .coerceIn(0, state.videoHeight)

                                            val action = when (event.action) {
                                                MotionEvent.ACTION_DOWN -> ControlMessage.ACTION_DOWN
                                                MotionEvent.ACTION_MOVE -> ControlMessage.ACTION_MOVE
                                                MotionEvent.ACTION_UP -> ControlMessage.ACTION_UP
                                                else -> return@setOnTouchListener false
                                            }

                                            viewModel.sendTouch(action, remoteX, remoteY)
                                            true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // 控制按钮栏
                        ControlBar(
                            onBack = { viewModel.sendBack() },
                            onHome = { viewModel.sendHome() },
                            onRecent = { viewModel.sendRecent() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlBar(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRecent: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, CardDark)
                )
            )
            .padding(horizontal = 48.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(icon = Icons.Default.ArrowBack, label = "返回", onClick = onBack)
        ControlButton(icon = Icons.Default.Circle, label = "主页", onClick = onHome)
        ControlButton(icon = Icons.Default.CropSquare, label = "最近", onClick = onRecent)
    }
}

@Composable
fun ControlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(CardDarkElevated)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
