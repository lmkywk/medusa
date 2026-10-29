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

/** 阿肥寻宝记（农场抽抽乐）任务账本：每日签到、浏览/游戏任务与抽奖机会。 */
public final class OrchardDrawTaskLedger {

    public static final String DOMAIN = "orchard.draw";
    private static final String TAG = "OrchardDrawTaskLedger";
    private static final String BLACKLIST_FIELD = "OrchardChouChouLeTaskList";
    private static final String ENTER_SCENE = "ANTORCHARD_DRAW_TIMES";

    private OrchardDrawTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            JSONObject enter = new JSONObject(AntOrchardRpcCall.enterDrawActivityantorchard("", ENTER_SCENE, "antorchard"));
            if (!"100000000".equals(enter.optString("code")) && !MessageUtil.checkSuccess(TAG, enter)) {
                return false;
            }
            JSONObject activity = enter.optJSONObject("drawActivity");
            if (activity == null) {
                JSONObject scene = enter.optJSONObject("drawScene");
                activity = scene == null ? null : scene.optJSONObject("drawActivity");
            }
            String activityId = activity == null ? "" : activity.optString("activityId", "");
            String mainScene = activity == null ? ENTER_SCENE : activity.optString("sceneCode", ENTER_SCENE);

            JSONArray scenes = enter.optJSONArray("drawSceneGroups");
            if (scenes == null) {
                JSONObject scene = enter.optJSONObject("drawScene");
                scenes = new JSONArray();
                if (scene != null) {
                    scenes.put(scene);
                }
            }

            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < scenes.length(); i++) {
                JSONObject group = scenes.optJSONObject(i);
                if (group == null) {
                    continue;
                }
                JSONObject drawActivity = group.optJSONObject("drawActivity");
                String sceneCode = drawActivity == null
                        ? group.optString("sceneCode", mainScene)
                        : drawActivity.optString("sceneCode", mainScene);
                JSONObject listJo = new JSONObject(AntOrchardRpcCall.listTaskantorchard(sceneCode + "_TASK", "antorchard"));
                if (!MessageUtil.checkSuccess(TAG, listJo)) {
                    continue;
                }
                JSONArray list = listJo.optJSONArray("taskInfoList");
                if (list == null) {
                    continue;
                }
                for (int j = 0; j < list.length(); j++) {
                    JSONObject item = list.optJSONObject(j);
                    if (item == null) {
                        continue;
                    }
                    JSONObject base = item.optJSONObject("taskBaseInfo");
                    if (base == null) {
                        continue;
                    }
                    String taskType = base.optString("taskType", "");
                    if (taskType.isEmpty()) {
                        continue;
                    }
                    String id = "orchard.draw:" + sceneCode + ":" + taskType;
                    tasks.add(parse(item, base, existing.remove(id), id, sceneCode, now));
                }
            }
            keepServerGoneTasks(tasks, existing, now);
            boolean saved = TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
            OrchardStatus.captureDraw(userId, enter.optJSONObject("drawAsset"), activityId, mainScene);
            // 活动结束/未开放也落一份空账本，避免 00:01 无休止补建
            return saved;
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    private static LedgerTask parse(JSONObject item, JSONObject base, LedgerTask old,
                                    String id, String scene, long now) {
        JSONObject biz = null;
        try {
            biz = new JSONObject(base.optString("bizInfo", "{}"));
        } catch (Throwable ignored) {
        }
        JSONObject rights = item.optJSONObject("taskRights");
        String taskType = base.optString("taskType", "");
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.group = scene;
        task.title = biz == null ? taskType : biz.optString("title", taskType);
        task.rawStatus = base.optString("taskStatus", "");
        int timesLimit = rights == null ? 0 : rights.optInt("rightsTimesLimit", 0);
        int times = rights == null ? 0 : rights.optInt("rightsTimes", 0);
        task.progressCurrent = timesLimit > 0 ? times : base.optInt("taskProgress", 0);
        task.progressTotal = timesLimit > 0 ? timesLimit : base.optInt("taskRequire", 0);
        int awardCount = rights == null ? 0 : rights.optInt("awardCount", 0);
        task.award = awardCount > 0 ? "抽奖机会x" + awardCount : "";
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
