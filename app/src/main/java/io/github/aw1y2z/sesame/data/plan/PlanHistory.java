package io.github.aw1y2z.sesame.data.plan;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/** 计划轮次历史：只保留最近 3 天，供计划页和通知展示。 */
public final class PlanHistory {

    public static final long RETENTION_MS = 3L * 24 * 60 * 60 * 1000;
    private static final int MAX_RECORDS = 500;
    private static final String TAG = "PlanHistory";
    private static final Object LOCK = new Object();

    public static final String COMPLETED = "COMPLETED";
    public static final String INCOMPLETE = "INCOMPLETE";
    public static final String SKIPPED = "SKIPPED";
    public static final String RUNNING = "RUNNING";
    public static final String RISK_STOPPED = "RISK_STOPPED";
    public static final String STOPPED = "STOPPED";

    public static final String TRIGGER_SCHEDULED = "SCHEDULED";
    public static final String TRIGGER_CATCHUP = "CATCHUP";
    public static final String TRIGGER_FALLBACK = "FALLBACK";
    public static final String TRIGGER_PROBE = "PROBE";
    public static final String TRIGGER_MANUAL = "MANUAL";
    public static final String TRIGGER_WINDOW = "WINDOW";

    public static final String REASON_OK = "OK";
    public static final String REASON_OK_MANUAL_OVERRIDE = "OK_MANUAL_OVERRIDE";
    public static final String REASON_SKIP_WINDOW_ENDED = "SKIP_WINDOW_ENDED";
    public static final String REASON_SKIP_NO_TASK = "SKIP_NO_TASK";
    public static final String REASON_SKIP_DONE = "SKIP_DONE";
    public static final String REASON_SKIP_DOMAIN_DISABLED = "SKIP_DOMAIN_DISABLED";
    public static final String REASON_SKIP_CONDITION = "SKIP_CONDITION";
    public static final String REASON_SKIP_BUSY = "SKIP_BUSY";
    public static final String REASON_SKIP_UNKNOWN = "SKIP_UNKNOWN";
    public static final String REASON_BLOCK_PAUSED = "BLOCK_PAUSED";
    public static final String REASON_BLOCK_VERIFY_REQUIRED = "BLOCK_VERIFY_REQUIRED";
    public static final String REASON_BLOCK_RISK_1009 = "BLOCK_RISK_1009";
    public static final String REASON_BLOCK_RISK_CAPTCHA = "BLOCK_RISK_CAPTCHA";
    public static final String REASON_TIMEOUT = "TIMEOUT";
    public static final String REASON_INTERRUPTED = "INTERRUPTED";
    public static final String REASON_STOPPED = "STOPPED";
    public static final String REASON_ERROR = "ERROR";
    public static final String REASON_RUNNING = "RUNNING";
    public static final String REASON_UNKNOWN = "UNKNOWN";

    /** 旧记录没有 reasonCode 时按 status + reason 推导；新记录在写入时补齐。 */
    public static String codeOf(String status, String reason) {
        String s = status == null ? "" : status;
        String r = reason == null ? "" : reason;
        if (COMPLETED.equals(s)) {
            if (r.contains("今日已完成")) {
                return REASON_SKIP_DONE;
            }
            if (r.contains("没有可执行任务")) {
                return REASON_SKIP_NO_TASK;
            }
            return r.contains("用户覆盖") ? REASON_OK_MANUAL_OVERRIDE : REASON_OK;
        }
        if (STOPPED.equals(s)) {
            return REASON_STOPPED;
        }
        if (RUNNING.equals(s)) {
            return REASON_RUNNING;
        }
        if (RISK_STOPPED.equals(s)) {
            if (r.contains("滑块") || r.contains("安全验证")) {
                return REASON_BLOCK_RISK_CAPTCHA;
            }
            if (r.contains("1009")) {
                return REASON_BLOCK_RISK_1009;
            }
            return REASON_BLOCK_VERIFY_REQUIRED;
        }
        if (SKIPPED.equals(s)) {
            if (r.contains("窗口已结束")) {
                return REASON_SKIP_WINDOW_ENDED;
            }
            if (r.contains("需要验证")) {
                return REASON_BLOCK_VERIFY_REQUIRED;
            }
            if (r.contains("暂停")) {
                return REASON_BLOCK_PAUSED;
            }
            if (r.contains("未启动")) {
                return REASON_SKIP_DOMAIN_DISABLED;
            }
            if (r.contains("设置未开启")) {
                return REASON_SKIP_CONDITION;
            }
            if (r.contains("今日已完成")) {
                return REASON_SKIP_DONE;
            }
            if (r.contains("没有可执行任务")) {
                return REASON_SKIP_NO_TASK;
            }
            if (r.contains("已有轮次") || r.contains("上一轮")) {
                return REASON_SKIP_BUSY;
            }
            return REASON_SKIP_UNKNOWN;
        }
        if (INCOMPLETE.equals(s)) {
            if (r.contains("设置未开启")) {
                return REASON_SKIP_CONDITION;
            }
            if (r.contains("超时")) {
                return REASON_TIMEOUT;
            }
            if (r.contains("中断")) {
                return REASON_INTERRUPTED;
            }
            if (r.contains("异常") || r.contains("失败")) {
                return REASON_ERROR;
            }
            return INCOMPLETE;
        }
        return REASON_UNKNOWN;
    }

    public static class Record {
        public String runId = "";
        public String planId = "";
        public String target = "";
        public String roundTime = "";
        public long startedAt;
        public long endedAt;
        public String status = INCOMPLETE;
        public String reason = "";
        public String reasonCode = "";
        public int completedCount;
        public int actionCount;
        public int energyDelta;
        public int roundIndex;
        public int roundCount;
        public String trigger = TRIGGER_SCHEDULED;
        public boolean manualOverride;

        public long durationMs() {
            return endedAt > startedAt ? endedAt - startedAt : 0L;
        }

        public JSONObject toJson() {
            JSONObject jo = new JSONObject();
            try {
                jo.put("runId", runId);
                jo.put("planId", planId);
                jo.put("target", target);
                jo.put("roundTime", roundTime);
                jo.put("startedAt", startedAt);
                jo.put("endedAt", endedAt);
                jo.put("status", status);
                jo.put("reason", reason);
                jo.put("reasonCode", reasonCode == null ? "" : reasonCode);
                jo.put("completedCount", completedCount);
                jo.put("actionCount", actionCount);
                jo.put("energyDelta", energyDelta);
                jo.put("roundIndex", roundIndex);
                jo.put("roundCount", roundCount);
                jo.put("trigger", trigger);
                jo.put("manualOverride", manualOverride);
            } catch (Throwable ignored) {
            }
            return jo;
        }

        public static Record fromJson(JSONObject jo) {
            if (jo == null) {
                return null;
            }
            Record record = new Record();
            record.runId = jo.optString("runId", "");
            record.planId = jo.optString("planId", "");
            record.target = jo.optString("target", "");
            record.roundTime = jo.optString("roundTime", "");
            record.startedAt = jo.optLong("startedAt", 0L);
            record.endedAt = jo.optLong("endedAt", 0L);
            record.status = jo.optString("status", INCOMPLETE);
            record.reason = jo.optString("reason", "");
            record.reasonCode = jo.optString("reasonCode", "");
            if (record.reasonCode.isEmpty()) {
                record.reasonCode = codeOf(record.status, record.reason);
            }
            record.completedCount = jo.optInt("completedCount", 0);
            record.actionCount = jo.optInt("actionCount", 0);
            record.energyDelta = jo.optInt("energyDelta", 0);
            record.roundIndex = jo.optInt("roundIndex", 0);
            record.roundCount = jo.optInt("roundCount", 0);
            record.trigger = jo.optString("trigger", TRIGGER_SCHEDULED);
            record.manualOverride = jo.optBoolean("manualOverride", false);
            return record;
        }
    }

    private PlanHistory() {
    }

    public static List<Record> load(String userId) {
        synchronized (LOCK) {
            List<Record> records = new ArrayList<>();
            File file = FileUtil.getPlanHistoryFile(userId);
            try {
                if (!file.exists()) {
                    return records;
                }
                String json = FileUtil.readFromFile(file);
                if (json == null || json.trim().isEmpty()) {
                    return records;
                }
                JSONArray array = new JSONArray(json);
                long cutoff = System.currentTimeMillis() - RETENTION_MS;
                for (int i = 0; i < array.length(); i++) {
                    Record record = Record.fromJson(array.optJSONObject(i));
                    long time = record == null ? 0L : Math.max(record.startedAt, record.endedAt);
                    if (record != null && time >= cutoff) {
                        records.add(record);
                    }
                }
            } catch (Throwable t) {
                Log.err(TAG, "读取计划历史失败", t);
            }
            return records;
        }
    }

    public static void append(String userId, Record record) {
        upsert(userId, record);
    }

    /** 同一个 runId 先写入 RUNNING，结束时原位更新，避免一次运行出现两条记录。 */
    public static void upsert(String userId, Record record) {
        if (record == null) {
            return;
        }
        synchronized (LOCK) {
            // 同一 runId 会先写 RUNNING 再更新为最终状态，reasonCode 必须随最终状态重算
            record.reasonCode = codeOf(record.status, record.reason);
            List<Record> records = new ArrayList<>(load(userId));
            boolean replaced = false;
            if (record.runId != null && !record.runId.isEmpty()) {
                for (int i = 0; i < records.size(); i++) {
                    if (record.runId.equals(records.get(i).runId)) {
                        records.set(i, record);
                        replaced = true;
                        break;
                    }
                }
            }
            if (!replaced) {
                records.add(record);
            }
            prune(records);
            save(userId, records);
        }
    }

    /** 进程重启后遗留的 RUNNING 不会有人回执；启动/排期时收口成 STOPPED，避免计划页和 AI 一直显示“执行中”。 */
    public static synchronized void cleanupStaleRunning(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Record> records = load(userId);
        boolean changed = false;
        for (Record record : records) {
            if (RUNNING.equals(record.status) && now - record.startedAt > 5L * 60_000L) {
                record.status = STOPPED;
                record.reason = "进程重启，未收到结束回执";
                record.endedAt = now;
                changed = true;
            }
        }
        if (changed) {
            save(userId, records);
            Log.record("计划📋已收口遗留 RUNNING 回执");
        }
    }

    public static void save(String userId, List<Record> records) {
        synchronized (LOCK) {
            try {
                prune(records);
                JSONArray array = new JSONArray();
                for (Record record : records) {
                    record.reasonCode = codeOf(record.status, record.reason);
                    array.put(record.toJson());
                }
                FileUtil.write2File(array.toString(), FileUtil.getPlanHistoryFile(userId));
            } catch (Throwable t) {
                Log.err(TAG, "保存计划历史失败", t);
            }
        }
    }

    private static void prune(List<Record> records) {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        Iterator<Record> iterator = records.iterator();
        while (iterator.hasNext()) {
            Record record = iterator.next();
            long time = Math.max(record.startedAt, record.endedAt);
            if (time < cutoff) {
                iterator.remove();
            }
        }
        while (records.size() > MAX_RECORDS) {
            records.remove(0);
        }
    }
}
