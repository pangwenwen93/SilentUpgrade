package com.le.lhkj.silentupgrade

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.le.lhkj.silentupgrade.install.FirmwareInstallState
import com.le.lhkj.silentupgrade.install.InstallResult
import com.le.lhkj.silentupgrade.install.SilentInstaller
import com.le.lhkj.silentupgrade.ui.theme.SilentUpgradeTheme
import com.le.lhkj.silentupgrade.utils.Logger

class MainActivity : ComponentActivity() {

    private lateinit var silentInstaller: SilentInstaller

    companion object {
        private const val TAG = "PH_MainActivity"

        const val EXTRA_APK_PATH = "extra_apk_path"
        const val EXTRA_PKG_NAME = "extra_pkg_name"
        const val EXTRA_APP_NAME = "extra_app_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemBars()
        setStatusBarDisabled()

        silentInstaller = SilentInstaller(this)

        setContent {
            SilentUpgradeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var installState by remember {
                        mutableStateOf(FirmwareInstallState.PREPARING_PACKAGE)
                    }
                    var progress by remember { mutableIntStateOf(0) }

                    LaunchedEffect(Unit) {
                        silentInstaller.setCallback(object : SilentInstaller.InstallCallback {
                            override fun onStateChanged(state: FirmwareInstallState) {
                                installState = state
                            }

                            override fun onProgressChanged(value: Int) {
                                progress = value
                            }

                            override fun onInstallResult(result: InstallResult) {
                                if (result is InstallResult.Failure) {
                                    Logger.logError(TAG, "Install result: ${result::class.java.simpleName}, ${result.message}")
                                } else {
                                    Logger.logDebug(TAG, "Install result: success")
                                }
                            }
                        })
                    }

                    FirmwareInstallScreen(
                        state = installState,
                        progress = progress,
                    )
                }
            }
        }

        val apkPath = intent.getStringExtra(EXTRA_APK_PATH)
        val pkgName = intent.getStringExtra(EXTRA_PKG_NAME)
        val appName = intent.getStringExtra(EXTRA_APP_NAME)
        if (!apkPath.isNullOrEmpty() && !pkgName.isNullOrEmpty()) {
            silentInstaller.install(apkPath, pkgName, appName)
        } else {
            Logger.logError(TAG, "No apk path or package name provided")
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        // 设置沉浸式模式行为（粘性沉浸模式）
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // 隐藏状态栏和导航栏
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())

        // 允许布局扩展到系统栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (::silentInstaller.isInitialized) {
            silentInstaller.release()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
    }

    private fun setStatusBarDisabled() {
        try {
            val statusBarManager = getSystemService("statusbar") ?: return
            val clazz = Class.forName("android.app.StatusBarManager")
            val method = clazz.getMethod("disable", Int::class.java)

            val flags =
                clazz.getField("DISABLE_HOME").getInt(null) or
                        clazz.getField("DISABLE_RECENT").getInt(null) or
                        clazz.getField("DISABLE_BACK").getInt(null) or
                        clazz.getField("DISABLE_EXPAND").getInt(null) or
                        clazz.getField("DISABLE_NOTIFICATION_ICONS").getInt(null) or
                        clazz.getField("DISABLE_NOTIFICATION_ALERTS").getInt(null)

            method.invoke(statusBarManager, flags)
            Logger.logDebug(TAG, "StatusBarManager disable true")
        } catch (e: Exception) {
            Logger.logError(TAG, "StatusBarManager disable failed: ${e.message}")
        }
    }
}