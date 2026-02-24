package com.adb.controller.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adb.controller.adb.AdbManager
import com.adb.controller.data.Device
import com.adb.controller.data.DeviceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MainUiState(
    val devices: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val showAddDialog: Boolean = false,
    val snackbarMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceRepository(application)
    private val adbManager = AdbManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.devicesFlow.collect { devices ->
                _uiState.update { it.copy(devices = devices) }
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun addDevice(name: String, host: String, port: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val deviceName = name.ifBlank { "$host:$port" }
            val device = Device(name = deviceName, host = host, port = port)
            repository.addDevice(device)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    showAddDialog = false,
                    snackbarMessage = "设备 $deviceName 已添加"
                )
            }
        }
    }

    fun removeDevice(device: Device) {
        viewModelScope.launch {
            repository.removeDevice(device.id)
            _uiState.update { it.copy(snackbarMessage = "设备 ${device.name} 已删除") }
        }
    }

    fun testConnection(host: String, port: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = adbManager.testConnection(host, port)
            onResult(result)
        }
    }
}
