package io.github.aw1y2z.sesame.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import io.github.aw1y2z.sesame.R
import io.github.aw1y2z.sesame.util.FileUtil
import io.github.aw1y2z.sesame.util.Log
import org.json.JSONObject
import java.io.File

/** 由模块 App（com.medusa.alipay）发通知，系统顶部才会显示“美杜莎”。 */
object MedusaNotifier {

    const val TYPE_RUN = "RUN"
    const val TYPE_DONE = "DONE"
    const val TYPE_ATTENTION = "ATTENTION"
    const val TYPE_RISK = "RISK"

    private const val CHANNEL_STATUS = "medusa_status"
    private const val CHANNEL_EVENT = "medusa_event"
    private const val CHANNEL_IMPORTANT = "medusa_important"
    private const val STATUS_ID = 99
    private const val EVENT_ID = 100
    private const val RISK_ID = 101
    private const val GROUP = "medusa_notices"
    private const val STATUS_FILE = "notice_status.json"

    private fun statusFile(): File = File(FileUtil.MAIN_DIRECTORY_FILE, STATUS_FILE)

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val status = NotificationChannel(
            CHANNEL_STATUS, "美杜莎运行状态", NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        val event = NotificationChannel(
            CHANNEL_EVENT, "美杜莎计划提醒", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(true)
            enableVibration(false)
        }
        val important = NotificationChannel(
            CHANNEL_IMPORTANT, "美杜莎重要提醒", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setShowBadge(true)
        }
        manager.createNotificationChannel(status)
        manager.createNotificationChannel(event)
        manager.createNotificationChannel(important)
    }

    private fun canNotify(context: Context): Boolean {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return manager.areNotificationsEnabled()
    }

    fun postRun(context: Context, title: String, detail: String, big: String, ongoing: Boolean) {
        try {
            ensureChannels(context)
            if (!canNotify(context)) {
                writeStatus(context, ready = false, active = false, type = TYPE_RUN)
                return
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val builder = builder(context, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_notice_run)
                .setColor(Color.parseColor("#16A968"))
                .setContentTitle(title.ifBlank { "正在执行" })
                .setContentText(detail.ifBlank { "美杜莎正在处理任务" })
                .setStyle(Notification.BigTextStyle().bigText(big.ifBlank { detail }))
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setGroup(GROUP)
                .setContentIntent(contentIntent(context))
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(Notification.PRIORITY_LOW)
            }
            manager.notify(STATUS_ID, builder.build())
            writeStatus(context, ready = true, active = true, type = TYPE_RUN)
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    fun postEvent(context: Context, type: String, title: String, detail: String, big: String, page: String = "") {
        try {
            ensureChannels(context)
            if (!canNotify(context)) {
                writeStatus(context, ready = false, active = false, type = type)
                return
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val risk = type == TYPE_RISK
            val channel = if (risk) CHANNEL_IMPORTANT else CHANNEL_EVENT
            val icon = when (type) {
                TYPE_ATTENTION -> R.drawable.ic_notice_attention
                TYPE_RISK -> R.drawable.ic_notice_risk
                else -> R.drawable.ic_notice_done
            }
            val color = when (type) {
                TYPE_ATTENTION -> "#F0A020"
                TYPE_RISK -> "#D64545"
                else -> "#16A968"
            }
            val builder = builder(context, channel)
                .setSmallIcon(icon)
                .setColor(Color.parseColor(color))
                .setContentTitle(title)
                .setContentText(detail)
                .setStyle(Notification.BigTextStyle().bigText(big.ifBlank { detail }))
                .setAutoCancel(true)
                .setOnlyAlertOnce(false)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setGroup(GROUP)
                .setContentIntent(contentIntent(context, page))
            if (risk) {
                builder.setCategory(Notification.CATEGORY_ERROR)
            } else if (type == TYPE_ATTENTION) {
                builder.setCategory(Notification.CATEGORY_REMINDER)
            } else {
                builder.setCategory(Notification.CATEGORY_STATUS)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                builder.setPriority(if (risk) Notification.PRIORITY_HIGH else Notification.PRIORITY_DEFAULT)
            }
            manager.notify(if (risk) RISK_ID else EVENT_ID, builder.build())
            writeStatus(context, ready = true, active = true, type = type)
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    fun cancelStatus(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(STATUS_ID)
            writeStatus(context, ready = true, active = false, type = TYPE_RUN)
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    private fun builder(context: Context, channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, channel)
        } else {
            Notification.Builder(context)
        }

    private fun contentIntent(context: Context, page: String = ""): PendingIntent {
        val intent = Intent().apply {
            if (page == "forest") {
                component = ComponentName(
                    "com.medusa.alipay",
                    "io.github.aw1y2z.sesame.ui.miuix.MiuixDomainActivity"
                )
                putExtra("domainKey", "forest")
                putExtra("domainTitle", "蚂蚁森林")
            } else {
                component = ComponentName("com.medusa.alipay", "io.github.aw1y2z.sesame.ui.miuix.MiuixMainActivity")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            context, if (page == "forest") 21 else 20, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun writeStatus(context: Context, ready: Boolean, active: Boolean, type: String) {
        try {
            val file = statusFile()
            val previous = if (file.exists()) {
                runCatching { JSONObject(FileUtil.readFromFile(file) ?: "{}") }.getOrElse { JSONObject() }
            } else JSONObject()
            previous.put("ready", ready)
            previous.put("active", active)
            previous.put("type", type)
            previous.put("at", System.currentTimeMillis())
            FileUtil.write2File(previous.toString(), file)
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }
}
