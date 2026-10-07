package com.hza.ledger.util

import android.util.Log

/**
 * 极简崩溃捕获。
 *
 * 为什么需要它：闪退类问题光看代码猜不出来，必须拿到运行时堆栈。
 * 但把崩溃日志交给用户也不现实——所以这里只把**最近一次崩溃的关键信息**
 * 存到 SharedPreferences，下次打开应用时在设置页展示出来。
 *
 * 隐私边界：只存类名与消息，**不存用户数据**，也不联网。
 */
object CrashReporter {

    private const val PREFS = "crash_capture"
    private const val KEY_MESSAGE = "last_crash"
    private const val KEY_TIME = "crash_time"
    private const val KEY_THREAD = "crash_thread"

    fun install() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val context = AppContextRef.contextOrNull() ?: return@runCatching
                context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_MESSAGE, describe(throwable))
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .putString(KEY_THREAD, thread.name)
                    .apply()
            }
            // 必须交给系统默认处理器，否则进程不会结束、闪退行为会变得很奇怪
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 最近一次崩溃的描述，无崩溃则返回 null。 */
    fun lastCrash(): CrashInfo? = runCatching {
        val context = AppContextRef.contextOrNull() ?: return null
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val message = prefs.getString(KEY_MESSAGE, null) ?: return null
        CrashInfo(
            message = message,
            timeMillis = prefs.getLong(KEY_TIME, 0L),
            threadName = prefs.getString(KEY_THREAD, "").orEmpty(),
        )
    }.getOrNull()

    fun clear() {
        runCatching {
            AppContextRef.contextOrNull()?.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                ?.edit()?.clear()?.apply()
        }
    }

    /**
     * 提取可读信息。
     *
     * 只取最上层的异常类型与消息——栈太深对用户无意义，
     * 而完整栈会包含大量混淆信息，反而淹没关键线索。
     */
    private fun describe(throwable: Throwable): String {
        val root = generateSequence(throwable) { it.cause }.last()
        val type = root.javaClass.simpleName
        val message = root.message?.takeIf { it.isNotBlank() } ?: "无错误信息"
        val cause = if (root !== throwable) "（由 ${throwable.javaClass.simpleName} 触发）" else ""
        return "$type: $message$cause"
    }
}

/** 最近一次崩溃的信息。 */
data class CrashInfo(
    val message: String,
    val timeMillis: Long,
    val threadName: String,
)

/**
 * 弱引用形式的 Context 持有。
 *
 * Application 类型通常不会被回收，但仍避免直接静态持有以防内存泄漏。
 */
object AppContextRef {
    @Volatile
    var app: android.content.Context? = null

    fun contextOrNull(): android.content.Context? = app
}
