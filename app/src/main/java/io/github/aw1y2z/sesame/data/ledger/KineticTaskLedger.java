package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.model.task.antSports.AntSportsRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;

/** 医疗健康·动能账本：只读抓取「赚能量」任务与每日签到，归一化为 6 态。 */
public final class KineticTaskLedger {

    public static final String DOMAIN = "sports.kinetic";
    public static final String CARD_SIGN = "sign";
    public static final String CARD_TASK = "task";
    private static final String TAG = "KineticTaskLedger";

    private KineticTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            if (RiskGuard.isVerificationRequired() || RiskGuard.isGlobalPaused()) {
                Log.record("任务账本⏸账号待验证，跳过动能任务抓取");
                return false;
            }
            if (RiskGuard.isPaused("sports")) {
                Log.record("任务账本⏸运动域保护性暂停，跳过动能任务抓取");
                return false;
            }
            JSONObject jo = new JSONObject(AntSportsRpcCall.queryCoinTaskPanel());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return false;
            }
            JSONObject data = jo.optJSONObject("data");
            return data != null && save(userId, data);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    /** 用已经拿到的任务面板响应落账本，避免执行时重复查询。 */
    public static boolean save(String userId, JSONObject data) {
        try {
            JSONArray array = data == null ? null : data.optJSONArray("taskList");
            if (array == null) {
                return false;
            }
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = new LinkedHashMap<>();
            for (LedgerTask task : TaskLedgerStore.loadTasks(userId, day, DOMAIN)) {
                existing.put(task.id, task);
            }
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                tasks.add(parse(raw, existing.remove(taskId(raw)), now));
            }
            // 签到不在 taskList 里，必须先从“已消失”集合移除，否则同一 ID 会同时留下
            // 一条新签到和一条 SERVER_GONE 的旧签到（金豆账本同位置已这样处理）。
            LedgerTask sign = signTask(userId, now);
            existing.remove("sign");
            for (LedgerTask old : existing.values()) {
                old.status = LedgerStatus.SKIPPED;
                old.manualReason = "SERVER_GONE";
                old.lastSeenAt = now;
                old.updatedAt = now;
                tasks.add(old);
            }
            if (sign != null) {
                tasks.add(0, sign);
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "save err:", t);
            return false;
        }
    }

    private static LedgerTask signTask(String userId, long now) {
        try {
            JSONObject jo = new JSONObject(AntSportsRpcCall.querySignInCoinTask());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return null;
            }
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                return null;
            }
            boolean signed = data.optBoolean("signed", false);
            int amount = 0;
            JSONArray configs = data.optJSONArray("signConfigList");
            if (configs != null) {
                for (int i = 0; i < configs.length(); i++) {
                    JSONObject item = configs.optJSONObject(i);
                    if (item != null && item.optBoolean("toDay", false)) {
                        amount = item.optInt("coinAmount", 0) + item.optInt("extraCoinAmount", 0);
                        break;
                    }
                }
            }
            LedgerTask task = new LedgerTask();
            task.id = "sign";
            task.title = "每日签到";
            task.group = CARD_SIGN;
            task.rawStatus = signed ? "SIGNED" : "WAIT_RECEIVE";
            task.status = signed ? LedgerStatus.CLAIMED : LedgerStatus.CLAIMABLE;
            task.award = amount > 0 ? amount + "g" : "";
            task.firstSeenAt = now;
            task.lastSeenAt = now;
            task.updatedAt = now;
            return task;
        } catch (Throwable t) {
            Log.err(TAG, "signTask err:", t);
            return null;
        }
    }

    public static boolean isActionable(String userId) {
        JSONObject summary = TaskLedgerStore.loadDomainSummary(userId, TaskLedgerStore.today(), DOMAIN);
        if (summary == null) {
            return true;
        }
        return summary.optInt("claimable", 0) > 0;
    }

    public static boolean recordClaimed(String userId, String taskId) {
        return update(userId, taskId, LedgerStatus.CLAIMED, "", 0);
    }

    public static boolean recordFailure(String userId, String taskId, String reason) {
        try {
            String day = TaskLedgerStore.today();
            List<LedgerTask> tasks = TaskLedgerStore.loadTasks(userId, day, DOMAIN);
            for (LedgerTask task : tasks) {
                if (taskId.equals(task.id)) {
                    task.failCount++;
                    task.updatedAt = System.currentTimeMillis();
                    task.lastSeenAt = task.updatedAt;
                    if (task.failCount >= 3) {
                        task.status = LedgerStatus.MANUAL;
                        task.manualReason = reason == null || reason.isEmpty() ? "AUTO_FAILED_3X" : reason;
                    }
                    break;
                }
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "recordFailure err:", t);
            return false;
        }
    }

    private static boolean update(String userId, String taskId, LedgerStatus status,
                                  String manualReason, int failCount) {
        try {
            String day = TaskLedgerStore.today();
            List<LedgerTask> tasks = TaskLedgerStore.loadTasks(userId, day, DOMAIN);
            for (LedgerTask task : tasks) {
                if (taskId.equals(task.id)) {
                    task.status = status;
                    task.manualReason = manualReason;
                    task.failCount = failCount;
                    task.updatedAt = System.currentTimeMillis();
                    task.lastSeenAt = task.updatedAt;
                    break;
                }
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "update err:", t);
            return false;
        }
    }

    private static LedgerTask parse(JSONObject raw, LedgerTask old, long now) {
        LedgerTask task = new LedgerTask();
        task.id = taskId(raw);
        task.title = raw.optString("taskName", task.id);
        task.group = CARD_TASK;
        task.rawStatus = raw.optString("taskStatus", "");
        task.progressCurrent = raw.optInt("currentNum", 0);
        task.progressTotal = raw.optInt("limitConfigNum", 0);
        task.award = raw.optInt("prizeAmount", 0) > 0 ? raw.optInt("prizeAmount", 0) + "g" : "";
        task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
        task.lastSeenAt = now;
        task.updatedAt = now;
        task.failCount = old == null ? 0 : old.failCount;

        String status = task.rawStatus;
        if ("HAS_RECEIVED".equals(status)) {
            task.status = LedgerStatus.CLAIMED;
            task.failCount = 0;
        } else if ("WAIT_RECEIVE".equals(status)) {
            task.status = LedgerStatus.CLAIMABLE;
            task.failCount = 0;
        } else if (old != null && old.failCount >= 3) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = old.manualReason;
        } else if (requiresExternalJump(raw)) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = "EXTERNAL_JUMP";
        } else if (task.progressCurrent > 0) {
            task.status = LedgerStatus.IN_PROGRESS;
        } else {
            task.status = LedgerStatus.UNTOUCHED;
        }
        if (task.status != LedgerStatus.MANUAL && task.status != LedgerStatus.SKIPPED) {
            task.manualReason = "";
        }
        return task;
    }

    private static boolean requiresExternalJump(JSONObject raw) {
        if (raw.optBoolean("adTask", false) || raw.optBoolean("multiTask", false)) {
            return true;
        }
        String action = raw.optString("taskAction", "");
        String type = raw.optString("taskType", "");
        return "JUMP".equals(action) || "SHOW_AD".equals(action)
                || "BROWSER".equals(type) || "TRANSFORMER".equals(type);
    }

    private static String taskId(JSONObject raw) {
        String id = raw.optString("taskId", "");
        if (!id.isEmpty()) {
            return id;
        }
        return raw.optString("taskName", "kinetic");
    }
}
