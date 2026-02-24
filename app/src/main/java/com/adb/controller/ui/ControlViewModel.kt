package com.adb.controller.ui

import android.app.Application
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adb.controller.adb.AdbManager
import com.adb.controller.scrcpy.ScrcpyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ControlUiState(
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val deviceName: String = "",
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val errorMessage: String? = null
)

enum class ConnectionState {
    CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

class ControlViewModel(application: Application) : AndroidViewModel(application) {

    private val adbManager = AdbManager(application)
    private var scrcpyClient: ScrcpyClient? = null

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState: StateFlow<ControlUiState> = _uiState.asStateFlow()

    fun connect(host: String, port: Int, surface: Surface) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _uiState.update { it.copy(connectionState = ConnectionState.CONNECTING) }

                // 1. 连接 ADB
                val result = adbManager.connect(host, port)
                if (result.isFailure) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = "ADB 连接失败: ${result.exceptionOrNull()?.message}"
                        )
                    }
                    return@launch
                }

                // 2. 推送 scrcpy-server
                if (!adbManager.pushScrcpyServer()) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = "推送 scrcpy-server 失败"
                        )
                    }
                    return@launch
                }

                // 3. 启动 scrcpy 客户端
                val client = ScrcpyClient(adbManager)
                client.callback = object : ScrcpyClient.Callback {
                    override fun onConnected(deviceName: String, width: Int, height: Int) {
                        _uiState.update {
                            it.copy(
                                connectionState = ConnectionState.CONNECTED,
                                deviceName = deviceName,
                                videoWidth = width,
                                videoHeight = height
                            )
                        }
                    }

                    override fun onDisconnected(error: String?) {
                        _uiState.update {
                            it.copy(
                                connectionState = if (error != null) ConnectionState.ERROR else ConnectionState.DISCONNECTED,
                                errorMessage = error
                            )
                        }
                    }

                    override fun onVideoSizeChanged(width: Int, height: Int) {
                        _uiState.update { it.copy(videoWidth = width, videoHeight = height) }
                    }
                }

                scrcpyClient = client
                if (!client.start(surface)) {
                    _uiState.update {
                        it.copy(
                            connectionState = ConnectionState.ERROR,
                            errorMessage = "scrcpy 启动失败"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionState = ConnectionState.ERROR,
                        errorMessage = e.message ?: "未知错误"
                    )
                }
            }
        }
    }

    fun sendTouch(action: Int, x: Int, y: Int) {
        val state = _uiState.value
        scrcpyClient?.sendTouch(action, x, y, state.videoWidth, state.videoHeight)
    }

    fun sendBack() = scrcpyClient?.sendBack()
    fun sendHome() = scrcpyClient?.sendHome()
    fun sendRecent() = scrcpyClient?.sendRecent()

    fun disconnect() {
        scrcpyClient?.stop()
        scrcpyClient = null
        viewModelScope.launch(Dispatchers.IO) {
            adbManager.disconnect()
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}
