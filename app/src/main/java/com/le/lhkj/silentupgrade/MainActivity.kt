package com.le.lhkj.silentupgrade

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.le.lhkj.silentupgrade.ui.theme.SilentUpgradeTheme
import com.le.lhkj.silentupgrade.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * MVI 中的 View
 *
 * 负责沉浸式/系统栏设置，观察 [InstallViewModel.uiState] 并渲染界面，
 * 将外部启动 intent 转换为 [InstallIntent.StartInstall] 交给 ViewModel 处理。
 */
class MainActivity : ComponentActivity() {

    private val viewModel: InstallViewModel by viewModels()

    companion object {
        private const val TAG = "PH_MainActivity"

        const val EXTRA_APK_PATH = "extra_apk_path"
        const val EXTRA_PKG_NAME = "extra_pkg_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        private const val DEFAULT_ASSET_APK = "app.apk"
        private const val UPGRADE_DIR = "upgrade"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.logDebug(TAG, "onCreate")

        hideSystemBars()
        setStatusBarDisabled()

        setContent {
            SilentUpgradeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                    FirmwareInstallScreen(uiState = uiState)
                }
            }
        }

        dispatchStartInstallFromIntent()
    }

    private fun dispatchStartInstallFromIntent() {
        val testMode = false
        lifecycleScope.launch {
            Logger.logDebug(TAG, "dispatchStartInstallFromIntent: testMode=$testMode")

            val apkPath = if (testMode) {
                copyAssetApkToLocal()?.absolutePath
            } else {
                intent.getStringExtra(EXTRA_APK_PATH)
            }

            val pkgName = if (testMode) {
                intent.getStringExtra(EXTRA_PKG_NAME).takeIf { !it.isNullOrEmpty() } ?: "com.le.lhkj.robot"
            } else {
                intent.getStringExtra(EXTRA_PKG_NAME)
            }

            val appName = if (testMode) {
                intent.getStringExtra(EXTRA_APP_NAME).takeIf { !it.isNullOrEmpty() } ?: "ROBOT"
            } else {
                intent.getStringExtra(EXTRA_APP_NAME).takeIf { !it.isNullOrEmpty() } ?: ""
            }

            Logger.logDebug(TAG, "resolved: apkPath=$apkPath, pkgName=$pkgName, appName=$appName")

            if (apkPath.isNullOrEmpty() || pkgName.isNullOrEmpty()) {
                Logger.logError(
                    TAG,
                    "Missing required params: " +
                            if (testMode) "no apk found in assets" else "EXTRA_APK_PATH or EXTRA_PKG_NAME is empty"
                )
                return@launch
            }

            viewModel.handleIntent(
                InstallIntent.StartInstall(apkPath, pkgName, appName)
            )
        }
    }

    private fun findAssetApkName(): String? {
        val allAssets = assets.list("")?.toList().orEmpty()
        Logger.logDebug(TAG, "assets list: $allAssets")
        val candidates = allAssets.filter { it.endsWith(".apk", ignoreCase = true) }
        Logger.logDebug(TAG, "apk candidates: $candidates")
        return candidates.firstOrNull { it.equals(DEFAULT_ASSET_APK, ignoreCase = true) }
            ?: candidates.firstOrNull()
    }

    private suspend fun copyAssetApkToLocal(): File? {
        val assetName = findAssetApkName() ?: return null
        val destFile = File(filesDir, "$UPGRADE_DIR/$assetName")
        return try {
            withContext(Dispatchers.IO) {
                destFile.parentFile?.mkdirs()
                assets.open(assetName).use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Logger.logDebug(TAG, "asset copied: ${destFile.absolutePath}, size=${destFile.length()}")
            destFile
        } catch (e: IOException) {
            Logger.logError(TAG, "copy asset failed: ${e.message}")
            null
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
        Logger.logDebug(TAG, "onDestroy")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
