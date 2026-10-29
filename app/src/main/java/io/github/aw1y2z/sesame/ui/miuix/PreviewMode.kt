package io.github.aw1y2z.sesame.ui.miuix

import android.content.Context
import android.content.Intent
import io.github.aw1y2z.sesame.BuildConfig
import io.github.aw1y2z.sesame.util.ToastUtil

/** UI 试验包：独立目录、独立界面；执行类指令一律丢弃，正式版和支付宝内的模块无感。 */
internal object PreviewMode {
    val enabled: Boolean = BuildConfig.UI_PREVIEW
}

/** 试验包与正式版同用户空间，必须拦住会真正触发执行的广播；其余广播照常。 */
internal fun Context.sendMedusaBroadcast(intent: Intent) {
    if (PreviewMode.enabled) {
        val action = intent.action.orEmpty()
        if (action.endsWith(".sesame.planRun") || action.endsWith(".sesame.planStop") ||
            action.endsWith(".sesame.restart") || action.endsWith(".sesame.reloadConfig")
        ) {
            ToastUtil.show(this, "UI 试验版：不会向支付宝发起执行或重启")
            return
        }
    }
    sendBroadcast(intent)
}
