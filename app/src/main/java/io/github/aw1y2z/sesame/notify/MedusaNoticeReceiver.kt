package io.github.aw1y2z.sesame.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.util.Log
import io.github.aw1y2z.sesame.util.NoticeText

/** 宿主发来的结构化通知事件；前台走气泡，后台走模块 App 的系统通知。 */
class MedusaNoticeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            MedusaNotifyConfig.ensure()
            when (intent.getStringExtra("kind")) {
                "RUN" -> {
                    val ongoing = intent.getBooleanExtra("ongoing", false)
                    MedusaNotifier.postRun(
                        context,
                        NoticeText.clean(intent.getStringExtra("title")),
                        NoticeText.clean(intent.getStringExtra("detail")),
                        NoticeText.clean(intent.getStringExtra("big")),
                        ongoing
                    )
                }
                "EVENT" -> {
                    val type = intent.getStringExtra("type") ?: MedusaNotifier.TYPE_DONE
                    val title = NoticeText.clean(intent.getStringExtra("title"))
                    val detail = NoticeText.clean(intent.getStringExtra("detail"))
                    val big = NoticeText.clean(intent.getStringExtra("big"))
                    val page = intent.getStringExtra("page") ?: ""
                    val bubble = intent.getBooleanExtra("bubble", true)
                            && (AppConfig.INSTANCE.showToast ?: true)
                    // P0 风控必须落到系统通知，避免用户离开 App 后错过；普通结果前台走气泡。
                    if (bubble && MedusaNoticeCenter.foreground && type != MedusaNotifier.TYPE_RISK) {
                        MedusaNoticeCenter.push(
                            MedusaNoticeCenter.Notice(type, title, detail)
                        )
                    } else {
                        MedusaNotifier.postEvent(context, type, title, detail, big, page)
                    }
                }
                "CANCEL" -> MedusaNotifier.cancelStatus(context)
                "BUBBLE" -> {
                    // 普通操作提示：只在前台显示白色气泡；不在前台就丢弃，不拉起 App、不发系统通知。
                    if (MedusaNoticeCenter.foreground) {
                        MedusaNoticeCenter.pushToast(NoticeText.clean(intent.getStringExtra("detail")))
                    }
                }
            }
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }
}
