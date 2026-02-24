package com.adb.controller.adb

import java.io.InputStream

/**
 * 对 Dadb AdbStream 的缓冲读取封装
 * ADB 协议中数据以消息帧传输，该类将帧拼接为连续字节流
 */
class AdbStreamReader(private val readBlock: () -> ByteArray) {

    private var buffer = ByteArray(0)
    private var position = 0

    /**
     * 读取指定数量的字节，会阻塞直到读满
     */
    fun readFully(count: Int): ByteArray {
        val result = ByteArray(count)
        var offset = 0
        while (offset < count) {
            if (position >= buffer.size) {
                buffer = readBlock()
                position = 0
                if (buffer.isEmpty()) {
                    throw java.io.EOFException("ADB stream ended unexpectedly")
                }
            }
            val available = buffer.size - position
            val toRead = minOf(available, count - offset)
            System.arraycopy(buffer, position, result, offset, toRead)
            position += toRead
            offset += toRead
        }
        return result
    }

    fun readByte(): Int {
        return readFully(1)[0].toInt() and 0xFF
    }

    fun readInt(): Int {
        val b = readFully(4)
        return ((b[0].toInt() and 0xFF) shl 24) or
                ((b[1].toInt() and 0xFF) shl 16) or
                ((b[2].toInt() and 0xFF) shl 8) or
                (b[3].toInt() and 0xFF)
    }

    fun readLong(): Long {
        val b = readFully(8)
        var result = 0L
        for (i in 0..7) {
            result = (result shl 8) or (b[i].toLong() and 0xFFL)
        }
        return result
    }

    fun readString(length: Int): String {
        val bytes = readFully(length)
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
    }
}
