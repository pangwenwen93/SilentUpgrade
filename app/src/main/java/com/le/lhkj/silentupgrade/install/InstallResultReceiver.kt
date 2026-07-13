package com.le.lhkj.silentupgrade.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.le.lhkj.silentupgrade.utils.Logger

/**
 * PackageInstaller 安装结果广播接收器
 *
 * 解析安装结果状态、描述以及安装包附加信息，并通过回调传递给调用方。
 * 自身不持有 [SilentInstaller] 强引用，避免内存泄漏。
 */
class InstallResultReceiver(
    private val onResult: (Int, String?) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_INSTALL_RESULT) return

        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val pkgName = intent.getStringExtra(EXTRA_APP_PKG_NAME)
        val appName = intent.getStringExtra(EXTRA_APP_NAME)
        val versionCode = intent.getStringExtra(EXTRA_APP_VERSION_CODE)
        val versionName = intent.getStringExtra(EXTRA_APP_VERSION_NAME)

        Logger.logDebug(
            TAG,
            "Install result: status=$status, message=$message, " +
                    "pkgName=$pkgName, appName=$appName, versionCode=$versionCode, versionName=$versionName"
        )
        onResult(status, message)
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
        internal const val ACTION_INSTALL_RESULT =
            "com.le.lhkj.silentupgrade.install.action.INSTALL_RESULT"

        internal const val EXTRA_APP_PKG_NAME = "appPkgName"
        internal const val EXTRA_APP_VERSION_CODE = "appVersionCode"
        internal const val EXTRA_APP_VERSION_NAME = "appVersionName"
        internal const val EXTRA_APP_NAME = "appName"
    }
}
