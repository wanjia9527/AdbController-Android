package com.adb.controller.scrcpy

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import com.adb.controller.adb.AdbManager
import com.adb.controller.adb.AdbStreamReader
import dadb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scrcpy 客户端
 * 负责与 scrcpy-server 通信，解码视频流并转发控制事件
 */
class ScrcpyClient(private val adbManager: AdbManager) {

    companion object {
        private const val TAG = "ScrcpyClient"
        private const val DEVICE_NAME_LENGTH = 64
        private const val NO_PTS = -1L // 0xFFFFFFFFFFFFFFFF
    }

    // 状态
    private val running = AtomicBoolean(false)
    private var serverStream: AdbStream? = null
    private var videoStream: AdbStream? = null
    private var controlStream: AdbStream? = null
    private var decoder: MediaCodec? = null
    private var controlOutput: OutputStream? = null

    // 设备信息
    var deviceName: String = ""
        private set
    var videoWidth: Int = 0
        private set
    var videoHeight: Int = 0
        private set

    interface Callback {
        fun onConnected(deviceName: String, width: Int, height: Int)
        fun onDisconnected(error: String?)
        fun onVideoSizeChanged(width: Int, height: Int)
    }

    var callback: Callback? = null

    /**
     * 启动 scrcpy 会话
     */
    fun start(surface: Surface): Boolean {
        if (running.get()) return false

        try {
            // 1. 推送 scrcpy-server
            // （假设已在外部调用 adbManager.pushScrcpyServer()）

            // 2. 启动 scrcpy-server 进程
            Log.i(TAG, "启动 scrcpy-server...")
            serverStream = adbManager.startScrcpyServer()
            if (serverStream == null) {
                Log.e(TAG, "无法启动 scrcpy-server")
                return false
            }

            // 等待 server 初始化
            Thread.sleep(1500)

            // 3. 连接视频流
            Log.i(TAG, "连接视频流...")
            videoStream = adbManager.openScrcpyStream()
            if (videoStream == null) {
                Log.e(TAG, "无法连接视频流")
                stop()
                return false
            }

            // 4. 连接控制流
            Log.i(TAG, "连接控制流...")
            controlStream = adbManager.openScrcpyStream()

            // 5. 解析视频流头部
            val videoReader = AdbStreamReader { videoStream!!.read() }

            // 读取 dummy byte
            videoReader.readByte()
            Log.d(TAG, "收到 dummy byte")

            // 读取设备名称 (64 bytes)
            deviceName = videoReader.readString(DEVICE_NAME_LENGTH)
            Log.i(TAG, "设备名称: $deviceName")

            // 读取 codec id (4 bytes) - "h264" = 0x68323634
            val codecId = videoReader.readInt()
            Log.d(TAG, "Codec ID: ${Integer.toHexString(codecId)}")

            // 读取初始视频尺寸
            videoWidth = videoReader.readInt()
            videoHeight = videoReader.readInt()
            Log.i(TAG, "视频尺寸: ${videoWidth}x${videoHeight}")

            // 读取控制流的 dummy byte
            if (controlStream != null) {
                try {
                    val controlReader = AdbStreamReader { controlStream!!.read() }
                    controlReader.readByte()
                    Log.d(TAG, "控制流 dummy byte 已读取")
                } catch (e: Exception) {
                    Log.w(TAG, "读取控制流 dummy byte 失败: ${e.message}")
                }
            }

            // 6. 设置控制输出流
            controlOutput = AdbStreamOutputStream(controlStream!!)

            // 7. 设置 MediaCodec 解码器
            setupDecoder(surface)

            // 8. 通知连接成功
            callback?.onConnected(deviceName, videoWidth, videoHeight)

            // 9. 启动解码循环
            running.set(true)
            startDecodingLoop(videoReader)

            return true
        } catch (e: Exception) {
            Log.e(TAG, "启动失败: ${e.message}", e)
            stop()
            return false
        }
    }

    /**
     * 停止 scrcpy 会话
     */
    fun stop() {
        running.set(false)
        try { decoder?.stop() } catch (_: Exception) {}
        try { decoder?.release() } catch (_: Exception) {}
        try { controlStream?.close() } catch (_: Exception) {}
        try { videoStream?.close() } catch (_: Exception) {}
        try { serverStream?.close() } catch (_: Exception) {}
        decoder = null
        controlOutput = null
        controlStream = null
        videoStream = null
        serverStream = null
        callback?.onDisconnected(null)
    }

    // ==================== 触摸控制 ====================

    fun sendTouch(action: Int, x: Int, y: Int, screenW: Int, screenH: Int) {
        val output = controlOutput ?: return
        try {
            ControlMessage.injectTouch(output, action, -1L, x, y, screenW, screenH)
        } catch (e: Exception) {
            Log.e(TAG, "发送触摸事件失败: ${e.message}")
        }
    }

    fun sendBack() {
        val output = controlOutput ?: return
        try { ControlMessage.pressBack(output) } catch (e: Exception) {
            Log.e(TAG, "发送返回键失败: ${e.message}")
        }
    }

    fun sendHome() {
        val output = controlOutput ?: return
        try { ControlMessage.pressHome(output) } catch (e: Exception) {
            Log.e(TAG, "发送 Home 键失败: ${e.message}")
        }
    }

    fun sendRecent() {
        val output = controlOutput ?: return
        try { ControlMessage.pressRecent(output) } catch (e: Exception) {
            Log.e(TAG, "发送最近任务键失败: ${e.message}")
        }
    }

    // ==================== 内部方法 ====================

    private fun setupDecoder(surface: Surface) {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            videoWidth,
            videoHeight
        )
        decoder = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, surface, null, 0)
            start()
        }
        Log.i(TAG, "MediaCodec 解码器已启动")
    }

    private fun startDecodingLoop(reader: AdbStreamReader) {
        Thread({
            Log.i(TAG, "解码循环开始")
            try {
                while (running.get()) {
                    // 读取帧元数据
                    val pts = reader.readLong()
                    val packetSize = reader.readInt()

                    if (packetSize <= 0 || packetSize > 1024 * 1024 * 4) {
                        Log.w(TAG, "异常帧大小: $packetSize, 跳过")
                        continue
                    }

                    // 读取帧数据
                    val frameData = reader.readFully(packetSize)

                    // 判断是否为配置包（SPS/PPS）
                    val isConfig = (pts == NO_PTS || pts == -1L ||
                            pts == Long.MAX_VALUE ||
                            pts.toULong() == ULong.MAX_VALUE)

                    // 送入解码器
                    feedDecoder(frameData, if (isConfig) 0L else pts, isConfig)

                    // 渲染输出
                    drainDecoder()
                }
            } catch (e: Exception) {
                if (running.get()) {
                    Log.e(TAG, "解码循环出错: ${e.message}", e)
                    running.set(false)
                    callback?.onDisconnected(e.message)
                }
            }
            Log.i(TAG, "解码循环结束")
        }, "ScrcpyDecoder").start()
    }

    private fun feedDecoder(data: ByteArray, pts: Long, isConfig: Boolean) {
        val codec = decoder ?: return
        val inputIndex = codec.dequeueInputBuffer(10_000) // 10ms 超时
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
            inputBuffer.clear()
            inputBuffer.put(data)
            val flags = if (isConfig) MediaCodec.BUFFER_FLAG_CODEC_CONFIG else 0
            codec.queueInputBuffer(inputIndex, 0, data.size, pts, flags)
        }
    }

    private fun drainDecoder() {
        val codec = decoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 1000) // 1ms 超时
            when {
                outputIndex >= 0 -> {
                    // 渲染到 Surface
                    codec.releaseOutputBuffer(outputIndex, true)
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    val w = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                    val h = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
                    Log.i(TAG, "输出格式变更: ${w}x${h}")
                    if (w != videoWidth || h != videoHeight) {
                        videoWidth = w
                        videoHeight = h
                        callback?.onVideoSizeChanged(w, h)
                    }
                }
                else -> break // INFO_TRY_AGAIN_LATER
            }
        }
    }

    /**
     * 将 AdbStream 的 write 封装为 OutputStream
     */
    private class AdbStreamOutputStream(private val stream: AdbStream) : OutputStream() {
        override fun write(b: Int) {
            stream.write(byteArrayOf(b.toByte()))
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (off == 0 && len == b.size) {
                stream.write(b)
            } else {
                stream.write(b.copyOfRange(off, off + len))
            }
        }

        override fun flush() {
            // ADB stream 没有缓冲
        }
    }
}
