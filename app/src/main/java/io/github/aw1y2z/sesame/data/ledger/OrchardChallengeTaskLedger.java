package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchardRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 限时挑战账本：三轮挑战及其子任务（施肥 / 小游戏 / 浏览广告）。 */
public final class OrchardChallengeTaskLedger {

    public static final String DOMAIN = "orchard.challenge";
    private static final String TAG = "OrchardChallengeTaskLedger";

    private OrchardChallengeTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            JSONObject root = new JSONObject(AntOrchardRpcCall.orchardSyncIndex("LIMITED_TIME_CHALLENGE", ""));
            if (!MessageUtil.checkResultCode(TAG, root)) {
                return false;
            }
            JSONObject challenge = root.optJSONObject("limitedTimeChallenge");
            OrchardStatus.captureChallenge(userId, challenge);
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            JSONArray rounds = challenge == null ? null : challenge.optJSONArray("limitedTimeChallengeTasks");
            for (int i = 0; rounds != null && i < rounds.length(); i++) {
                JSONObject round = rounds.optJSONObject(i);
                if (round == null) {
                    continue;
                }
                String roundStatus = round.optString("taskStatus", "");
                int roundAward = round.optInt("awardCount", 0);
                JSONArray children = round.optJSONArray("childTaskList");
                for (int j = 0; children != null && j < children.length(); j++) {
                    JSONObject child = children.optJSONObject(j);
                    if (child == null) {
                        continue;
                    }
                    String childId = child.optString("taskId", "");
                    if (childId.isEmpty()) {
                        continue;
                    }
                    String id = "orchard.challenge:" + (i + 1) + ":" + childId;
                    tasks.add(parse(child, existing.remove(id), id, i + 1, roundStatus, roundAward, now));
                }
            }
            keepServerGoneTasks(tasks, existing, now);
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static LedgerTask parse(JSONObject child, LedgerTask old, String id, int roundIndex,
                                    String roundStatus, int roundAward, long now) {
        JSONObject display = child.optJSONObject("taskDisplayConfig");
        String title = display == null ? "" : display.optString("title", "");
        if (title.isEmpty()) {
            title = titleOf(child);
        }
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.group = "挑战" + roundIndex;
        task.title = title;
        task.rawStatus = child.optString("taskStatus", "");
        task.progressCurrent = child.optInt("taskProgress", 0);
        task.progressTotal = child.optInt("taskRequire", 0);
        task.award = roundAward > 0 ? "本轮肥料x" + roundAward : "";
        task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
        task.lastSeenAt = now;
        task.updatedAt = now;
        task.failCount = old == null ? 0 : old.failCount;
        task.manualReason = old == null ? "" : old.manualReason;

        if ("RECEIVED".equals(roundStatus) || "RECEIVED".equals(task.rawStatus)) {
            task.status = LedgerStatus.CLAIMED;
            task.failCount = 0;
            task.manualReason = "";
        } else if ("FINISHED".equals(roundStatus) || "FINISHED".equals(task.rawStatus)) {
            task.status = LedgerStatus.CLAIMABLE;
            task.failCount = 0;
            task.manualReason = "";
        } else if (old != null && old.failCount >= 3) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = old.manualReason == null || old.manualReason.isEmpty()
                    ? "AUTO_FAILED_3X" : old.manualReason;
        } else if (task.progressCurrent > 0) {
            task.status = LedgerStatus.IN_PROGRESS;
        } else {
            task.status = LedgerStatus.UNTOUCHED;
        }
        return task;
    }

    private static String titleOf(JSONObject child) {
        String action = child.optString("actionType", "");
        String taskId = child.optString("taskId", "");
        if ("SPREAD_MANURE".equals(action)) {
            return "施肥" + child.optInt("taskRequire", 0) + "次";
        }
        if ("GAME_CENTER".equals(action)) {
            return "玩小游戏";
        }
        if ("ADD_HOME".equals(action)) {
            return "添加小组件";
        }
        if (taskId.contains("KUAISHOU")) {
            return "逛一逛快手";
        }
        if (taskId.contains("JINDOUDUOBAO")) {
            return "逛金豆夺宝";
        }
        if (taskId.contains("DENGHUO")) {
            return "浏览灯火";
        }
        if ("VISIT".equals(action)) {
            return "浏览任务";
        }
        if ("TRIGGER".equals(action)) {
            return "触发任务";
        }
        return taskId;
    }

    private static Map<String, LedgerTask> loadExisting(String userId, String day) {
        Map<String, LedgerTask> map = new LinkedHashMap<>();
        for (LedgerTask task : TaskLedgerStore.loadTasks(userId, day, DOMAIN)) {
            map.put(task.id, task);
        }
        return map;
    }

    private static void keepServerGoneTasks(List<LedgerTask> tasks, Map<String, LedgerTask> remaining, long now) {
        for (LedgerTask old : remaining.values()) {
            old.status = LedgerStatus.SKIPPED;
            old.manualReason = "SERVER_GONE";
            old.lastSeenAt = now;
            old.updatedAt = now;
            tasks.add(old);
        }
    }
}
