package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RiskGuard;

/** 金豆夺宝任务账本：主页自带的金豆任务，外跳类标为需手动。 */
public final class GoldenBeansTaskLedger {

    public static final String DOMAIN = "golden";
    public static final String CARD_SIGN = "sign";
    public static final String CARD_TASK = "task";
    private static final String TAG = "GoldenBeansTaskLedger";

    private GoldenBeansTaskLedger() {
    }

    public static boolean capture(String userId) {
        if (RiskGuard.isPaused("golden")) {
            Log.record("任务账本⏸金豆域保护性暂停，跳过任务抓取");
            return false;
        }
        JSONObject home = GoldenBeansStatus.fetch();
        if (home == null) {
            return false;
        }
        GoldenBeansStatus.capture(userId, home);
        return save(userId, home);
    }

    public static boolean save(String userId, JSONObject home) {
        try {
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = new LinkedHashMap<>();
            for (LedgerTask task : TaskLedgerStore.loadTasks(userId, day, DOMAIN)) {
                existing.put(task.id, task);
            }
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            addTasks(tasks, existing, home.optJSONArray("taskList"), CARD_TASK, now);
            // farmTaskList 是芭芭农场的肥料任务（果园域账本已收录），不并入金豆账本
            // 签到是独立伪任务，先从旧账本移除，避免被当成“服务端已下线”再补一条
            existing.remove("sign");
            for (LedgerTask old : existing.values()) {
                if ("farm".equals(old.group)) {
                    // 旧版误把芭芭农场肥料任务并入本账本：直接丢弃，不留“已跳过”残影
                    continue;
                }
                old.status = LedgerStatus.SKIPPED;
                old.manualReason = "SERVER_GONE";
                old.lastSeenAt = now;
                old.updatedAt = now;
                tasks.add(old);
            }
            LedgerTask sign = signTask(home.optJSONObject("signInfo"), now);
            if (sign != null) {
                tasks.add(0, sign);
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "save err:", t);
            return false;
        }
    }

    public static boolean isActionable(String userId) {
        JSONObject summary = TaskLedgerStore.loadDomainSummary(userId, TaskLedgerStore.today(), DOMAIN);
        if (summary == null) {
            return true;
        }
        return summary.optInt("claimable", 0) > 0 || summary.optInt("untouched", 0) > 0;
    }

    private static void addTasks(List<LedgerTask> out, Map<String, LedgerTask> existing,
                                 JSONArray array, String group, long now) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject raw = array.optJSONObject(i);
            if (raw == null) {
                continue;
            }
            String id = raw.optString("taskId", "");
            if (id.isEmpty()) {
                continue;
            }
            out.add(parse(raw, group, existing.remove(id), now));
        }
    }

    private static LedgerTask signTask(JSONObject sign, long now) {
        if (sign == null) {
            return null;
        }
        JSONArray list = sign.optJSONArray("signList");
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null || !item.optBoolean("today", false)) {
                continue;
            }
            LedgerTask task = new LedgerTask();
            task.id = "sign";
            task.title = "每日签到";
            task.group = CARD_SIGN;
            task.rawStatus = item.optBoolean("signed", false) ? "SIGNED" : "TODO";
            task.status = item.optBoolean("signed", false) ? LedgerStatus.CLAIMED : LedgerStatus.CLAIMABLE;
            task.award = item.optInt("awardCount", 0) + "豆";
            task.progressCurrent = sign.optInt("continuousDays", 0);
            task.progressTotal = 7;
            task.firstSeenAt = now;
            task.lastSeenAt = now;
            task.updatedAt = now;
            return task;
        }
        return null;
    }

    private static LedgerTask parse(JSONObject raw, String group, LedgerTask old, long now) {
        LedgerTask task = new LedgerTask();
        task.id = raw.optString("taskId", "");
        JSONObject display = raw.optJSONObject("taskDisplayConfig");
        task.title = display == null ? task.id : display.optString("title", task.id);
        task.group = group;
        task.rawStatus = raw.optString("taskStatus", "");
        task.progressCurrent = raw.optInt("taskProgress", 0);
        task.progressTotal = raw.optInt("taskRequire", 0);
        task.award = raw.optInt("awardCount", 0) + "豆";
        task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
        task.lastSeenAt = now;
        task.updatedAt = now;
        task.failCount = old == null ? 0 : old.failCount;

        String status = task.rawStatus.toUpperCase();
        if (status.contains("RECEIVED") || status.contains("AWARDED") || status.contains("COMPLETED")) {
            task.status = LedgerStatus.CLAIMED;
        } else if (status.contains("FINISH") || status.contains("DONE") || status.contains("RECEIVE")) {
            task.status = LedgerStatus.CLAIMABLE;
        } else if (old != null && old.failCount >= 3) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = old.manualReason;
        } else if (!autoAction(raw.optString("actionType", ""))) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = "EXTERNAL_VISIT";
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

    private static boolean autoAction(String actionType) {
        return "TRIGGER".equals(actionType)
                || "GAMECENTER_TRIGGER".equals(actionType)
                || "MANURE_EXCHANGE".equals(actionType);
    }
}
