package com.adb.controller.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devices")

class DeviceRepository(private val context: Context) {

    private val devicesKey = stringPreferencesKey("devices_list")
    private val json = Json { ignoreUnknownKeys = true }

    val devicesFlow: Flow<List<Device>> = context.dataStore.data.map { prefs ->
        val raw = prefs[devicesKey] ?: "[]"
        runCatching { json.decodeFromString<List<Device>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun addDevice(device: Device) {
        context.dataStore.edit { prefs ->
            val list = currentList(prefs).toMutableList()
            list.add(device)
            prefs[devicesKey] = json.encodeToString(list)
        }
    }

    suspend fun removeDevice(deviceId: String) {
        context.dataStore.edit { prefs ->
            val list = currentList(prefs).filter { it.id != deviceId }
            prefs[devicesKey] = json.encodeToString(list)
        }
    }

    suspend fun updateDevice(device: Device) {
        context.dataStore.edit { prefs ->
            val list = currentList(prefs).map { if (it.id == device.id) device else it }
            prefs[devicesKey] = json.encodeToString(list)
        }
    }

    private fun currentList(prefs: Preferences): List<Device> {
        val raw = prefs[devicesKey] ?: "[]"
        return runCatching { json.decodeFromString<List<Device>>(raw) }.getOrDefault(emptyList())
    }
}
