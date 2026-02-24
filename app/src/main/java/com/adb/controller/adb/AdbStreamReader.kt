package com.adb.controller.adb

import okio.BufferedSource
import okio.Source
import okio.buffer

/**
 * 对 Dadb AdbStream 的 okio Source 进行结构化读取封装
 */
class AdbStreamReader(source: Source) {

    private val buffered: BufferedSource = source.buffer()

    fun readFully(count: Int): ByteArray {
        return buffered.readByteArray(count.toLong())
    }

    fun readByte(): Int {
        return buffered.readByte().toInt() and 0xFF
    }

    /** 读取 4 字节大端序 Int */
    fun readInt(): Int {
        return buffered.readInt()
    }

    /** 读取 8 字节大端序 Long */
    fun readLong(): Long {
        return buffered.readLong()
    }

    fun readString(length: Int): String {
        val bytes = buffered.readByteArray(length.toLong())
        return String(bytes, Charsets.UTF_8).trimEnd('\u0000')
    }
}
