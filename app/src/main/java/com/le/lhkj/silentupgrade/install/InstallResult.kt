package com.le.lhkj.silentupgrade.install

import android.content.pm.PackageInstaller

/**
 * 安装结果密封类，覆盖 PackageInstaller 返回的全部状态
 */
sealed class InstallResult {
    abstract val message: String?

    data class Success(override val message: String? = null) : InstallResult()

    sealed class Failure : InstallResult() {
        data class Generic(override val message: String? = null) : Failure()
        data class Blocked(override val message: String? = null) : Failure()
        data class Aborted(override val message: String? = null) : Failure()
        data class Invalid(override val message: String? = null) : Failure()
        data class Conflict(override val message: String? = null) : Failure()
        data class Storage(override val message: String? = null) : Failure()
        data class Incompatible(override val message: String? = null) : Failure()
        data class Timeout(override val message: String? = null) : Failure()
    }

    val isSuccess: Boolean get() = this is Success
}

internal fun mapStatusToResult(
    status: Int,
    message: String?
): InstallResult {
    return when (status) {
        PackageInstaller.STATUS_SUCCESS -> InstallResult.Success(message)
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallResult.Failure.Blocked(message)
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult.Failure.Aborted(message)
        PackageInstaller.STATUS_FAILURE_INVALID -> InstallResult.Failure.Invalid(message)
        PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallResult.Failure.Conflict(message)
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallResult.Failure.Storage(message)
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallResult.Failure.Incompatible(message)
        PackageInstaller.STATUS_FAILURE_TIMEOUT -> InstallResult.Failure.Timeout(message)
        else -> InstallResult.Failure.Generic(message)
    }
}
