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
import io.github.aw1y2z.sesame.model.task.antForest.AntForestRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 森林乐园限定任务账本：queryOptionalPlay 的 taskTriggerPlayInfo.taskList。 */
public final class ForestParkTaskLedger {

    public static final String DOMAIN = "forest.park";
    private static final String TAG = "ForestParkTaskLedger";
    private static final String BLACKLIST_FIELD = "AntForestVitalityTaskList";

    private ForestParkTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            Map<String, LedgerTask> captured = new LinkedHashMap<>();
            JSONObject jo = new JSONObject(AntForestRpcCall.queryOptionalPlay());
            if (!MessageUtil.checkSuccess(TAG, jo)) {
                return false;
            }
            JSONObject trigger = jo.optJSONObject("taskTriggerPlayInfo");
            JSONArray list = trigger == null ? null : trigger.optJSONArray("taskList");
            if (list == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.length(); i++) {
                JSONObject task = list.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                String taskType = task.optString("taskType", "");
                String scene = task.optString("sceneCode", "");
                if (taskType.isEmpty()) {
                    continue;
                }
                String id = "forest.park:" + scene + ":" + taskType;
                JSONObject biz = task.optJSONObject("bizInfo");
                String title = biz == null ? taskType : biz.optString("title", taskType);
                LedgerTask lt = new LedgerTask();
                lt.id = id;
                lt.title = title;
                lt.group = scene;
                lt.rawStatus = task.optString("taskStatus", "");
                lt.progressCurrent = task.optInt("rightsTimes", 0);
                lt.progressTotal = task.optInt("rightsTimesLimit", 0);
                lt.award = awardText(task);
                lt.lastSeenAt = now;
                lt.updatedAt = now;
                LedgerTask old = existing.remove(id);
                lt.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
                lt.failCount = old == null ? 0 : old.failCount;
                lt.manualReason = old == null ? "" : old.manualReason;

                if ("RECEIVED".equals(lt.rawStatus)) {
                    lt.status = LedgerStatus.CLAIMED;
                } else if ("FINISHED".equals(lt.rawStatus)) {
                    lt.status = LedgerStatus.CLAIMABLE;
                } else if (lt.failCount >= 3) {
                    lt.status = LedgerStatus.MANUAL;
                    lt.manualReason = lt.manualReason == null || lt.manualReason.isEmpty()
                            ? "AUTO_FAILED_3X" : lt.manualReason;
                } else {
                    String reason = blacklistReason(title);
                    if (!reason.isEmpty()) {
                        lt.status = "PRESET_BLACKLIST".equals(reason) ? LedgerStatus.SKIPPED : LedgerStatus.MANUAL;
                        lt.manualReason = reason;
                    } else if (lt.progressCurrent > 0) {
                        lt.status = LedgerStatus.IN_PROGRESS;
                    } else {
                        lt.status = LedgerStatus.UNTOUCHED;
                    }
                }
                if (lt.status != LedgerStatus.MANUAL && lt.status != LedgerStatus.SKIPPED) {
                    lt.manualReason = "";
                }
                captured.put(id, lt);
            }
            List<LedgerTask> tasks = new ArrayList<>(captured.values());
            for (LedgerTask old : existing.values()) {
                old.status = LedgerStatus.SKIPPED;
                old.manualReason = "SERVER_GONE";
                tasks.add(old);
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static String awardText(JSONObject task) {
        int count = task.optInt("awardCount", 0);
        if (count <= 0) {
            return "";
        }
        String type = task.optString("awardType", "");
        if (LedgerAward.isNone(type)) {
            return LedgerAward.fallback(LedgerAward.descOf(task, null));
        }
        if (type.equalsIgnoreCase("ENERGY") || type.isEmpty()) {
            return "能量x" + count;
        }
        if (type.equalsIgnoreCase("VITALITY")) {
            return "活力值x" + count;
        }
        if (type.contains("DRAW")) {
            return "抽奖机会x" + count;
        }
        return "奖励x" + count;
    }

    private static String blacklistReason(String title) {
        if (MessageUtil.presetBlackList("AntForestV2", BLACKLIST_FIELD).contains(title)) {
            return "PRESET_BLACKLIST";
        }
        try {
            ModelFields fields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntForestV2");
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
}
