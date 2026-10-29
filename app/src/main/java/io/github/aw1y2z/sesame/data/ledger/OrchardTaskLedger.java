package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelField;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.model.task.antOrchard.AntOrchardRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 芭芭农场肥料任务账本：orchardListTask 的 taskList + 7 天签到。 */
public final class OrchardTaskLedger {

    public static final String DOMAIN = "orchard.task";
    private static final String TAG = "OrchardTaskLedger";
    private static final String BLACKLIST_FIELD = "AntOrchardTaskList";

    private OrchardTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.orchardListTask());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray list = jo.optJSONArray("taskList");
            if (list == null) {
                return false;
            }
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = "orchard:" + firstNonEmpty(item.optString("taskId"),
                        item.optString("groupId"), titleOf(item));
                tasks.add(parse(item, existing.remove(id), id, now));
            }
            JSONObject signInfo = jo.optJSONObject("signTaskInfo");
            LedgerTask sign = parseSign(signInfo, existing, now);
            if (sign != null) {
                tasks.add(0, sign);
            }
            keepServerGoneTasks(tasks, existing, now);
            boolean saved = TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
            if (signInfo != null) {
                OrchardStatus.captureSign(userId, signInfo);
            }
            OrchardStatus.captureFactory(userId, jo.optJSONObject("manureFactory"));
            OrchardStatus.captureTeam(userId, jo.optBoolean("inTeam", false));
            return saved;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static LedgerTask parse(JSONObject item, LedgerTask old, String id, long now) {
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.group = item.optString("groupId", "");
        task.title = titleOf(item);
        task.rawStatus = item.optString("taskStatus", "");
        task.award = awardText(item);
        int rightsTimes = item.optInt("rightsTimes", 0);
        int rightsLimit = item.optInt("rightsTimesLimit", 0);
        task.progressCurrent = rightsLimit > 0 ? rightsTimes : item.optInt("taskProgress", 0);
        task.progressTotal = rightsLimit > 0 ? rightsLimit : item.optInt("taskRequire", 0);
        task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
        task.lastSeenAt = now;
        task.updatedAt = now;
        task.failCount = old == null ? 0 : old.failCount;
        task.manualReason = old == null ? "" : old.manualReason;

        if ("RECEIVED".equals(task.rawStatus)) {
            task.status = LedgerStatus.CLAIMED;
            task.failCount = 0;
            task.manualReason = "";
        } else if ("FINISHED".equals(task.rawStatus)) {
            task.status = LedgerStatus.CLAIMABLE;
            task.failCount = 0;
            task.manualReason = "";
        } else if (old != null && old.failCount >= 3) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = old.manualReason == null || old.manualReason.isEmpty()
                    ? "AUTO_FAILED_3X" : old.manualReason;
        } else {
            String reason = blacklistReason(task.title);
            if (!reason.isEmpty()) {
                task.status = "PRESET_BLACKLIST".equals(reason) ? LedgerStatus.SKIPPED : LedgerStatus.MANUAL;
                task.manualReason = reason;
            } else if (task.progressCurrent > 0) {
                task.status = LedgerStatus.IN_PROGRESS;
            } else {
                task.status = LedgerStatus.UNTOUCHED;
            }
        }
        if (task.status != LedgerStatus.MANUAL && task.status != LedgerStatus.SKIPPED) {
            task.manualReason = "";
        }
        return task;
    }

    private static LedgerTask parseSign(JSONObject signInfo, Map<String, LedgerTask> existing, long now) {
        if (signInfo == null) {
            return null;
        }
        JSONObject current = signInfo.optJSONObject("currentSignItem");
        if (current == null) {
            return null;
        }
        String id = "orchard.sign:" + current.optString("signKey", TaskLedgerStore.today());
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.group = "sign";
        task.title = "每日签到";
        boolean signed = current.optBoolean("signed", false);
        task.rawStatus = signed ? "RECEIVED" : "TODO";
        task.status = signed ? LedgerStatus.CLAIMED : LedgerStatus.UNTOUCHED;
        task.award = "肥料x" + current.optInt("awardCount", 0);
        task.progressCurrent = current.optInt("currentContinuousCount", 0);
        task.progressTotal = 7;
        LedgerTask old = existing.remove(id);
        task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
        task.lastSeenAt = now;
        task.updatedAt = now;
        task.failCount = 0;
        return task;
    }

    private static String titleOf(JSONObject item) {
        JSONObject display = item.optJSONObject("taskDisplayConfig");
        String title = display == null ? "" : display.optString("title", "");
        if (!title.isEmpty()) {
            return title;
        }
        String action = item.optString("actionType", "");
        if ("ANTFARM_COLLECT_MANURE".equals(action)) {
            return "去森林领落叶肥料";
        }
        if ("ANTFOREST_DEFOLIATION".equals(action)) {
            return "森林落叶肥料";
        }
        if ("SYSTEM_SWITCH".equals(action) && "TAOBAO".equals(item.optString("taskPlantType"))) {
            return "去淘宝农场得肥料";
        }
        return firstNonEmpty(item.optString("groupId"), item.optString("taskId"), "未命名任务");
    }

    private static String awardText(JSONObject item) {
        JSONObject display = item.optJSONObject("taskDisplayConfig");
        String type = display == null ? "" : display.optString("awardType", "");
        if (type.isEmpty()) {
            type = item.optString("awardType", "");
        }
        int count = item.optInt("confAwardCount", 0);
        if (count <= 0) {
            count = item.optInt("awardCount", 0);
        }
        if (count <= 0) {
            count = item.optInt("nextStageAwardCount", 0);
        }
        if (count <= 0) {
            return "";
        }
        String desc = LedgerAward.descOf(item, display);
        if (LedgerAward.isNone(type)) {
            return LedgerAward.fallback(desc);
        }
        if ("MANURE".equals(type) || type.isEmpty()) {
            return "肥料x" + count;
        }
        if ("RIGHTS".equals(type)) {
            return "奖励x" + count;
        }
        return LedgerAward.fallback(desc);
    }

    private static String blacklistReason(String title) {
        if (MessageUtil.presetBlackList("AntOrchard", BLACKLIST_FIELD).contains(title)) {
            return "PRESET_BLACKLIST";
        }
        try {
            ModelFields fields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntOrchard");
            ModelField field = fields == null ? null : fields.get(BLACKLIST_FIELD);
            if (field instanceof SelectModelField
                    && ((SelectModelField) field).getValue() != null
                    && ((SelectModelField) field).getValue().contains(title)) {
                return "BLACKLISTED";
            }
        } catch (Throwable ignored) {
        }
        return "";
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

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
