package io.github.aw1y2z.sesame.notify

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** App 前台时的轻提示队列；与系统通知共用同一套四类语义。 */
object MedusaNoticeCenter {

    const val TYPE_TOAST = "TOAST"

    data class Notice(
        val type: String,
        val title: String,
        val detail: String,
        val id: Long = System.nanoTime()
    )

    @Volatile
    var foreground: Boolean = false

    private var activeActivities = 0

    @JvmStatic
    fun onActivityResumed() {
        activeActivities++
        foreground = true
    }

    @JvmStatic
    fun onActivityPaused() {
        activeActivities = (activeActivities - 1).coerceAtLeast(0)
        foreground = activeActivities > 0
    }

    var current by mutableStateOf<Notice?>(null)

    private val queue = ArrayDeque<Notice>()

    @JvmStatic
    fun isForeground(): Boolean = foreground

    /** 普通操作提示走与通知相同的白色气泡，不再弹 Android 原生黑底 Toast。 */
    @JvmStatic
    fun pushToast(text: CharSequence?) {
        val clean = text?.toString()?.trim().orEmpty()
        if (clean.isEmpty()) return
        push(Notice(TYPE_TOAST, "", clean))
    }

    fun push(notice: Notice) {
        if (current == null) {
            current = notice
            return
        }
        if (queue.size >= 3) {
            queue.removeLast()
        }
        queue.addLast(notice)
    }

    fun dismiss() {
        current = if (queue.isEmpty()) null else queue.removeFirst()
    }

    fun clear() {
        queue.clear()
        current = null
    }
}
