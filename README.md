# SilentUpgrade

Android 静默升级应用，支持按不同渠道签名打包。

## 签名配置

项目在 `app/build.gradle.kts` 中配置了两个 `signingConfig`，分别对应两个 `productFlavor`：

| 渠道（Flavor） | 签名配置 | Keystore 文件 |
|---|---|---|
| factoryPad | factoryPadPlatform | `app/platform.jks` |
| commonPad | commonPadPlatform | `app/common_pad_platform.keystore` |

`release` 与 `debug` 构建均会采用所属 `productFlavor` 对应的签名配置。

## 打包命令

项目根目录提供了 `build_apk.sh` 打包脚本，支持以下用法：

```bash
# factoryPad + release（platform.jks 签名）
./build_apk.sh factory release

# factoryPad + debug（platform.jks 签名）
./build_apk.sh factory debug

# commonPad + release（common_pad_platform.keystore 签名）
./build_apk.sh common release

# commonPad + debug（common_pad_platform.keystore 签名）
./build_apk.sh common debug

# 一次性打包全部 4 个包
./build_apk.sh all
```

打包完成后，APK 会复制到项目根目录的 `outputs/` 文件夹，并按以下格式命名：

```
outputs/SilentUpgrade_factoryPad_release.apk
outputs/SilentUpgrade_factoryPad_debug.apk
outputs/SilentUpgrade_commonPad_release.apk
outputs/SilentUpgrade_commonPad_debug.apk
```

## 直接调用 Gradle 打包

也可以不通过脚本，直接执行 Gradle 任务：

```bash
./gradlew assembleFactoryPadRelease
./gradlew assembleFactoryPadDebug
./gradlew assembleCommonPadRelease
./gradlew assembleCommonPadDebug
```

生成的 APK 默认位于 `app/build/outputs/apk/` 下对应子目录中。
