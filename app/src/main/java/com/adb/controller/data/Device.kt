package com.adb.controller.data

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val host: String,
    val port: Int = 5555
)
