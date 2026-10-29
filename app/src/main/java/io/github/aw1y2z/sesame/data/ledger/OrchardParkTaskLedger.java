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

/** 芭芭农场乐园限定任务账本：queryOptionalPlay 的 taskTriggerPlayInfo.taskList。 */
public final class OrchardParkTaskLedger {

    public static final String DOMAIN = "orchard.park";
    private static final String TAG = "OrchardParkTaskLedger";
    private static final String BLACKLIST_FIELD = "AntOrchardTaskList";

    private OrchardParkTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            JSONObject jo = new JSONObject(AntOrchardRpcCall.queryOptionalPlay());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return false;
            }
            JSONObject trigger = jo.optJSONObject("taskTriggerPlayInfo");
            JSONArray list = trigger == null ? null : trigger.optJSONArray("taskList");
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
                String taskType = item.optString("taskType", "");
                String scene = item.optString("sceneCode", "");
                if (taskType.isEmpty()) {
                    continue;
                }
                String id = "orchard.park:" + scene + ":" + taskType;
                tasks.add(parse(item, existing.remove(id), id, scene, now));
            }
            keepServerGoneTasks(tasks, existing, now);
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static LedgerTask parse(JSONObject item, LedgerTask old, String id, String scene, long now) {
        JSONObject biz = item.optJSONObject("bizInfo");
        String taskType = item.optString("taskType", "");
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.group = scene;
        task.title = biz == null ? taskType : biz.optString("title", taskType);
        task.rawStatus = item.optString("taskStatus", "");
        task.progressCurrent = item.optInt("rightsTimes", 0);
        task.progressTotal = item.optInt("rightsTimesLimit", 0);
        int awardCount = item.optInt("awardCount", 0) - item.optInt("alreadyReceiveAwardCount", 0);
        task.award = awardCount > 0 ? "肥料x" + awardCount : "";
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
}
