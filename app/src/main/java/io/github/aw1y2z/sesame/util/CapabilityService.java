package io.github.aw1y2z.sesame.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.List;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.data.plan.PlanHistory;
import io.github.aw1y2z.sesame.data.plan.PlanRuntime;
import io.github.aw1y2z.sesame.hook.ApplicationHook;

/**
 * 运行能力检查：只在支付宝注入进程执行。
 * 模块 App 自己检查的是 com.medusa.alipay 的权限，不能代表宿主。
 */
public final class CapabilityService {

    public static final String OK = "OK";
    public static final String MISSING = "MISSING";
    public static final String DEGRADED = "DEGRADED";
    public static final String UNKNOWN = "UNKNOWN";

    private static final long MISSED_GRACE_MS = 5L * 60L * 1000L;

    private CapabilityService() {
    }

    public static JSONArray collect(Context context, String userId) {
        JSONArray out = new JSONArray();
        try {
            out.put(hookItem());
            out.put(loginItem(userId));
            out.put(storagePermissionItem(context));
            out.put(storageWritableItem(context));
            out.put(exactAlarmItem());
            out.put(batteryItem());
            out.put(notificationItem(context));
            out.put(foregroundItem());
            out.put(networkItem(context));
            out.put(rpcItem());
            out.put(alarmHealthItem(userId, System.currentTimeMillis()));
            out.put(oemItem());
            out.put(deviceIdleItem(context));
        } catch (Throwable t) {
            Log.err("CapabilityService", "collect err", t);
        }
        return out;
    }

    private static JSONObject item(String id, String state, String detail, String action) {
        JSONObject item = new JSONObject();
        try {
            item.put("id", id);
            item.put("state", state);
            item.put("detail", detail == null ? "" : detail);
            item.put("action", action == null ? "none" : action);
            item.put("checkedAt", System.currentTimeMillis());
        } catch (Throwable ignored) {
        }
        return item;
    }

    private static JSONObject hookItem() {
        try {
            boolean ready = ApplicationHook.isHooked() && ApplicationHook.getContext() != null;
            return ready
                    ? item("HOOK", OK, "宿主 Hook 已加载", "none")
                    : item("HOOK", MISSING, "宿主 Hook 未加载，请确认支付宝正在运行", "open_alipay");
        } catch (Throwable t) {
            return item("HOOK", MISSING, "无法读取 Hook 状态", "open_alipay");
        }
    }

    private static JSONObject loginItem(String userId) {
        try {
            boolean login = userId != null && !userId.isEmpty()
                    && ApplicationHook.getUserId() != null;
            return login
                    ? item("LOGIN", OK, "已登录", "none")
                    : item("LOGIN", MISSING, "未识别到登录账号", "open_alipay");
        } catch (Throwable t) {
            return item("LOGIN", MISSING, "无法读取登录状态", "open_alipay");
        }
    }

    private static JSONObject storagePermissionItem(Context context) {
        if (context == null) {
            return item("STORAGE_PERMISSION", UNKNOWN, "没有宿主上下文", "none");
        }
        try {
            boolean granted = PermissionUtil.checkFilePermissions(context);
            if (granted) {
                return item("STORAGE_PERMISSION", OK, "已授予所有文件访问权限", "none");
            }
            // 宿主不需要 MANAGE_EXTERNAL_STORAGE：它只读写自己的 Android/media 目录。
            // 之前把宿主侧的环境检查当成模块权限，导致模块已授权仍误报“缺失”。
            if (FileUtil.isMainDirectoryWritable()) {
                return item("STORAGE_PERMISSION", OK,
                        "宿主媒体目录可读写（宿主无需所有文件权限）", "none");
            }
            return item("STORAGE_PERMISSION", MISSING,
                    PermissionUtil.getStorageStatusText(context), "open_storage_settings");
        } catch (Throwable t) {
            return item("STORAGE_PERMISSION", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject storageWritableItem(Context context) {
        if (context == null) {
            return item("STORAGE_WRITABLE", UNKNOWN, "没有宿主上下文", "none");
        }
        try {
            if (FileUtil.isMainDirectoryWritable()) {
                return item("STORAGE_WRITABLE", OK, "宿主媒体目录可写", "none");
            }
            String error = FileUtil.getLastWriteError();
            return item("STORAGE_WRITABLE", MISSING,
                    error == null || error.isEmpty() ? "共享目录不可写" : error,
                    "open_storage_settings");
        } catch (Throwable t) {
            return item("STORAGE_WRITABLE", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject exactAlarmItem() {
        try {
            boolean granted = PermissionUtil.checkAlarmPermissions();
            return granted
                    ? item("EXACT_ALARM", OK, "已允许精确闹钟", "none")
                    : item("EXACT_ALARM", MISSING, "未允许精确闹钟，计划可能延迟", "open_alarm_settings");
        } catch (Throwable t) {
            return item("EXACT_ALARM", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject batteryItem() {
        try {
            boolean granted = PermissionUtil.checkBatteryPermissions();
            return granted
                    ? item("BATTERY_OPTIMIZATION", OK, "支付宝不受电池优化限制", "none")
                    : item("BATTERY_OPTIMIZATION", MISSING, "支付宝仍受电池优化限制", "open_battery_settings");
        } catch (Throwable t) {
            return item("BATTERY_OPTIMIZATION", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject notificationItem(Context context) {
        if (context == null) {
            return item("NOTIFICATION", UNKNOWN, "没有宿主上下文", "none");
        }
        try {
            boolean ready = NotificationUtil.isNotificationReady(context);
            return ready
                    ? item("NOTIFICATION", OK, "通知可用", "none")
                    : item("NOTIFICATION", MISSING, "通知被关闭或渠道被禁用", "open_notification_settings");
        } catch (Throwable t) {
            return item("NOTIFICATION", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject foregroundItem() {
        try {
            boolean enabled = Boolean.TRUE.equals(AppConfig.INSTANCE.getEnableOnGoing());
            boolean active = NotificationUtil.isStatusNotificationActive();
            if (enabled && active) {
                return item("FOREGROUND_SERVICE", OK, "常驻通知已开启", "none");
            }
            if (!enabled) {
                return item("FOREGROUND_SERVICE", DEGRADED,
                        "常驻通知关闭；系统可能在息屏后回收进程", "none");
            }
            return item("FOREGROUND_SERVICE", DEGRADED,
                    "常驻通知已开启但当前未激活", "none");
        } catch (Throwable t) {
            return item("FOREGROUND_SERVICE", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject networkItem(Context context) {
        if (context == null) {
            return item("NETWORK", UNKNOWN, "没有宿主上下文", "none");
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return item("NETWORK", UNKNOWN, "无法读取网络服务", "none");
            }
            Network network = cm.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
            if (caps == null) {
                return item("NETWORK", MISSING, "当前没有可用网络", "open_network_settings");
            }
            boolean internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            boolean validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            String type = networkType(context);
            if (validated) {
                return item("NETWORK", OK, "网络可用（" + type + "）", "none");
            }
            return item("NETWORK", internet ? DEGRADED : MISSING,
                    "网络未验证（" + type + "）", "open_network_settings");
        } catch (Throwable t) {
            return item("NETWORK", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    public static String networkType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return "none";
            }
            Network network = cm.getActiveNetwork();
            NetworkCapabilities caps = network == null ? null : cm.getNetworkCapabilities(network);
            if (caps == null) {
                return "none";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return "wifi";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                return "mobile";
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                return "ethernet";
            }
            return "other";
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static JSONObject rpcItem() {
        try {
            boolean init = ApplicationHook.isInitReady();
            boolean offline = ApplicationHook.isOffline();
            boolean checked = ApplicationHook.isLastCheckOk();
            if (init && !offline && checked) {
                return item("RPC_READY", OK, "RPC 已就绪", "none");
            }
            if (init) {
                return item("RPC_READY", DEGRADED,
                        "RPC 已初始化，最近一次检查未通过"
                                + (offline ? "（离线标记）" : ""), "none");
            }
            return item("RPC_READY", MISSING, "宿主尚未完成初始化", "open_alipay");
        } catch (Throwable t) {
            return item("RPC_READY", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject alarmHealthItem(String userId, long now) {
        if (userId == null || userId.isEmpty()) {
            return item("ALARM_HEALTH", UNKNOWN, "没有账号，无法判断下一跳", "none");
        }
        try {
            PlanRuntime runtime = PlanRuntime.load(userId);
            long nextAt = todayAt(runtime.nextTime, now);
            if (nextAt <= 0L) {
                return item("ALARM_HEALTH", UNKNOWN, "当前没有下一跳", "none");
            }
            List<PlanHistory.Record> history = PlanHistory.load(userId);
            boolean ran = hasRun(history, runtime.nextTime, now);
            if (nextAt < now - MISSED_GRACE_MS && !ran) {
                return item("ALARM_HEALTH", DEGRADED,
                        "下一跳 " + runtime.nextTime + " 已过，未见执行记录", "none");
            }
            return item("ALARM_HEALTH", OK, "下一次 " + runtime.nextTime, "none");
        } catch (Throwable t) {
            return item("ALARM_HEALTH", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static JSONObject oemItem() {
        boolean samsung = "samsung".equalsIgnoreCase(Build.MANUFACTURER);
        if (samsung) {
            return item("OEM_BACKGROUND", UNKNOWN,
                    "三星休眠列表无法直接读取；夜间漏跑请检查后台使用限制", "open_battery_settings");
        }
        return item("OEM_BACKGROUND", OK, "非三星设备，按系统电池设置检查", "none");
    }

    private static JSONObject deviceIdleItem(Context context) {
        if (context == null) {
            return item("DEVICE_IDLE", UNKNOWN, "没有宿主上下文", "none");
        }
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean idle = pm != null && pm.isDeviceIdleMode();
            boolean powerSave = pm != null && pm.isPowerSaveMode();
            if (idle || powerSave) {
                return item("DEVICE_IDLE", DEGRADED,
                        (idle ? "Doze 空闲" : "正常") + " · " + (powerSave ? "省电模式" : "非省电"), "none");
            }
            return item("DEVICE_IDLE", OK, "正常 · 非省电", "none");
        } catch (Throwable t) {
            return item("DEVICE_IDLE", UNKNOWN, "检查失败：" + t.getMessage(), "none");
        }
    }

    private static boolean hasRun(List<PlanHistory.Record> history, String roundTime, long now) {
        if (history == null || roundTime == null || roundTime.isEmpty()) {
            return false;
        }
        for (PlanHistory.Record record : history) {
            if (record == null || !isSameDay(record.startedAt, now)) {
                continue;
            }
            if (!roundTime.equals(record.roundTime)) {
                continue;
            }
            if (!PlanHistory.SKIPPED.equals(record.status)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSameDay(long millis, long now) {
        if (millis <= 0L) {
            return false;
        }
        Calendar a = Calendar.getInstance();
        a.setTimeInMillis(millis);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(now);
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static long todayAt(String hhmm, long now) {
        if (hhmm == null) {
            return 0L;
        }
        try {
            String[] parts = hhmm.split(":");
            if (parts.length != 2) {
                return 0L;
            }
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(now);
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            calendar.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            return calendar.getTimeInMillis();
        } catch (Throwable t) {
            return 0L;
        }
    }
}
