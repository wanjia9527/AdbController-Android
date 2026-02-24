# ADB 远程控制 App

通过 ADB over TCP/IP 从一台安卓设备远程控制另一台安卓设备。

## 功能

- 📱 输入 IP 地址添加设备，支持连接测试
- 📋 主页设备列表管理（添加/删除）
- 🖥️ 远程实时屏幕投射（H.264 硬件解码）
- 👆 触摸事件转发（点击、滑动）
- 🔘 虚拟导航栏（返回、Home、最近任务）

## 技术栈

- **Kotlin** + **Jetpack Compose** + **Material3**
- **Dadb** — 纯 Kotlin ADB 协议库
- **scrcpy-server v2.7** — 屏幕采集与控制注入
- **MediaCodec** — 硬件加速 H.264 解码

## 编译

### GitHub Actions（推荐）

Push 到 GitHub 后会自动触发 CI 编译：
1. 创建 GitHub 仓库并推送代码
2. 进入 Actions 页面查看构建状态
3. 构建完成后在 Artifacts 中下载 APK

### 本地编译

```bash
# 1. 下载 scrcpy-server
mkdir -p app/src/main/assets
wget "https://github.com/Genymobile/scrcpy/releases/download/v2.7/scrcpy-server-v2.7" \
  -O app/src/main/assets/scrcpy-server

# 2. 编译
./gradlew assembleDebug
```

## 使用方法

### 被控设备准备

1. 进入 **设置 → 关于手机** → 连续点击版本号 7 次开启开发者选项
2. 进入 **开发者选项** → 开启 **USB 调试**
3. 通过 USB 连接到电脑，执行：
   ```bash
   adb tcpip 5555
   ```
4. 或在 Android 11+ 中直接开启 **无线调试**

### 控制端使用

1. 安装 APK 到控制端手机
2. 点击 ＋ 添加被控设备的 IP 和端口
3. 点击设备卡片进入远程控制

## 项目结构

```
app/src/main/java/com/adb/controller/
├── adb/            # ADB 连接管理
├── scrcpy/         # Scrcpy 客户端 & 控制协议
├── data/           # 数据模型 & 持久化
└── ui/             # Compose UI 页面
```

## 许可证

MIT
