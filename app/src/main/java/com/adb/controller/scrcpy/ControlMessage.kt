package com.adb.controller.scrcpy

import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Scrcpy 控制消息协议实现
 * 参考 scrcpy v2.x 的二进制协议格式
 */
object ControlMessage {

    // 消息类型
    const val TYPE_INJECT_KEYCODE = 0
    const val TYPE_INJECT_TEXT = 1
    const val TYPE_INJECT_TOUCH_EVENT = 2
    const val TYPE_INJECT_SCROLL_EVENT = 3
    const val TYPE_BACK_OR_SCREEN_ON = 4
    const val TYPE_SET_SCREEN_POWER_MODE = 10

    // 触摸动作
    const val ACTION_DOWN = 0
    const val ACTION_UP = 1
    const val ACTION_MOVE = 2

    // 按键动作
    const val KEY_ACTION_DOWN = 0
    const val KEY_ACTION_UP = 1

    // Android KeyEvent 常量
    const val KEYCODE_HOME = 3
    const val KEYCODE_BACK = 4
    const val KEYCODE_APP_SWITCH = 187
    const val KEYCODE_VOLUME_UP = 24
    const val KEYCODE_VOLUME_DOWN = 25
    const val KEYCODE_POWER = 26

    /**
     * 注入触摸事件
     */
    fun injectTouch(
        output: OutputStream,
        action: Int,
        pointerId: Long,
        x: Int,
        y: Int,
        screenWidth: Int,
        screenHeight: Int,
        pressure: Float = if (action == ACTION_UP) 0f else 1f
    ) {
        val buf = ByteBuffer.allocate(32)
        buf.put(TYPE_INJECT_TOUCH_EVENT.toByte())
        buf.put(action.toByte())
        buf.putLong(pointerId)
        buf.putInt(x)
        buf.putInt(y)
        buf.putShort(screenWidth.toShort())
        buf.putShort(screenHeight.toShort())
        // pressure: unsigned fixed-point 16-bit (0xFFFF = 1.0)
        buf.putShort((pressure * 0xFFFF).toInt().toShort())
        buf.putInt(1)  // action button (primary)
        buf.putInt(1)  // buttons (primary)
        output.write(buf.array(), 0, buf.position())
        output.flush()
    }

    /**
     * 注入按键事件
     */
    fun injectKeycode(
        output: OutputStream,
        action: Int,
        keycode: Int,
        repeat: Int = 0,
        metaState: Int = 0
    ) {
        val buf = ByteBuffer.allocate(14)
        buf.put(TYPE_INJECT_KEYCODE.toByte())
        buf.put(action.toByte())
        buf.putInt(keycode)
        buf.putInt(repeat)
        buf.putInt(metaState)
        output.write(buf.array(), 0, buf.position())
        output.flush()
    }

    /**
     * 发送返回键
     */
    fun pressBack(output: OutputStream) {
        injectKeycode(output, KEY_ACTION_DOWN, KEYCODE_BACK)
        injectKeycode(output, KEY_ACTION_UP, KEYCODE_BACK)
    }

    /**
     * 发送 Home 键
     */
    fun pressHome(output: OutputStream) {
        injectKeycode(output, KEY_ACTION_DOWN, KEYCODE_HOME)
        injectKeycode(output, KEY_ACTION_UP, KEYCODE_HOME)
    }

    /**
     * 发送最近任务键
     */
    fun pressRecent(output: OutputStream) {
        injectKeycode(output, KEY_ACTION_DOWN, KEYCODE_APP_SWITCH)
        injectKeycode(output, KEY_ACTION_UP, KEYCODE_APP_SWITCH)
    }

    /**
     * 注入文本输入
     */
    fun injectText(output: OutputStream, text: String) {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(5 + textBytes.size)
        buf.put(TYPE_INJECT_TEXT.toByte())
        buf.putInt(textBytes.size)
        buf.put(textBytes)
        output.write(buf.array(), 0, buf.position())
        output.flush()
    }
}
