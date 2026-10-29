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
import io.github.aw1y2z.sesame.model.task.antDodo.AntDodoRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 神奇物种任务账本：AntDodo.taskList 的 taskGroupInfoList，叶子任务入账。 */
public final class SpeciesTaskLedger {

    public static final String DOMAIN = "species.task";
    private static final String TAG = "SpeciesTaskLedger";
    private static final String BLACKLIST_FIELD = "AntDodoTaskList";

    private SpeciesTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            Map<String, LedgerTask> captured = new LinkedHashMap<>();
            JSONObject jo = new JSONObject(AntDodoRpcCall.taskList());
            JSONObject data = jo.optJSONObject("data");
            if (data == null) {
                data = jo;
            }
            JSONArray groups = data.optJSONArray("taskGroupInfoList");
            if (groups == null) {
                return false;
            }
            long now = System.currentTimeMillis();
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                JSONArray list = group == null ? null : group.optJSONArray("taskInfoList");
                collectList(captured, existing, list, now);
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

    private static void collectList(Map<String, LedgerTask> out, Map<String, LedgerTask> existing,
                                    JSONArray list, long now) {
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONArray children = item.optJSONArray("childTaskTypeList");
            if (children != null && children.length() > 0) {
                collectList(out, existing, children, now);
                continue;
            }
            JSONObject base = item.optJSONObject("taskBaseInfo");
            if (base == null) {
                continue;
            }
            String taskType = base.optString("taskType", "");
            String scene = base.optString("sceneCode", "");
            if (taskType.isEmpty()) {
                continue;
            }
            String id = "species:" + scene + ":" + taskType;
            if (out.containsKey(id)) {
                continue;
            }
            String title = titleOf(base, taskType);
            LedgerTask task = new LedgerTask();
            task.id = id;
            task.title = title;
            task.group = scene;
            task.rawStatus = base.optString("taskStatus", "");
            task.progressCurrent = base.optInt("taskProgress", 0);
            task.progressTotal = base.optInt("taskRequire", 0);
            task.award = awardText(item, base);
            task.lastSeenAt = now;
            task.updatedAt = now;
            LedgerTask old = existing.remove(id);
            task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
            task.failCount = old == null ? 0 : old.failCount;
            task.manualReason = old == null ? "" : old.manualReason;

            if ("RECEIVED".equals(task.rawStatus)) {
                task.status = LedgerStatus.CLAIMED;
            } else if ("FINISHED".equals(task.rawStatus)) {
                task.status = LedgerStatus.CLAIMABLE;
            } else if (task.failCount >= 3) {
                task.status = LedgerStatus.MANUAL;
                task.manualReason = task.manualReason == null || task.manualReason.isEmpty()
                        ? "AUTO_FAILED_3X" : task.manualReason;
            } else {
                String reason = blacklistReason(title);
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
            out.put(id, task);
        }
    }

    private static String titleOf(JSONObject base, String fallback) {
        try {
            JSONObject biz = new JSONObject(base.optString("bizInfo", "{}"));
            String title = biz.optString("taskTitle", "");
            return title.isEmpty() ? fallback : title;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String awardText(JSONObject item, JSONObject base) {
        JSONObject rights = item == null ? null : item.optJSONObject("taskRights");
        if (rights == null) {
            return "";
        }
        int count = rights.optInt("awardCount", rights.optInt("totalAwardCount", 0));
        if (count <= 0) {
            return "";
        }
        String type = rights.optString("awardType", "");
        String desc = LedgerAward.descOf(base, item.optJSONObject("taskDisplayConfig"));
        if (LedgerAward.isNone(type)) {
            return LedgerAward.fallback(desc);
        }
        if (type.contains("ANIMAL") || type.contains("CARD")) {
            return "卡片x" + count;
        }
        if (type.contains("ENERGY")) {
            return "能量x" + count;
        }
        if (type.contains("VITALITY")) {
            return "活力值x" + count;
        }
        if (type.contains("DRAW")) {
            return "抽奖机会x" + count;
        }
        if ("REWARD".equals(type) || "RIGHTS".equals(type)) {
            return "奖励x" + count;
        }
        return LedgerAward.fallback(desc);
    }

    private static String blacklistReason(String title) {
        if (MessageUtil.presetBlackList("AntDodo", BLACKLIST_FIELD).contains(title)) {
            return "PRESET_BLACKLIST";
        }
        try {
            ModelFields fields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntDodo");
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
