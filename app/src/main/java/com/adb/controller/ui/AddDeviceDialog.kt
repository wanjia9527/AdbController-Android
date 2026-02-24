package com.adb.controller.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.adb.controller.ui.theme.*

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, host: String, port: Int) -> Unit,
    onTestConnection: (host: String, port: Int, callback: (Boolean) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("5555") }
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = CardDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 标题
                Text(
                    text = "添加设备",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                // 设备名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("设备名称（可选）") },
                    placeholder = { Text("如: 我的手机") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Label, null, tint = Primary) },
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp)
                )

                // IP 地址
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        testResult = null
                    },
                    label = { Text("IP 地址 *") },
                    placeholder = { Text("192.168.1.100") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Wifi, null, tint = Primary) },
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )

                // 端口
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it.filter { c -> c.isDigit() }
                        testResult = null
                    },
                    label = { Text("端口") },
                    placeholder = { Text("5555") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.SettingsEthernet, null, tint = Primary) },
                    colors = textFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                // 测试连接结果
                testResult?.let { success ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (success) Online else Offline,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (success) "连接成功！" else "连接失败，请检查 IP 和端口",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) Online else Offline
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 测试连接
                    OutlinedButton(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 5555
                            isTesting = true
                            testResult = null
                            onTestConnection(host, port) { success ->
                                testResult = success
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = host.isNotBlank() && !isTesting,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedButtonDefaults.outlinedButtonColors(
                            contentColor = Accent
                        )
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Accent
                            )
                        } else {
                            Text("测试连接")
                        }
                    }

                    // 添加
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull() ?: 5555
                            onConfirm(name, host, port)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = host.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text("添加")
                    }
                }

                // 取消
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("取消", color = TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = TextHint,
    focusedLabelColor = Primary,
    unfocusedLabelColor = TextSecondary,
    cursorColor = Primary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedPlaceholderColor = TextHint,
    unfocusedPlaceholderColor = TextHint
)
