# AGENTS.md — SilentUpgrade

本文件为 AI Agent 提供项目上下文、编码约束与协作指南。

## 项目概述

SilentUpgrade 是一款 Android 静默升级应用，使用系统级 `PackageInstaller` API 安装 APK，无需用户确认。

- **应用 ID**: `com.le.lhkj.silentupgrade`
- **技术栈**: Kotlin、Jetpack Compose、Android PackageInstaller API
- **入口**: [MainActivity.kt](app/src/main/java/com/le/lhkj/silentupgrade/MainActivity.kt)
- **核心安装逻辑**: [SilentInstaller.kt](app/src/main/java/com/le/lhkj/silentupgrade/install/SilentInstaller.kt)
- **UI**: [FirmwareInstallScreen.kt](app/src/main/java/com/le/lhkj/silentupgrade/FirmwareInstallScreen.kt)

## 运行前提

该应用必须具备系统权限或 platform 签名才能执行静默安装：

- `AndroidManifest.xml` 声明 `android:sharedUserId="android.uid.system"`
- 声明 `INSTALL_PACKAGES` 等受保护权限
- 使用对应设备平台的 platform 签名进行打包

## 构建配置

项目配置了两个 `productFlavor`，每个 Flavor 对应不同的 platform 签名：

| 渠道 | Gradle 任务 | 签名配置 | Keystore |
|---|---|---|---|
| factoryPad | `assembleFactoryPadRelease/Debug` | factoryPadPlatform | [app/platform.jks](app/platform.jks) |
| commonPad | `assembleCommonPadRelease/Debug` | commonPadPlatform | [app/common_pad_platform.keystore](app/common_pad_platform.keystore) |

快捷打包脚本：[build_apk.sh](build_apk.sh)

```bash
./build_apk.sh factory release
./build_apk.sh factory debug
./build_apk.sh common release
./build_apk.sh common debug
./build_apk.sh all
```

打包后的 APK 会复制到 `outputs/` 目录。

## 代码结构

```
app/src/main/java/com/le/lhkj/silentupgrade/
├── MainActivity.kt                 # 入口 Activity，读取 intent 参数并启动安装
├── FirmwareInstallScreen.kt        # Jetpack Compose 安装进度界面
├── install/
│   ├── SilentInstaller.kt          # 静默安装核心实现（PackageInstaller）
│   ├── InstallResultReceiver.kt    # 安装结果广播接收器（独立类）
│   ├── InstallResult.kt            # 安装结果密封类
│   └── FirmwareInstallState.kt     # 安装状态枚举
├── utils/
│   └── Logger.kt                   # 日志封装（Timber + Log）
└── ui/theme/
    ├── Color.kt
    ├── Theme.kt
    └── Type.kt
```

## 核心约束与约定

修改以下部分时务必保持约束：

1. **静默安装模式**: 必须使用 `PackageInstaller.SessionParams.MODE_FULL_INSTALL`。
2. **SilentInstaller 方法拆分**: `createSession`、`copyInstallFile`、`execInstallCommand` 必须是独立方法。
3. **APK 拷贝缓冲**: 缓冲区大小固定为 `65536` 字节。
4. **PendingIntent requestCode**: 安装提交时固定为 `1`。
5. **FirmwareInstallState**: 仅包含 `title`、`description`、`iconRes` 和派生属性 `isInstalling`，不添加其他字段。
6. **InstallResultReceiver**: 必须是独立类，避免持有 `SilentInstaller` 强引用。
7. **安装参数**: `SilentInstaller.install()` 当前仅需 `apkPath` 与 `pkgName`，`appName` 仅作为广播附加信息保留。
8. **常量归属**: 启动 `MainActivity` 的公开 intent extras 定义在 `MainActivity` companion object 中；安装结果广播的私有 extras 定义在 `SilentInstaller` 内部。

## 安装流程

1. **PREPARING_PACKAGE**: 校验 APK 文件存在且非空。
2. **VERIFYING_FILE**: 解析 APK 包信息，校验包名一致且版本号高于已安装版本。
3. **INSTALLING_NEW_VERSION**: 创建 PackageInstaller Session，拷贝 APK 文件流，提交安装命令。
4. **STARTING_NEW_VERSION**: 安装成功后启动目标应用。
5. **FAILED**: 任一环节失败时进入失败状态。

## 调用方式

外部通过启动 `MainActivity` 并携带以下 extras 触发安装：

```kotlin
intent.putExtra(MainActivity.EXTRA_APK_PATH, "/path/to/app.apk")
intent.putExtra(MainActivity.EXTRA_PKG_NAME, "com.example.app")
intent.putExtra(MainActivity.EXTRA_APP_NAME, "示例应用") // 可选
```

## 修改建议

- 若需调整界面文案/图标，修改 [FirmwareInstallState.kt](app/src/main/java/com/le/lhkj/silentupgrade/install/FirmwareInstallState.kt) 与对应 drawable 资源。
- 若需新增安装结果处理分支，在 [InstallResult.kt](app/src/main/java/com/le/lhkj/silentupgrade/install/InstallResult.kt) 与 `mapStatusToResult` 中扩展。
- 若需修改进度映射或安装步骤，注意保持 `FirmwareInstallState` 字段精简，不要引入 `progressStart`、`progressEnd`、`showRetry`、`stepNumber` 等冗余字段。
- 不要在 `SilentInstaller` 与 `InstallResultReceiver` 之间引入强引用或额外数据类包装安装信息。
