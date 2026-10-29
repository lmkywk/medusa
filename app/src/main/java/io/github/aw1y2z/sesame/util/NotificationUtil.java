package io.github.aw1y2z.sesame.util;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.io.File;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.data.plan.PlanHistory;

/**
 * 通知事件出口：宿主只发结构化事件，由模块 App（com.medusa.alipay）负责系统通知，
 * 这样通知顶部显示“美杜莎”，且在 App 前台时可以改走气泡。
 */
public class NotificationUtil {

    public static final String TYPE_RUN = "RUN";
    public static final String TYPE_DONE = "DONE";
    public static final String TYPE_ATTENTION = "ATTENTION";
    public static final String TYPE_RISK = "RISK";

    private static final String MODULE_PACKAGE = "com.medusa.alipay";
    private static final String NOTICE_ACTION = "com.medusa.alipay.notice.EVENT";
    private static final String STATUS_FILE = "notice_status.json";

    private static volatile Context context;
    @Getter
    private static volatile long lastNoticeTime = 0;
    private static String contentText = "";
    private static final AtomicInteger runningCount = new AtomicInteger(0);
    private static final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

    public static void startRound() {
        Statistics.resetRoundCollected();
    }

    public static void trackTaskStart(String name) {
        if (!StringUtil.isEmpty(name)) {
            runningTasks.add(name);
        }
        runningCount.incrementAndGet();
        updateRunningText();
    }

    public static void trackTaskEnd(String name) {
        if (runningCount.decrementAndGet() < 0) {
            runningCount.set(0);
        }
        if (!StringUtil.isEmpty(name)) {
            runningTasks.remove(name);
        }
        updateRunningText();
    }

    private static void updateRunningText() {
        long now = System.currentTimeMillis();
        if (runningTasks.isEmpty()) {
            if (runningCount.get() == 0) {
                updateLastExecText();
            }
            return;
        }
        contentText = "执行中：" + String.join(" / ", runningTasks);
        lastNoticeTime = now;
        sendRun();
    }

    public static int getRunningCount() {
        return runningCount.get();
    }

    public static void start(Context ctx) {
        context = ctx == null ? null : ctx.getApplicationContext();
        contentText = "启动中";
        lastNoticeTime = System.currentTimeMillis();
        sendRun();
    }

    public static void stop() {
        contentText = "";
        sendCancel();
    }

    public static void updateStatusText(String status) {
        try {
            long forestPauseTime = io.github.aw1y2z.sesame.data.RuntimeInfo.getInstance()
                    .getLong(io.github.aw1y2z.sesame.data.RuntimeInfo.RuntimeInfoKey.ForestPauseTime);
            if (forestPauseTime > System.currentTimeMillis()) {
                status = "触发异常，等待至" + TimeUtil.getCommonDate(forestPauseTime);
            }
            contentText = NoticeText.clean(status);
            lastNoticeTime = System.currentTimeMillis();
            sendRun();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private static volatile long nextExecTime = 0;

    public static void setNextExecTime(long nextExecTime) {
        NotificationUtil.nextExecTime = nextExecTime;
    }

    public static void updateLastExecText() {
        try {
            long now = System.currentTimeMillis();
            String text = "上次执行 " + TimeUtil.getTimeStr(now);
            if (nextExecTime > 0) {
                text += " · 下次执行 " + TimeUtil.getTimeStr(nextExecTime);
                nextExecTime = 0;
            }
            contentText = NoticeText.clean(text);
            lastNoticeTime = now;
            sendRun();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void setRunning() {
        try {
            lastNoticeTime = System.currentTimeMillis();
            sendRun();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    public static void updateIdleText() {
        try {
            String text = "等待执行";
            if (nextExecTime > 0) {
                text += " · 下次执行 " + TimeUtil.getTimeStr(nextExecTime);
            }
            contentText = NoticeText.clean(text);
            lastNoticeTime = System.currentTimeMillis();
            sendRun();
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /**
     * 一轮计划结束后发一条可回看的事件。默认“简洁”：只保留最新一条结果；
     * P0 风控会固定使用重要提醒，避免被普通完成覆盖。
     */
    public static void notifyPlanResult(String label, String roundTime, boolean completed,
                                        int completedCount, int actions, long durationMs,
                                        String reason, String trigger,
                                        String status, String notice) {
        if (context == null) {
            return;
        }
        String cleanReason = NoticeText.clean(reason);
        String cleanNotice = NoticeText.clean(notice);
        boolean risk = PlanHistory.RISK_STOPPED.equals(status)
                || (!completed && containsAny(cleanReason, "验证", "风控", "1009", "暂停"));
        boolean attention = !risk && (!completed || cleanNotice.contains("需手动")
                || cleanNotice.contains("需要你处理"));
        String type = risk ? TYPE_RISK : (attention ? TYPE_ATTENTION : TYPE_DONE);
        String title = risk ? "风控暂停" : (attention ? "需要你处理" : "计划完成");

        String detail = NoticeText.clean(label + " " + roundTime + " · "
                + (completed ? "已完成" : "未完成") + " · 用时 " + durationText(durationMs));
        if (attention && !cleanNotice.isEmpty()) {
            detail = NoticeText.oneLine(cleanNotice, 88);
        } else if (risk && !cleanReason.isEmpty()) {
            detail = NoticeText.oneLine(cleanReason, 88);
        }
        StringBuilder big = new StringBuilder(detail);
        big.append("\n动作 ").append(actions).append(" 项 · 能量 +").append(completedCount).append('g');
        if (!sourceText(trigger).isEmpty()) {
            big.append(" · ").append(sourceText(trigger));
        }
        if (!cleanReason.isEmpty() && !risk) {
            big.append("\n原因：").append(cleanReason);
        }
        if (!cleanNotice.isEmpty() && !attention) {
            big.append("\n提示：").append(cleanNotice);
        }
        sendEvent(type, title, detail, NoticeText.oneLine(big, 420),
                AppConfig.INSTANCE.getShowToast() == null || AppConfig.INSTANCE.getShowToast());
    }

    private static boolean containsAny(String value, String... words) {
        if (value == null) {
            return false;
        }
        for (String word : words) {
            if (value.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static String sourceText(String trigger) {
        if ("CATCHUP".equals(trigger)) {
            return "补跑";
        }
        if ("FALLBACK".equals(trigger)) {
            return "晚间兜底";
        }
        if ("PROBE".equals(trigger)) {
            return "零点试探";
        }
        if ("MANUAL".equals(trigger)) {
            return "手动执行";
        }
        return "定时执行";
    }

    private static String durationText(long durationMs) {
        long seconds = Math.max(0L, durationMs / 1000L);
        if (seconds < 60) {
            return seconds + "秒";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 60) {
            return minutes + "分" + rest + "秒";
        }
        return (minutes / 60) + "小时" + (minutes % 60) + "分";
    }

    private static String energyText() {
        return "本轮收取 " + Statistics.getRoundCollected() + "g"
                + " · 本日收取 " + Statistics.getData(Statistics.TimeType.DAY, Statistics.DataType.COLLECTED) + "g";
    }

    private static void sendRun() {
        if (context == null) {
            return;
        }
        try {
            String big = (StringUtil.isEmpty(contentText) ? "" : contentText + "\n") + energyText();
            String runTitle = contentText.startsWith("执行中") || contentText.startsWith("计划")
                    ? "正在执行" : "运行状态";
            Intent intent = new Intent(NOTICE_ACTION)
                    .setPackage(MODULE_PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra("kind", "RUN")
                    .putExtra("type", TYPE_RUN)
                    .putExtra("title", runTitle)
                    .putExtra("detail", NoticeText.oneLine(contentText, 120))
                    .putExtra("big", NoticeText.oneLine(big, 420))
                    .putExtra("ongoing", Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableOnGoing()));
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    private static void sendEvent(String type, String title, String detail, String big, boolean bubble) {
        sendEvent(type, title, detail, big, bubble, "");
    }

    private static void sendEvent(String type, String title, String detail, String big, boolean bubble, String page) {
        if (context == null) {
            return;
        }
        try {
            Intent intent = new Intent(NOTICE_ACTION)
                    .setPackage(MODULE_PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra("kind", "EVENT")
                    .putExtra("type", type)
                    .putExtra("title", title)
                    .putExtra("detail", detail)
                    .putExtra("big", big)
                    .putExtra("bubble", bubble)
                    .putExtra("page", page == null ? "" : page)
                    .putExtra("ongoing", false);
            context.sendBroadcast(intent);
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /** 3.9.0 活力值秒杀提醒；page=forest 让模块 App 打开森林域页面。 */
    public static void notifySeckill(String title, String detail, String big) {
        sendEvent(TYPE_ATTENTION, title, detail, big, true, "forest");
    }

    /** 4.0.0：AI 每日/异常摘要通知。内容已经是聚合结论，不含日志原文。 */
    public static void notifyAiSummary(String title, String detail) {
        sendEvent(TYPE_ATTENTION, title, detail, detail, true, "");
    }

    /**
     * 3.10.0 普通操作提示：宿主进程发 BUBBLE 广播，模块 App 前台时显示白色气泡。
     * 不建系统通知、不拉起模块 App；模块不在前台就丢弃，避免黑底 Toast 回潮。
     */
    public static boolean showBubble(String text) {
        return showBubble(text, null);
    }

    public static boolean showBubble(String text, Context fallbackContext) {
        try {
            Context ctx = context != null ? context : fallbackContext;
            if (ctx == null) {
                return false;
            }
            String clean = NoticeText.clean(text);
            if (clean == null || clean.trim().isEmpty()) {
                return false;
            }
            Intent intent = new Intent(NOTICE_ACTION)
                    .setPackage(MODULE_PACKAGE)
                    .putExtra("kind", "BUBBLE")
                    .putExtra("detail", clean);
            ctx.sendBroadcast(intent);
            return true;
        } catch (Exception e) {
            Log.printStackTrace(e);
            return false;
        }
    }

    private static void sendCancel() {
        if (context == null) {
            return;
        }
        try {
            context.sendBroadcast(new Intent(NOTICE_ACTION)
                    .setPackage(MODULE_PACKAGE)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    .putExtra("kind", "CANCEL"));
        } catch (Exception e) {
            Log.printStackTrace(e);
        }
    }

    /** 通知总开关 + 模块 App 渠道是否可用；模块 App 每次发通知会把状态写到共享目录。 */
    public static boolean isNotificationReady(Context ctx) {
        try {
            JSONObject status = readStatusFile();
            if (status != null && System.currentTimeMillis() - status.optLong("at", 0L) < 24L * 60L * 60L * 1000L) {
                return status.optBoolean("ready", false);
            }
            if (ctx == null) {
                return false;
            }
            android.app.NotificationManager manager =
                    (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return manager != null && manager.areNotificationsEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 运行状态通知是否真的挂着；读模块 App 写的共享状态。 */
    public static boolean isStatusNotificationActive() {
        try {
            JSONObject status = readStatusFile();
            return status != null && status.optBoolean("active", false);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static JSONObject readStatusFile() {
        try {
            File file = new File(FileUtil.MAIN_DIRECTORY_FILE, STATUS_FILE);
            if (!file.exists()) {
                return null;
            }
            String text = FileUtil.readFromFile(file);
            return text == null || text.isEmpty() ? null : new JSONObject(text);
        } catch (Throwable t) {
            return null;
        }
    }
}
