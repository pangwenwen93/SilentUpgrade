package com.le.lhkj.silentupgrade.install

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.le.lhkj.silentupgrade.utils.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 静默安装器
 *
 * 通过 [PackageInstaller] 实现 APK 静默安装，并在安装过程中回调
 * [FirmwareInstallState] 与进度，供界面展示。
 *
 * 使用前提：应用需具备 platform 签名或系统权限，并声明 [INSTALL_PACKAGES]。
 */
class SilentInstaller(context: Context) {

    interface InstallCallback {
        /** 安装阶段变化 */
        fun onStateChanged(state: FirmwareInstallState)

        /** 安装进度变化，范围 0..100 */
        fun onProgressChanged(value: Int)

        /** PackageInstaller 最终安装结果 */
        fun onInstallResult(result: InstallResult)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private var callback: InstallCallback? = null
    private var pendingDeferred: CompletableDeferred<InstallResult>? = null

    private val resultReceiver = InstallResultReceiver { status, message ->
        val result = mapStatusToResult(status, message)
        pendingDeferred?.complete(result)
    }

    init {
        val filter = IntentFilter(InstallResultReceiver.ACTION_INSTALL_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(resultReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                appContext,
                resultReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    fun setCallback(callback: InstallCallback) {
        this.callback = callback
    }

    /**
     * 开始静默安装
     *
     * @param apkPath 待安装 APK 的绝对路径
     * @param pkgName 期望安装 APK 的包名，校验时会与 APK 实际包名比对
     * @param appName 应用名称，用于安装结果广播附加信息
     */
    fun install(apkPath: String, pkgName: String, appName: String? = null) {
        scope.launch {
            try {
                emitState(FirmwareInstallState.PREPARING_PACKAGE)
                emitProgress(0)

                val apkFile = File(apkPath)
                requireApkExists(apkFile)
                emitProgress(5)

                // 2. 校验文件
                emitState(FirmwareInstallState.VERIFYING_FILE)
                emitProgress(10)
                val packageInfo = verifyApk(apkFile, pkgName)
                val versionCode = getVersionCode(packageInfo)
                val versionName = packageInfo.versionName
                emitProgress(20)

                // 3. 安装新版本
                emitState(FirmwareInstallState.INSTALLING_NEW_VERSION)
                val packageInstaller = appContext.packageManager.packageInstaller
                val sessionParams = createSessionParams(apkFile)
                val sessionId = createSession(packageInstaller, sessionParams)

                if (sessionId == -1) {
                    emitInstallResult(InstallResult.Failure.Generic("create session failed"))
                    emitState(FirmwareInstallState.FAILED)
                    return@launch
                }

                val copySuccess = copyInstallFile(
                    packageInstaller,
                    sessionId,
                    apkFile
                ) { innerProgress ->
                    // 将内部 0..100 映射到 20..90
                    emitProgress(20 + innerProgress * 70 / 100)
                }
                if (!copySuccess) {
                    emitInstallResult(InstallResult.Failure.Generic("copy install file failed"))
                    emitState(FirmwareInstallState.FAILED)
                    return@launch
                }

                val result = execInstallCommand(
                    pkgName = pkgName,
                    appName = appName,
                    versionName = versionName,
                    versionCode = versionCode,
                    packageInstaller = packageInstaller,
                    sessionId = sessionId
                )

                if (result.isSuccess) {
                    // 4. 启动新版本
                    emitState(FirmwareInstallState.STARTING_NEW_VERSION)
                    emitProgress(95)
                    startInstalledApp(pkgName)
                    delay(800)
                    emitProgress(100)
                    Logger.logDebug(TAG, "Install completed successfully")
                } else {
                    emitState(FirmwareInstallState.FAILED)
                }
                emitInstallResult(result)

            } catch (e: Exception) {
                Logger.logError(TAG, "Install failed: ${e.message}")
                emitState(FirmwareInstallState.FAILED)
                emitInstallResult(InstallResult.Failure.Generic(e.message))
            }
        }
    }

    private fun requireApkExists(file: File) {
        if (!file.exists() || !file.isFile) {
            throw IllegalArgumentException("APK file not found: ${file.absolutePath}")
        }
    }

    private fun verifyApk(file: File, expectedPkgName: String): PackageInfo {
        if (file.length() <= 0) {
            throw IllegalArgumentException("APK file is empty")
        }
        if (expectedPkgName.isBlank()) {
            throw IllegalArgumentException("Expected package name is empty")
        }

        val packageInfo = getPackageArchiveInfo(file)
            ?: throw IllegalArgumentException("Cannot parse package info from APK")
        val actualPkgName = packageInfo.packageName
            ?: throw IllegalArgumentException("Cannot parse package name from APK")

        if (actualPkgName != expectedPkgName) {
            throw IllegalArgumentException(
                "Package name mismatch: expected=$expectedPkgName, actual=$actualPkgName"
            )
        }

        // 版本号校验：待安装版本必须大于已安装版本
        val newVersionCode = getVersionCode(packageInfo)
        val installedVersionCode = getInstalledVersionCode(expectedPkgName)
        if (installedVersionCode != null && newVersionCode <= installedVersionCode) {
            throw IllegalArgumentException(
                "Version code must be greater than installed version: " +
                        "installed=$installedVersionCode, new=$newVersionCode"
            )
        }

        Logger.logDebug(
            TAG,
            "verifyApk success: $actualPkgName, newVersion=$newVersionCode, installedVersion=$installedVersionCode"
        )
        return packageInfo
    }

    private fun getPackageArchiveInfo(file: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            appContext.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_PERMISSIONS
            )
        }
    }

    private fun getInstalledVersionCode(pkgName: String): Long? {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.packageManager.getPackageInfo(
                    pkgName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                appContext.packageManager.getPackageInfo(pkgName, 0)
            }
            getVersionCode(info)
        } catch (e: PackageManager.NameNotFoundException) {
            // 未安装时允许安装（首次安装场景）
            null
        }
    }

    private fun getVersionCode(packageInfo: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    private fun createSessionParams(apkFile: File): PackageInstaller.SessionParams {
        return PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        ).apply {
            setSize(apkFile.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
    }

    private fun createSession(
        packageInstaller: PackageInstaller,
        sessionParams: PackageInstaller.SessionParams
    ): Int {
        return try {
            packageInstaller.createSession(sessionParams)
        } catch (e: IOException) {
            Logger.logError(TAG, "createSession failed: ${e.message}")
            -1
        }
    }

    @SuppressLint("RequestInstallPackagesPolicy")
    private fun copyInstallFile(
        packageInstaller: PackageInstaller,
        sessionId: Int,
        apkFile: File,
        onProgress: ((Int) -> Unit)? = null
    ): Boolean {
        var input: InputStream? = null
        var output: OutputStream? = null
        var session: PackageInstaller.Session? = null
        var success = false
        try {
            session = packageInstaller.openSession(sessionId)
            output = session.openWrite(NAME_APK, 0, apkFile.length())
            input = FileInputStream(apkFile)

            var total = 0
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                total += read
                val progress = (total * 100 / apkFile.length()).toInt()
                onProgress?.invoke(progress)
            }
            session.fsync(output)
            Logger.logDebug(TAG, "streamed $total bytes")
            success = true
        } catch (e: IOException) {
            Logger.logError(TAG, "copyInstallFile failed: ${e.message}")
        } finally {
            try {
                output?.close()
                input?.close()
                session?.close()
            } catch (e: IOException) {
                Logger.logError(TAG, "close stream failed: ${e.message}")
            }
        }
        return success
    }

    private suspend fun execInstallCommand(
        pkgName: String,
        appName: String?,
        versionName: String?,
        versionCode: Long?,
        packageInstaller: PackageInstaller,
        sessionId: Int
    ): InstallResult {
        var session: PackageInstaller.Session? = null
        return try {
            session = packageInstaller.openSession(sessionId)

            val deferred = CompletableDeferred<InstallResult>()
            pendingDeferred = deferred

            val intent = Intent(InstallResultReceiver.ACTION_INSTALL_RESULT).apply {
                setPackage(appContext.packageName)
                putExtra(EXTRA_APP_PKG_NAME, pkgName + "")
                putExtra(EXTRA_APP_VERSION_CODE, versionCode.toString() + "")
                putExtra(EXTRA_APP_VERSION_NAME, versionName + "")
                putExtra(EXTRA_APP_NAME, appName + "")
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(appContext, REQUEST_CODE, intent, flags)

            session.commit(pendingIntent.intentSender)
            deferred.await()
        } catch (e: Exception) {
            Logger.logError(TAG, "execInstallCommand failed: ${e.message}")
            InstallResult.Failure.Generic(e.message)
        } finally {
            pendingDeferred = null
            try {
                session?.close()
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    /**
     * 启动已安装的应用。
     * 安装成功后调用，跳转到目标应用的入口 Activity。
     */
    private fun startInstalledApp(pkgName: String) {
        try {
            val launchIntent = appContext.packageManager.getLaunchIntentForPackage(pkgName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                appContext.startActivity(launchIntent)
                Logger.logDebug(TAG, "Started app: $pkgName")
            } else {
                Logger.logError(TAG, "No launch intent for package: $pkgName")
            }
        } catch (e: Exception) {
            Logger.logError(TAG, "Failed to start app: ${e.message}")
        }
    }

    private fun emitState(state: FirmwareInstallState) {
        mainHandler.post { callback?.onStateChanged(state) }
    }

    private fun emitProgress(progress: Int) {
        val validProgress = progress.coerceIn(0, 100)
        mainHandler.post { callback?.onProgressChanged(validProgress) }
    }

    private fun emitInstallResult(result: InstallResult) {
        mainHandler.post { callback?.onInstallResult(result) }
    }

    fun release() {
        job.cancel()
        try {
            appContext.unregisterReceiver(resultReceiver)
        } catch (_: Exception) {
            // ignore
        }
    }

    companion object {
        private const val TAG = "SilentInstaller"
        private const val BUFFER_SIZE = 65536
        private const val NAME_APK = "base.apk"
        private const val REQUEST_CODE = 1

        private const val EXTRA_APP_PKG_NAME = "appPkgName"
        private const val EXTRA_APP_VERSION_CODE = "appVersionCode"
        private const val EXTRA_APP_VERSION_NAME = "appVersionName"
        private const val EXTRA_APP_NAME = "appName"
    }
}
