package com.le.lhkj.silentupgrade.utils

import android.util.Log
import timber.log.Timber

/**
 * 日志
 */
object Logger {

    @JvmStatic
    fun logDebug(tag: String, text: String) {
        Timber.d("$tag---$text")
        Log.d(tag, "日志：$text")
    }

    @JvmStatic
    fun logError(tag: String, text: String) {
        Timber.d("$tag---$text")
        Log.e(tag, "日志：$text")
    }

    @JvmStatic
    fun logDebug(text: String) {
        Timber.d(text)
        Log.d("默认日志", "日志：$text")
    }
}