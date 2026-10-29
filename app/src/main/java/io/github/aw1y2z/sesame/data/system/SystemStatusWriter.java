package io.github.aw1y2z.sesame.data.system;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.List;

import io.github.aw1y2z.sesame.BuildConfig;
import io.github.aw1y2z.sesame.data.ledger.FarmStatus;
import io.github.aw1y2z.sesame.data.ledger.ForestStatus;
import io.github.aw1y2z.sesame.data.ledger.GreenActionStatus;
import io.github.aw1y2z.sesame.data.ledger.LedgerScheduler;
import io.github.aw1y2z.sesame.data.ledger.OceanStatus;
import io.github.aw1y2z.sesame.data.ledger.OrchardStatus;
import io.github.aw1y2z.sesame.data.ledger.ProtectStatus;
import io.github.aw1y2z.sesame.data.ledger.SpeciesStatus;
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore;
import io.github.aw1y2z.sesame.data.plan.PlanHistory;
import io.github.aw1y2z.sesame.data.plan.PlanRuntime;
import io.github.aw1y2z.sesame.data.plan.PlanScheduler;
import io.github.aw1y2z.sesame.hook.ApplicationHook;
import io.github.aw1y2z.sesame.util.CapabilityService;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 注入进程写的只读系统状态报告：能力、调度、风控、数据、设备。
 * 不写任务标题原文、好友列表、token。
 */
public final class SystemStatusWriter {

    private static final String TAG = "SystemStatusWriter";
    private static final String FILE_NAME = "system_status.json";
    private static final long THROTTLE_MS = 30_000L;
    private static final int MAX_BYTES = 64 * 1024;
    private static final long PROCESS_STARTED_AT = System.currentTimeMillis();
    private static final long MISSED_GRACE_MS = 5L * 60L * 1000L;

    private static final Object LOCK = new Object();
    private static volatile long lastWriteAt = 0L;

    private SystemStatusWriter() {
    }

    public static void writeAsync(Context context, String trigger) {
        if (context == null) {
            return;
        }
        try {
            new Thread(() -> write(context, trigger, false), "Medusa-SystemStatus").start();
        } catch (Throwable t) {
            Log.err(TAG, "start writer err", t);
        }
    }

    public static void write(Context context, String trigger, boolean force) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            if (!force && now - lastWriteAt < THROTTLE_MS) {
                return;
            }
            lastWriteAt = now;
            try {
                JSONObject root = build(context, trigger, now);
                String json = root.toString();
                if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                    JSONObject minimal = new JSONObject();
                    minimal.put("schema", 1);
                    minimal.put("generatedAt", now);
                    minimal.put("trigger", trigger == null ? "unknown" : trigger);
                    minimal.put("error", "report too large");
                    json = minimal.toString();
                }
                File file = new File(FileUtil.MAIN_DIRECTORY_FILE, FILE_NAME);
                if (!FileUtil.write2File(json, file)) {
                    Log.debug("系统状态写入失败：" + FileUtil.getLastWriteError());
                }
            } catch (Throwable t) {
                Log.err(TAG, "write err", t);
            }
        }
    }

    private static JSONObject build(Context context, String trigger, long now) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schema", 1);
        root.put("generatedAt", now);
        root.put("trigger", trigger == null ? "unknown" : trigger);

        String uid = UserIdMap.getCurrentUid();
        if (uid == null) {
            uid = "";
        }

        JSONObject process = new JSONObject();
        process.put("pid", android.os.Process.myPid());
        process.put("startedAt", PROCESS_STARTED_AT);
        process.put("uptimeMs", Math.max(0L, now - PROCESS_STARTED_AT));
        process.put("hostVersion", hostVersion());
        process.put("moduleVersion", BuildConfig.VERSION_NAME == null ? "" : BuildConfig.VERSION_NAME);
        process.put("fingerprint", BuildConfig.MEDUSA_FINGERPRINT == null ? "" : BuildConfig.MEDUSA_FINGERPRINT);
        process.put("hookReady", hookReady());
        process.put("lastStartReason", "unknown");
        root.put("process", process);

        JSONObject account = new JSONObject();
        account.put("uid", uid);
        account.put("label", accountLabel(uid));
        account.put("login", login(uid));
        root.put("account", account);

        JSONObject scheduler = new JSONObject();
        boolean suspectedKill = false;
        if (!uid.isEmpty()) {
            PlanRuntime runtime = PlanRuntime.load(uid);
            List<PlanHistory.Record> history = PlanHistory.load(uid);
            long nextAt = todayAt(runtime.nextTime, now);
            long lastMissedAt = lastMissedAt(history, now);
            boolean ran = hasRun(history, runtime.nextTime, now);
            suspectedKill = nextAt > 0L && nextAt < now - MISSED_GRACE_MS && !ran;

            scheduler.put("nextTarget", safe(runtime.nextTarget));
            scheduler.put("nextTime", safe(runtime.nextTime));
            scheduler.put("nextAt", nextAt);
            scheduler.put("lastRun", lastRun(runtime));
            scheduler.put("missedToday", missedToday(history, now));

            JSONObject alarmHealth = new JSONObject();
            alarmHealth.put("lastSetAt", PlanScheduler.getLastAlarmSetAt());
            alarmHealth.put("lastFiredAt", runtime.endedAt > 0L ? runtime.endedAt : runtime.updatedAt);
            alarmHealth.put("lastMissedAt", lastMissedAt);
            alarmHealth.put("suspectedKill", suspectedKill);
            scheduler.put("alarmHealth", alarmHealth);
        }
        root.put("scheduler", scheduler);
        root.put("capabilities", CapabilityService.collect(context, uid));

        JSONObject risk = new JSONObject();
        risk.put("globalPaused", RiskGuard.isGlobalPaused());
        risk.put("verificationRequired", RiskGuard.isVerificationRequired());
        JSONArray scopes = new JSONArray();
        List<String> paused = RiskGuard.getPausedScopes();
        for (int i = 0; i < paused.size() && i < 20; i++) {
            String scope = paused.get(i);
            JSONObject item = new JSONObject();
            item.put("scope", scope);
            item.put("reason", safe(RiskGuard.getScopeReason(scope)));
            scopes.put(item);
        }
        risk.put("scopes", scopes);
        JSONObject lastSignal = new JSONObject();
        lastSignal.put("method", safe(RiskGuard.getLastSignalMethod()));
        lastSignal.put("reason", safe(RiskGuard.getLastSignalReason()));
        lastSignal.put("at", RiskGuard.getLastSignalAt());
        risk.put("lastSignal", lastSignal);
        root.put("risk", risk);

        JSONObject data = new JSONObject();
        String day = TaskLedgerStore.today();
        data.put("ledgerDay", day);
        JSONObject report = uid.isEmpty() ? null : TaskLedgerStore.loadReport(uid, day);
        data.put("lastSyncAt", report == null ? 0L : report.optLong("syncedAt", 0L));
        JSONObject reportSummary = new JSONObject();
        reportSummary.put("total", report == null ? 0 : report.optInt("total", 0));
        reportSummary.put("pending", report == null ? 0 : report.optInt("pending", 0));
        reportSummary.put("manual", report == null ? 0 : report.optInt("manual", 0));
        data.put("report", reportSummary);
        JSONObject snapshot = uid.isEmpty() ? null : TaskLedgerStore.loadSnapshot(uid, day);
        data.put("complete", snapshot != null && snapshot.optBoolean("complete", false));
        data.put("statuses", statusHealth(uid, now));
        data.put("statusRetryDay", LedgerScheduler.getStatusRetryDay(context));
        data.put("statusRetryCount", LedgerScheduler.getStatusRetryCount(context));
        JSONObject log = new JSONObject();
        File logDir = new File(FileUtil.MAIN_DIRECTORY_FILE, "log");
        File[] logFiles = logDir.listFiles();
        int fileCount = 0;
        long bytes = 0L;
        if (logFiles != null) {
            for (File file : logFiles) {
                if (file == null || !file.isFile()) {
                    continue;
                }
                fileCount++;
                bytes += file.length();
            }
        }
        log.put("files", fileCount);
        log.put("bytes", bytes);
        data.put("log", log);
        root.put("data", data);

        JSONObject device = new JSONObject();
        device.put("manufacturer", safe(Build.MANUFACTURER));
        device.put("model", safe(Build.MODEL));
        device.put("android", safe(Build.VERSION.RELEASE));
        device.put("oneUI", oneUiVersion());
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        device.put("idle", pm != null && pm.isDeviceIdleMode());
        device.put("powerSave", pm != null && pm.isPowerSaveMode());
        device.put("networkType", CapabilityService.networkType(context));
        root.put("device", device);

        JSONObject oem = new JSONObject();
        oem.put("lastKillAt", 0L);
        oem.put("guidance", suspectedKill && "samsung".equalsIgnoreCase(Build.MANUFACTURER)
                ? samsungGuidance() : "");
        root.put("oem", oem);
        return root;
    }

    private static JSONObject lastRun(PlanRuntime runtime) throws Exception {
        JSONObject last = new JSONObject();
        last.put("planId", safe(runtime.lastPlanId));
        last.put("target", safe(runtime.lastTarget));
        last.put("status", safe(runtime.lastStatus));
        last.put("reason", safe(runtime.reason));
        last.put("startedAt", runtime.startedAt);
        last.put("endedAt", runtime.endedAt);
        long duration = runtime.endedAt > runtime.startedAt
                ? runtime.endedAt - runtime.startedAt : runtime.lastDurationMs;
        last.put("durationMs", Math.max(0L, duration));
        return last;
    }

    private static int missedToday(List<PlanHistory.Record> history, long now) {
        int count = 0;
        for (PlanHistory.Record record : history) {
            if (record == null || !isSameDay(record.startedAt, now)) {
                continue;
            }
            if ("窗口已结束，未执行".equals(record.reason)) {
                count++;
            }
        }
        return count;
    }

    private static long lastMissedAt(List<PlanHistory.Record> history, long now) {
        long latest = 0L;
        for (PlanHistory.Record record : history) {
            if (record == null || !isSameDay(record.startedAt, now)) {
                continue;
            }
            if ("窗口已结束，未执行".equals(record.reason) && record.startedAt > latest) {
                latest = record.startedAt;
            }
        }
        return latest;
    }

    private static boolean hasRun(List<PlanHistory.Record> history, String roundTime, long now) {
        if (roundTime == null || roundTime.isEmpty()) {
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

    private static String hostVersion() {
        try {
            return ApplicationHook.getAlipayVersion() == null
                    ? "" : safe(ApplicationHook.getAlipayVersion().getVersionString());
        } catch (Throwable t) {
            return "";
        }
    }

    private static boolean hookReady() {
        try {
            return ApplicationHook.isHooked() && ApplicationHook.getContext() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean login(String uid) {
        try {
            return !uid.isEmpty() && ApplicationHook.getUserId() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String accountLabel(String uid) {
        try {
            String label = UserIdMap.getAccountLabel(uid);
            return label == null ? "" : label;
        } catch (Throwable t) {
            return "";
        }
    }

    private static String oneUiVersion() {
        String raw = readSystemProperty("ro.build.version.oneui");
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        if (raw.matches("\\d{5}")) {
            return raw.charAt(0) + "." + raw.charAt(1) + "." + raw.charAt(2) + "." + raw.substring(3);
        }
        return raw;
    }

    private static String readSystemProperty(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method get = clazz.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            return value == null ? "" : String.valueOf(value);
        } catch (Throwable t) {
            return "";
        }
    }

    private static String samsungGuidance() {
        return "检测到计划时间已过但本轮没有执行记录，进程是在计划时间之后启动的。"
                + "请检查：设置 → 电池 → 后台使用限制 → 休眠应用/深度休眠应用，"
                + "确认“支付宝”和“Medusa”不在列表中，并加入“永不休眠的应用”；"
                + "同时确认“设备维护 → 自动优化”不会关闭后台应用。";
    }

    /** 首页依赖的七个状态快照：是否当天、生成时间、距今多久。 */
    private static JSONArray statusHealth(String uid, long now) {
        JSONArray array = new JSONArray();
        if (uid.isEmpty()) {
            return array;
        }
        addStatus(array, "ocean", OceanStatus.loadToday(uid), now);
        addStatus(array, "farm", FarmStatus.loadToday(uid), now);
        addStatus(array, "forest", ForestStatus.loadToday(uid), now);
        addStatus(array, "species", SpeciesStatus.loadToday(uid), now);
        addStatus(array, "orchard", OrchardStatus.loadToday(uid), now);
        addStatus(array, "protect", ProtectStatus.loadToday(uid), now);
        addStatus(array, "green", GreenActionStatus.loadToday(uid), now);
        return array;
    }

    private static void addStatus(JSONArray array, String name, JSONObject status, long now) {
        try {
            JSONObject item = new JSONObject();
            item.put("name", name);
            item.put("present", status != null);
            long at = status == null ? 0L : status.optLong("at", 0L);
            item.put("at", at);
            item.put("day", at > 0L ? TaskLedgerStore.dayOf(at) : "");
            item.put("ageMinutes", at > 0L ? Math.max(0L, (now - at) / 60_000L) : -1L);
            array.put(item);
        } catch (Throwable ignored) {
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
