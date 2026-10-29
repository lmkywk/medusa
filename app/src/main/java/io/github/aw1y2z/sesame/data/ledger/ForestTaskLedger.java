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

/** 森林任务账本：forestTasksNew + listTaskopengreen 两套列表，叶子任务才入账。 */
public final class ForestTaskLedger {

    public static final String DOMAIN = "forest.reward";
    private static final String TAG = "ForestTaskLedger";
    private static final String BLACKLIST_FIELD = "AntForestVitalityTaskList";

    private ForestTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            Map<String, LedgerTask> captured = new LinkedHashMap<>();
            boolean fetched = collectFirstList(captured, existing);
            fetched = collectOpenGreen(captured, existing) || fetched;
            if (!fetched) {
                return false;
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

    private static boolean collectFirstList(Map<String, LedgerTask> out, Map<String, LedgerTask> existing) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.queryTaskList());
            JSONArray groups = jo.optJSONArray("forestTasksNew");
            if (groups == null) {
                return false;
            }
            for (int i = 0; i < groups.length(); i++) {
                JSONObject group = groups.optJSONObject(i);
                JSONArray list = group == null ? null : group.optJSONArray("taskInfoList");
                collectList(out, existing, list);
            }
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "collectFirstList err:", t);
            return false;
        }
    }

    private static boolean collectOpenGreen(Map<String, LedgerTask> out, Map<String, LedgerTask> existing) {
        try {
            JSONObject jo = new JSONObject(AntForestRpcCall.listTaskopengreen());
            JSONArray list = jo.optJSONArray("taskInfoList");
            if (list == null) {
                return false;
            }
            collectList(out, existing, list);
            return true;
        } catch (Throwable t) {
            Log.err(TAG, "collectOpenGreen err:", t);
            return false;
        }
    }

    private static void collectList(Map<String, LedgerTask> out, Map<String, LedgerTask> existing,
                                    JSONArray list) {
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
                collectList(out, existing, children);
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
            String id = "forest:" + scene + ":" + taskType;
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
            task.award = awardText(item);
            task.firstSeenAt = existing.containsKey(id) ? existing.get(id).firstSeenAt : System.currentTimeMillis();
            task.lastSeenAt = System.currentTimeMillis();
            task.updatedAt = task.lastSeenAt;

            LedgerTask old = existing.get(id);
            task.failCount = old == null ? 0 : old.failCount;
            task.manualReason = old == null ? "" : old.manualReason;
            if ("RECEIVED".equals(task.rawStatus)) {
                task.status = LedgerStatus.CLAIMED;
            } else if ("FINISHED".equals(task.rawStatus)) {
                task.status = LedgerStatus.CLAIMABLE;
            } else if ("TODO".equals(task.rawStatus)
                    && io.github.aw1y2z.sesame.model.base.TaskAlternative.isBountyCardTask(taskType)) {
                // 悬赏卡：缴费/出行/回收/线下支付等真实行为，脚本不做，标需手动
                task.status = LedgerStatus.MANUAL;
                task.manualReason = "BOUNTY_CARD";
                task.failCount = 0;
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
            existing.remove(id);
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

    private static String awardText(JSONObject item) {
        JSONObject rights = item == null ? null : item.optJSONObject("taskRights");
        if (rights == null) {
            return "";
        }
        int count = rights.optInt("awardCount", rights.optInt("totalAwardCount", 0));
        if (count <= 0) {
            return "";
        }
        String type = rights.optString("awardType", "");
        String desc = LedgerAward.descOf(
                item.optJSONObject("taskBaseInfo"),
                item.optJSONObject("taskDisplayConfig")
        );
        if (LedgerAward.isNone(type)) {
            return LedgerAward.fallback(desc);
        }
        if (type.contains("VITALITY")) {
            return "活力值x" + count;
        }
        if (type.contains("ENERGY")) {
            return "能量x" + count;
        }
        if (type.contains("MANURE") || type.contains("FERTILIZER")) {
            return "肥料x" + count;
        }
        if (type.contains("CARD") || type.contains("ANIMAL")) {
            return "卡片x" + count;
        }
        if ("LUCKY_DRAW".equals(type) || type.contains("DRAW")) {
            return "抽奖机会x" + count;
        }
        if ("REWARD".equals(type) || "RIGHTS".equals(type)) {
            return "奖励x" + count;
        }
        return LedgerAward.fallback(desc);
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
