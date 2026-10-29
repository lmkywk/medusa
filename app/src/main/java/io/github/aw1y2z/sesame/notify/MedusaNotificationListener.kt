package io.github.aw1y2z.sesame.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.aw1y2z.sesame.data.AppConfig
import io.github.aw1y2z.sesame.util.Log

/**
 * 通知净化：只处理支付宝的通知。
 * 默认只取消营销/活动/推荐/福利/优惠；支付、转账、收款、退款、安全、验证永远保留。
 * 监听服务只能“显示后取消”，不做子进程 hook；内容只在本机判断，不存储、不上传。
 */
class MedusaNotificationListener : NotificationListenerService() {

    companion object {
        private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
        private val PROTECTED = listOf(
            "支付", "付款", "转账", "收款", "退款", "到账", "账单", "还款", "借呗", "花呗",
            "余额", "安全", "验证", "登录", "身份", "风险", "冻结", "异常", "交易", "密码"
        )
        private val MARKETING = listOf(
            "营销", "活动", "推荐", "福利", "优惠", "红包", "直播", "领券", "优惠券",
            "限时", "抽奖", "会员", "积分", "任务", "签到", "双11", "促销", "商城",
            "好物", "免单", "满减", "秒杀", "特价", "权益", "开奖", "中奖", "礼包",
            "专享", "新品", "店铺", "关注有礼"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        MedusaNotifyConfig.ensure()
        Log.other("通知净化：服务已连接")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName != ALIPAY_PACKAGE) {
            return
        }
        try {
            MedusaNotifyConfig.ensure()
            val onlyMedusa = AppConfig.INSTANCE.noticeOnlyMedusa ?: false
            val filterMarketing = AppConfig.INSTANCE.noticeFilterMarketing ?: true
            if (!onlyMedusa && !filterMarketing) {
                return
            }
            val notification = sbn.notification ?: return
            val extras = notification.extras ?: return
            val channelId = notification.channelId ?: ""
            val manager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            val channelName = manager?.getNotificationChannel(channelId)?.name?.toString() ?: ""
            val text = buildString {
                append(channelName).append(' ')
                append(extras.getCharSequence(Notification.EXTRA_TITLE) ?: "")
                append(' ')
                append(extras.getCharSequence(Notification.EXTRA_TEXT) ?: "")
                append(' ')
                append(extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: "")
                append(' ')
                append(extras.getCharSequence(Notification.EXTRA_SUB_TEXT) ?: "")
            }
            if (text.isBlank()) {
                return
            }
            if (containsAny(text, PROTECTED)) {
                return
            }
            if (onlyMedusa || containsAny(text, MARKETING)) {
                cancelNotification(sbn.key)
                Log.other("通知净化：已拦截一条支付宝" + if (onlyMedusa) "非支付/安全通知" else "营销通知")
            }
        } catch (t: Throwable) {
            Log.printStackTrace(t)
        }
    }

    private fun containsAny(text: String, keys: List<String>): Boolean =
        keys.any { text.contains(it, ignoreCase = true) }
}
