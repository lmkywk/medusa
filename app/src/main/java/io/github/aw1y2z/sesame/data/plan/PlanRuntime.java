package io.github.aw1y2z.sesame.data.plan;

import org.json.JSONObject;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/** 注入进程写的计划运行快照，模块 App 读取后更新通知和计划页。 */
public final class PlanRuntime {

    public static final String IDLE = "IDLE";
    public static final String RUNNING = "RUNNING";
    public static final String WAITING = "WAITING";
    public static final String RISK_PAUSED = "RISK_PAUSED";
    public static final String RISK_STOPPED = "RISK_STOPPED";
    public static final String STOPPING = "STOPPING";
    public static final String STOPPED = "STOPPED";
    public static final String OFFLINE = "OFFLINE";

    private static final String TAG = "PlanRuntime";
    private static final Object LOCK = new Object();

    public String state = IDLE;
    public String planId = "";
    public String target = "";
    public String roundTime = "";
    public int roundIndex;
    public int roundCount;
    public int completedCount;
    public String reason = "";
    public String nextTarget = "";
    public String nextTime = "";
    public long startedAt;
    public long endedAt;
    public String lastStatus = "";
    public int lastCompletedCount;
    public int lastActionCount;
    public int lastEnergyDelta;
    public long lastDurationMs;
    public String lastPlanId = "";
    public String lastTarget = "";
    public String lastRoundTime = "";
    public long updatedAt;

    /** 3.0：一次运行的标识与触发来源。 */
    public String runId = "";
    public String trigger = "";
    public boolean manualOverride;
    /** 3.0：本轮给用户的说明（已完成/需手动/网络异常），供状态页和气泡提示。 */
    public String notice = "";
    public long noticeAt;
    /** 本轮正在执行的具体任务/阶段，供计划卡实时滚动展示。 */
    public String activity = "";
    public long activityAt;
    /** 手动节律建议：上次手动开始/结束时间，按目标记录的最后结束时间。 */
    public long lastManualStartedAt;
    public long lastManualAt;
    public String lastManualTarget = "";
    public long cooldownUntil;
    public long lastRequestAt;
    public boolean lastRequestAccepted;
    public String lastRejectReason = "";
    public JSONObject manualLastByTarget = new JSONObject();

    public JSONObject toJson() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("state", state);
            jo.put("planId", planId);
            jo.put("target", target);
            jo.put("roundTime", roundTime);
            jo.put("roundIndex", roundIndex);
            jo.put("roundCount", roundCount);
            jo.put("completedCount", completedCount);
            jo.put("reason", reason);
            jo.put("nextTarget", nextTarget);
            jo.put("nextTime", nextTime);
            jo.put("startedAt", startedAt);
            jo.put("endedAt", endedAt);
            jo.put("lastStatus", lastStatus);
            jo.put("lastCompletedCount", lastCompletedCount);
            jo.put("lastActionCount", lastActionCount);
            jo.put("lastEnergyDelta", lastEnergyDelta);
            jo.put("lastDurationMs", lastDurationMs);
            jo.put("lastPlanId", lastPlanId);
            jo.put("lastTarget", lastTarget);
            jo.put("lastRoundTime", lastRoundTime);
            jo.put("updatedAt", updatedAt);
            jo.put("runId", runId);
            jo.put("trigger", trigger);
            jo.put("manualOverride", manualOverride);
            jo.put("notice", notice);
            jo.put("noticeAt", noticeAt);
            jo.put("activity", activity);
            jo.put("activityAt", activityAt);
            jo.put("lastManualStartedAt", lastManualStartedAt);
            jo.put("lastManualAt", lastManualAt);
            jo.put("lastManualTarget", lastManualTarget);
            jo.put("cooldownUntil", cooldownUntil);
            jo.put("lastRequestAt", lastRequestAt);
            jo.put("lastRequestAccepted", lastRequestAccepted);
            jo.put("lastRejectReason", lastRejectReason);
            jo.put("manualLastByTarget", manualLastByTarget);
        } catch (Throwable ignored) {
        }
        return jo;
    }

    public static PlanRuntime load(String userId) {
        synchronized (LOCK) {
            PlanRuntime runtime = new PlanRuntime();
            try {
                String json = FileUtil.readFromFile(FileUtil.getPlanRuntimeFile(userId));
                if (json == null || json.trim().isEmpty()) {
                    return runtime;
                }
                JSONObject jo = new JSONObject(json);
                runtime.state = jo.optString("state", IDLE);
                runtime.planId = jo.optString("planId", "");
                runtime.target = jo.optString("target", "");
                runtime.roundTime = jo.optString("roundTime", "");
                runtime.roundIndex = jo.optInt("roundIndex", 0);
                runtime.roundCount = jo.optInt("roundCount", 0);
                runtime.completedCount = jo.optInt("completedCount", 0);
                runtime.reason = jo.optString("reason", "");
                runtime.nextTarget = jo.optString("nextTarget", "");
                runtime.nextTime = jo.optString("nextTime", "");
                runtime.startedAt = jo.optLong("startedAt", 0L);
                runtime.endedAt = jo.optLong("endedAt", 0L);
                runtime.lastStatus = jo.optString("lastStatus", "");
                runtime.lastCompletedCount = jo.optInt("lastCompletedCount", 0);
                runtime.lastActionCount = jo.optInt("lastActionCount", 0);
                runtime.lastEnergyDelta = jo.optInt("lastEnergyDelta", 0);
                runtime.lastDurationMs = jo.optLong("lastDurationMs", 0L);
                runtime.lastPlanId = jo.optString("lastPlanId", "");
                runtime.lastTarget = jo.optString("lastTarget", "");
                runtime.lastRoundTime = jo.optString("lastRoundTime", "");
                runtime.updatedAt = jo.optLong("updatedAt", 0L);
                runtime.runId = jo.optString("runId", "");
                runtime.trigger = jo.optString("trigger", "");
                runtime.manualOverride = jo.optBoolean("manualOverride", false);
                runtime.notice = jo.optString("notice", "");
                runtime.noticeAt = jo.optLong("noticeAt", 0L);
                runtime.activity = jo.optString("activity", "");
                runtime.activityAt = jo.optLong("activityAt", 0L);
                runtime.lastManualStartedAt = jo.optLong("lastManualStartedAt", 0L);
                runtime.lastManualAt = jo.optLong("lastManualAt", 0L);
                runtime.lastManualTarget = jo.optString("lastManualTarget", "");
                runtime.cooldownUntil = jo.optLong("cooldownUntil", 0L);
                runtime.lastRequestAt = jo.optLong("lastRequestAt", 0L);
                runtime.lastRequestAccepted = jo.optBoolean("lastRequestAccepted", false);
                runtime.lastRejectReason = jo.optString("lastRejectReason", "");
                JSONObject manualLast = jo.optJSONObject("manualLastByTarget");
                if (manualLast != null) {
                    runtime.manualLastByTarget = manualLast;
                }
            } catch (Throwable t) {
                Log.err(TAG, "读取计划运行状态失败", t);
            }
            return runtime;
        }
    }

    public void save(String userId) {
        synchronized (LOCK) {
            try {
                updatedAt = System.currentTimeMillis();
                FileUtil.write2File(toJson().toString(), FileUtil.getPlanRuntimeFile(userId));
            } catch (Throwable t) {
                Log.err(TAG, "保存计划运行状态失败", t);
            }
        }
    }

    public static void clear(String userId) {
        PlanRuntime runtime = new PlanRuntime();
        runtime.updatedAt = System.currentTimeMillis();
        runtime.save(userId);
    }
}
