package com.le.lhkj.silentupgrade

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.le.lhkj.silentupgrade.install.FirmwareInstallState
import com.le.lhkj.silentupgrade.install.InstallResult
import com.le.lhkj.silentupgrade.install.SilentInstaller
import com.le.lhkj.silentupgrade.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * MVI 中的 ViewModel
 *
 * 持有 [SilentInstaller]，将安装回调转换为不可变的 [InstallUiState]，
 * 并通过 [handleIntent] 接收来自 View 的意图。
 */
class InstallViewModel(application: Application) : AndroidViewModel(application) {

    private val silentInstaller = SilentInstaller(application)

    private val _uiState = MutableStateFlow(InstallUiState())
    val uiState: StateFlow<InstallUiState> = _uiState.asStateFlow()

    init {
        silentInstaller.setCallback(object : SilentInstaller.InstallCallback {
            override fun onStateChanged(state: FirmwareInstallState) {
                _uiState.update { it.copy(state = state) }
            }

            override fun onProgressChanged(value: Int) {
                _uiState.update { it.copy(progress = value) }
            }

            override fun onInstallResult(result: InstallResult) {
                if (result is InstallResult.Failure) {
                    Logger.logError(TAG, "Install result: ${result::class.java.simpleName}, ${result.message}")
                } else {
                    Logger.logDebug(TAG, "Install result: success")
                }
                _uiState.update { it.copy(result = result) }
            }
        })
    }

    /**
     * 处理来自 View 的意图。
     */
    fun handleIntent(intent: InstallIntent) {
        when (intent) {
            is InstallIntent.StartInstall -> startInstallIfNeeded(intent)
        }
    }

    private fun startInstallIfNeeded(intent: InstallIntent.StartInstall) {
        val current = _uiState.value
        // 避免配置变化或重复 intent 导致重复安装
        if (current.state != FirmwareInstallState.PREPARING_PACKAGE || current.progress != 0) {
            Logger.logDebug(TAG, "Install already started or completed, skip")
            return
        }
        silentInstaller.install(intent.apkPath, intent.pkgName, intent.appName)
    }

    override fun onCleared() {
        super.onCleared()
        silentInstaller.release()
    }

    companion object {
        private const val TAG = "InstallViewModel"
    }
}
