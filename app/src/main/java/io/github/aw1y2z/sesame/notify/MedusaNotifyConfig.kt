package io.github.aw1y2z.sesame.notify

import io.github.aw1y2z.sesame.data.AppConfig

/** 模块 App 的通知进程可能早于界面启动；只加载一次共享 AppConfig。 */
object MedusaNotifyConfig {
    @Volatile
    private var loaded = false

    fun ensure() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                AppConfig.load()
                loaded = true
            }
        }
    }
}
