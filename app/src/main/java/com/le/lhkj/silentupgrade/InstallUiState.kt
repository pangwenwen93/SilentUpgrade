package com.le.lhkj.silentupgrade

import com.le.lhkj.silentupgrade.install.FirmwareInstallState
import com.le.lhkj.silentupgrade.install.InstallResult

/**
 * MVI 中的 Model（界面状态）
 *
 * 包含安装阶段、进度以及最终结果，是 Compose 界面的唯一数据源。
 */
data class InstallUiState(
    val state: FirmwareInstallState = FirmwareInstallState.PREPARING_PACKAGE,
    val progress: Int = 0,
    val result: InstallResult? = null,
)
