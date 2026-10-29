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
import io.github.aw1y2z.sesame.model.task.antFarm.AntFarmRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 庄园任务账本：饲料任务、装扮抽抽乐（每日/IP）两套列表。 */
public final class FarmTaskLedger {

    public static final String DOMAIN = "farm";
    public static final String DOMAIN_DRAW = "farm.draw";
    public static final String CARD_TASK = "task";
    public static final String CARD_DRAW = "draw";
    private static final String TAG = "FarmTaskLedger";

    private static final String SCENE_DAILY_DRAW = "ANTFARM_DAILY_DRAW_TASK";
    private static final String SCENE_IP_DRAW = "ANTFARM_IP_DRAW_TASK";

    private FarmTaskLedger() {
    }

    public static boolean captureMain(String userId) {
        try {
            JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmTask());
            if (!MessageUtil.checkMemo(TAG, jo)) {
                return false;
            }
            JSONArray list = jo.optJSONArray("farmTaskList");
            if (list == null) {
                return false;
            }
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day, DOMAIN);
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = "farm:" + firstNonEmpty(item.optString("taskId"), item.optString("bizKey"), item.optString("title"));
                tasks.add(parse(item, existing.remove(id), id, CARD_TASK, now, false));
            }
            keepServerGoneTasks(tasks, existing, now);
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "captureMain err:", t);
            return false;
        }
    }

    public static boolean captureDraw(String userId) {
        try {
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day, DOMAIN_DRAW);
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            boolean any = false;
            for (String scene : new String[]{SCENE_DAILY_DRAW, SCENE_IP_DRAW}) {
                JSONObject jo = new JSONObject(AntFarmRpcCall.listFarmDrawTask(scene));
                if (!MessageUtil.checkMemo(TAG, jo)) {
                    continue;
                }
                JSONArray list = jo.optJSONArray("farmTaskList");
                if (list == null) {
                    continue;
                }
                any = true;
                for (int i = 0; i < list.length(); i++) {
                    JSONObject item = list.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String id = "farm.draw:" + scene + ":" + firstNonEmpty(item.optString("taskId"), item.optString("bizKey"), item.optString("title"));
                    tasks.add(parse(item, existing.remove(id), id, CARD_DRAW, now, true));
                }
            }
            if (!any) {
                return false;
            }
            keepServerGoneTasks(tasks, existing, now);
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN_DRAW, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "captureDraw err:", t);
            return false;
        }
    }

    private static LedgerTask parse(JSONObject item, LedgerTask old, String id, String group,
                                    long now, boolean draw) {
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.group = group;
        task.title = item.optString("title", id);
        task.rawStatus = item.optString("taskStatus", "");
        task.award = awardText(item);
        task.progressCurrent = item.optInt("rightsTimes", 0);
        task.progressTotal = item.optInt("rightsTimesLimit", 0);
        task.firstSeenAt = old == null || old.firstSeenAt <= 0 ? now : old.firstSeenAt;
        task.lastSeenAt = now;
        task.updatedAt = now;
        task.failCount = old == null ? 0 : old.failCount;
        task.manualReason = old == null ? "" : old.manualReason;

        String status = task.rawStatus;
        if ("RECEIVED".equals(status)) {
            task.status = LedgerStatus.CLAIMED;
            task.failCount = 0;
            task.manualReason = "";
        } else if ("FINISHED".equals(status)) {
            task.status = LedgerStatus.CLAIMABLE;
            task.failCount = 0;
            task.manualReason = "";
        } else if (old != null && old.failCount >= 3) {
            task.status = LedgerStatus.MANUAL;
            task.manualReason = old.manualReason == null || old.manualReason.isEmpty()
                    ? "AUTO_FAILED_3X" : old.manualReason;
        } else {
            String reason = blacklistReason(task.title, draw);
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

    private static String awardText(JSONObject item) {
        String type = item.optString("awardType", "");
        int count = item.optInt("awardCount", 0);
        if (count <= 0) {
            return "";
        }
        String desc = firstNonEmpty(
                item.optString("taskDesc", ""),
                item.optString("desc", "")
        );
        if (LedgerAward.isNone(type)) {
            return LedgerAward.fallback(desc);
        }
        if ("ALLPURPOSE".equals(type)) {
            return "饲料x" + count;
        }
        if ("ENERGY".equals(type)) {
            return "能量x" + count;
        }
        if ("CUISINE".equals(type)) {
            return "食材x" + count;
        }
        if ("DAILY_DRAW_TIMES".equals(type)) {
            return "每日抽奖机会x" + count;
        }
        if ("IP_DRAW_MACHINE_DRAW_TIMES".equals(type)) {
            return "联动抽奖机会x" + count;
        }
        return "奖励x" + count;
    }

    private static String blacklistReason(String title, boolean draw) {
        String listTitle = draw ? "AntFarmDrawMachineTaskList" : "AntFarmDoFarmTaskList";
        if (MessageUtil.presetBlackList("AntFarm", listTitle).contains(title)) {
            return "PRESET_BLACKLIST";
        }
        try {
            ModelFields fields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntFarm");
            ModelField field = fields == null ? null : fields.get(listTitle);
            if (field instanceof SelectModelField
                    && ((SelectModelField) field).getValue() != null
                    && ((SelectModelField) field).getValue().contains(title)) {
                return "BLACKLISTED";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static Map<String, LedgerTask> loadExisting(String userId, String day, String domain) {
        Map<String, LedgerTask> map = new LinkedHashMap<>();
        for (LedgerTask task : TaskLedgerStore.loadTasks(userId, day, domain)) {
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
