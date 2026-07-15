package com.le.lhkj.silentupgrade

/**
 * MVI 中的 Intent（用户/系统事件）
 *
 * 当前仅支持启动安装；后续如需支持重试、取消等操作可在此扩展。
 */
sealed class InstallIntent {
    data class StartInstall(
        val apkPath: String,
        val pkgName: String,
        val appName: String? = null,
    ) : InstallIntent()
}
