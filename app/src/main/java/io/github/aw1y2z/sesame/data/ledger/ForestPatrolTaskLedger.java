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

/** 新版保护地巡护任务账本：listMonopolyTasks，仅在红山动物园区域有任务。 */
public final class ForestPatrolTaskLedger {

    public static final String DOMAIN = "forest.patrol";
    private static final String TAG = "ForestPatrolTaskLedger";
    private static final String REGION_HSDWY = "hongshandongwuyuan";
    private static final String SCENE_HSDWY = "ANTFOREST_MONOPOLY_TASK_HSDWY";
    private static final String BLACKLIST_FIELD = "MonopolyTaskList";

    private ForestPatrolTaskLedger() {
    }

    public static boolean capture(String userId) {
        try {
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = loadExisting(userId, day);
            Map<String, LedgerTask> captured = new LinkedHashMap<>();
            String regionCode = "";
            JSONObject entry = new JSONObject(AntForestRpcCall.queryMonopolyEntryInfo());
            if (MessageUtil.checkResultCode(TAG, entry)) {
                JSONObject region = entry.optJSONObject("regionInfo");
                regionCode = region == null ? "" : region.optString("regionCode", "");
            }
            long now = System.currentTimeMillis();
            boolean fetched = false;
            if (REGION_HSDWY.equals(regionCode)) {
                JSONObject jo = new JSONObject(AntForestRpcCall.listMonopolyTasks(REGION_HSDWY, SCENE_HSDWY));
                if (MessageUtil.checkSuccess(TAG, jo)) {
                    fetched = true;
                    JSONArray list = jo.optJSONArray("taskInfoList");
                    if (list != null) {
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject task = list.optJSONObject(i);
                            JSONObject base = task == null ? null : task.optJSONObject("taskBaseInfo");
                            if (base == null) {
                                continue;
                            }
                            String taskType = base.optString("taskType", "");
                            String scene = base.optString("sceneCode", "");
                            if (taskType.isEmpty() || !SCENE_HSDWY.equals(scene)) {
                                continue;
                            }
                            String id = "forest.patrol:" + scene + ":" + taskType;
                            String title = titleOf(base, taskType);
                            LedgerTask lt = new LedgerTask();
                            lt.id = id;
                            lt.title = title;
                            lt.group = scene;
                            lt.rawStatus = base.optString("taskStatus", "");
                            lt.award = "巡护奖励";
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
                                    lt.status = "PRESET_BLACKLIST".equals(reason)
                                            ? LedgerStatus.SKIPPED : LedgerStatus.MANUAL;
                                    lt.manualReason = reason;
                                } else if ("TODO".equals(lt.rawStatus)) {
                                    lt.status = LedgerStatus.UNTOUCHED;
                                } else {
                                    lt.status = LedgerStatus.UNTOUCHED;
                                }
                            }
                            if (lt.status != LedgerStatus.MANUAL && lt.status != LedgerStatus.SKIPPED) {
                                lt.manualReason = "";
                            }
                            captured.put(id, lt);
                        }
                    }
                }
            } else if (!regionCode.isEmpty()) {
                // 其他区域没有任务列表，按“已查、空列表”处理，卡片显示无任务
                fetched = true;
            }
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

    private static String titleOf(JSONObject base, String fallback) {
        try {
            JSONObject biz = new JSONObject(base.optString("bizInfo", "{}"));
            String title = biz.optString("title", biz.optString("taskTitle", ""));
            return title.isEmpty() ? fallback : title;
        } catch (Throwable ignored) {
            return fallback;
        }
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
