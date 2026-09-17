# Flight Instruments (原生 Android 版)

由原 Qt 工程 `qtAndroid` 移植而来的 Kotlin 原生 Android 工程。

## 功能对照

| 原 Qt 模块 | 本工程实现 |
|---|---|
| QtSensors 九种传感器 | `SensorHub` + `SensorManager` |
| QtPositioning GPS/卫星 | `GpsHub` + `SatelliteHub` |
| QtNetwork 网速测试 | `NetworkSpeedTester` |
| QTextToSpeech 语音 | `SpeechHelper` |
| 腾讯逆地理编码 + SQLite 缓存 | `ReverseGeocoder` |
| CPU 压测/频率 | `CpuMonitor` |
| 行驶数据统计 | `TripRecorder` |
| qfi 仪表盘 | `FlightInstrumentView`（Canvas 多仪表：PFD/NAV 可滑动切换，含 ASI/ADI/ALT/TC/HSI/VSI） |

## 编译步骤

1. 使用 Android Studio（建议 Hedgehog 或更新版本）打开本目录 `android-native`。
2. 首次同步时让 Gradle 下载依赖（AGP 8.7.3 / Kotlin 2.1.0 / Gradle 8.9）。
3. 连接 Android 7.0（API 24）及以上真机，或创建模拟器。
4. 点击 Run 构建并安装。

> 当前本机没有 Android SDK，未做实际编译验证；如 Android Studio 提示版本兼容问题，
> 可在 `app/build.gradle.kts` 中把 `compileSdk/targetSdk` 与 AGP/Kotlin 版本调整为本机已安装版本。

## 运行说明

- 首次启动会请求定位权限，用于 GPS 和卫星信息。
- 底部四个按钮与旧版一致：CPU 测试、网络测试、传感器开关、仪表/文本/调试模式切换。
- 腾讯逆地理 Key 已写入 `ReverseGeocoder.kt`，如提示鉴权失败请替换为新 Key。

## 后续可完善项

- 将 `FlightInstrumentView` 从简化 Canvas 绘制升级为完整 PFD/NAV/ADI 等仪表。
- 增加 WGS84 -> GCJ02 坐标转换（当前直接使用 GPS 原始坐标请求逆地理）。
- 网速测试 URL 可替换为更稳定的国内测速地址。
