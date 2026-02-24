package com.adb.controller.adb

import android.content.Context
import android.util.Log
import dadb.AdbStream
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ADB 连接管理器
 * 使用 Dadb 库实现 ADB over TCP/IP 连接
 */
class AdbManager(private val context: Context) {

    companion object {
        private const val TAG = "AdbManager"
        private const val SCRCPY_SERVER_PATH = "/data/local/tmp/scrcpy-server"
    }

    private var dadb: Dadb? = null

    val isConnected: Boolean get() = dadb != null

    /**
     * 连接到远程设备的 ADB 守护进程
     */
    suspend fun connect(host: String, port: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            disconnect()
            val connection = Dadb.create(host, port)
            // 测试连接
            val response = connection.shell("getprop ro.product.model")
            val deviceName = response.output.trim().ifEmpty { "$host:$port" }
            dadb = connection
            Log.i(TAG, "已连接到设备: $deviceName ($host:$port)")
            Result.success(deviceName)
        } catch (e: Exception) {
            Log.e(TAG, "连接失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 断开 ADB 连接
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        try {
            dadb?.close()
        } catch (e: Exception) {
            Log.w(TAG, "断开连接时出错: ${e.message}")
        } finally {
            dadb = null
        }
    }

    /**
     * 测试设备是否可达
     */
    suspend fun testConnection(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val testDadb = Dadb.create(host, port)
            val response = testDadb.shell("echo ok")
            val ok = response.output.trim() == "ok"
            testDadb.close()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "连接测试失败: ${e.message}")
            false
        }
    }

    /**
     * 推送 scrcpy-server 到被控设备
     */
    suspend fun pushScrcpyServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            val d = dadb ?: return@withContext false

            // 从 assets 复制到本地缓存
            val tempFile = File(context.cacheDir, "scrcpy-server")
            context.assets.open("scrcpy-server").use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 推送到设备
            d.push(tempFile, SCRCPY_SERVER_PATH, 0b110100100, System.currentTimeMillis())
            tempFile.delete()

            Log.i(TAG, "scrcpy-server 已推送到设备")
            true
        } catch (e: Exception) {
            Log.e(TAG, "推送 scrcpy-server 失败: ${e.message}", e)
            false
        }
    }

    /**
     * 启动 scrcpy-server（非阻塞，返回用于保持进程的 ADB 流）
     */
    fun startScrcpyServer(
        maxSize: Int = 1920,
        bitRate: Int = 8_000_000,
        maxFps: Int = 60
    ): AdbStream? {
        return try {
            val d = dadb ?: return null
            val command = "CLASSPATH=$SCRCPY_SERVER_PATH " +
                    "app_process / com.genymobile.scrcpy.Server 2.7 " +
                    "tunnel_forward=true " +
                    "video_codec=h264 " +
                    "audio=false " +
                    "control=true " +
                    "max_size=$maxSize " +
                    "video_bit_rate=$bitRate " +
                    "max_fps=$maxFps " +
                    "send_device_meta=true " +
                    "send_frame_meta=true " +
                    "send_dummy_byte=true " +
                    "send_codec_meta=true"

            Log.i(TAG, "启动 scrcpy-server: $command")
            d.open("shell:$command")
        } catch (e: Exception) {
            Log.e(TAG, "启动 scrcpy-server 失败: ${e.message}", e)
            null
        }
    }

    /**
     * 打开到 scrcpy-server 的流连接
     */
    fun openScrcpyStream(): AdbStream? {
        return try {
            dadb?.open("localabstract:scrcpy")
        } catch (e: Exception) {
            Log.e(TAG, "连接 scrcpy 流失败: ${e.message}", e)
            null
        }
    }

    /**
     * 获取设备屏幕尺寸
     */
    suspend fun getScreenSize(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val response = dadb?.shell("wm size") ?: return@withContext null
            val match = Regex("(\\d+)x(\\d+)").find(response.output)
            if (match != null) {
                Pair(match.groupValues[1].toInt(), match.groupValues[2].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
