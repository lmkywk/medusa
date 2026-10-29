package io.github.aw1y2z.sesame.util;

import android.content.Context;

import io.github.aw1y2z.sesame.notify.MedusaNoticeCenter;


public class ToastUtil {

    public static void show(Context context, int resId) {
        show(context, context.getText(resId));
    }

    public static void show(Context context, CharSequence text) {
        String clean = NoticeText.clean(text);
        if (clean == null || clean.trim().isEmpty()) {
            return;
        }
        // 3.10.0：不再弹 Android 原生黑底 Toast；模块前台走白色气泡，宿主进程走 BUBBLE 广播。
        if (MedusaNoticeCenter.isForeground()) {
            MedusaNoticeCenter.pushToast(clean);
            return;
        }
        NotificationUtil.showBubble(clean, context);
    }
}
