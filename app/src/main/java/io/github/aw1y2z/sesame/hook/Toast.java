package io.github.aw1y2z.sesame.hook;

import android.content.Context;
import android.os.Handler;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.NoticeText;
import io.github.aw1y2z.sesame.util.NotificationUtil;

public class Toast {
    private static final String TAG = Toast.class.getSimpleName();

    public static void show(CharSequence cs) {
        show(cs, false);
    }

    public static void show(CharSequence cs, boolean force) {
        if (!force && !io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getShowToast()) {
            return;
        }
        // 3.10.0：宿主不再弹 Android 原生黑底 Toast；发 BUBBLE 广播给模块 App，
        // 模块在前台时显示白色气泡，不在前台则安静丢弃（重要事件仍走系统通知）。
        Context context = ApplicationHook.getContext();
        NotificationUtil.showBubble(NoticeText.clean(cs), context);
    }

    public static void show(Context context, Handler handler, CharSequence cs) {
        NotificationUtil.showBubble(NoticeText.clean(cs), context);
    }
}
