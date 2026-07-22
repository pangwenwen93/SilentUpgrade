package com.le.lhkj.silentupgrade.install

import androidx.annotation.DrawableRes
import com.le.lhkj.silentupgrade.R

/**
 * 固件安装界面状态枚举
 *
 * 将原三种状态扩展为五种：
 * 1. 准备升级包
 * 2. 校验文件
 * 3. 安装新版本
 * 4. 启动新版本
 * 5. 失败
 */
enum class FirmwareInstallState(
    /** 阶段标题 */
    val title: String,
    /** 界面展示文案 */
    val description: String,
    /** 状态图标 */
    @get:DrawableRes val iconRes: Int,
) {
    PREPARING_PACKAGE(
        title = "准备升级包",
        description = "正在准备升级包...",
        iconRes = R.drawable.hardware_installing,
    ),
    VERIFYING_FILE(
        title = "校验文件",
        description = "正在校验文件...",
        iconRes = R.drawable.hardware_installing,
    ),
    INSTALLING_NEW_VERSION(
        title = "安装新版本",
        description = "正在安装新版本...",
        iconRes = R.drawable.hardware_installing,
    ),
    STARTING_NEW_VERSION(
        title = "版本升级成功，即将启动",
        description = "启动中...",
        iconRes = R.drawable.hardware_installing,
    ),
    FAILED(
        title = "失败",
        description = "安装更新包失败，请稍后重新升级",
        iconRes = R.drawable.hardware_install_fail,
    );

    /** 当前状态是否属于安装流程中（非失败） */
    val isInstalling: Boolean
        get() = this != FAILED
}
