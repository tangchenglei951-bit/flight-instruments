## 当前版本锁定（重要）

- 当前锁定版本：versionCode = 4，versionName = 1.0.3
- 在用户明确要求解锁并发布新版本之前，**不再递增版本号、不再推送触发新构建、不再发布新 APK**
- 如需发布新版本，必须先得到用户明确同意，再执行 `./bump-version.ps1`
# 发布与版本规则

## 版本号规则（强约束）

- 每次对外发布新的 APK，必须先把 `gradle.properties` 中的 `appVersionCode` 加 1。
- 每次发布同时更新 `appVersionName`（最后一位 +1）。
- **版本号没有增加的构建，不允许作为更新发布。**
- 可以使用 `./bump-version.ps1` 一键递增版本号，然后再执行构建。

## 构建方式

1. 递增版本号：`./bump-version.ps1`
2. 提交并推送代码，GitHub Actions 会自动在线编译。
3. 从构建产物下载新的 `app-debug.apk`。

## 说明

`appVersionCode` 与 `appVersionName` 定义在根目录 `gradle.properties`，
由 `app/build.gradle.kts` 读取并写入 Android 的 versionCode / versionName。
