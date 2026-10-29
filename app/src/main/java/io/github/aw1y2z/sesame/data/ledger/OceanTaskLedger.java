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
import io.github.aw1y2z.sesame.model.task.antOcean.AntOceanRpcCall;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.MessageUtil;

/** 神奇海洋·奖励任务账本：只读抓取任务列表，归一化为 6 态。 */
public final class OceanTaskLedger {

    public static final String DOMAIN = "ocean";
    public static final String DOMAIN_FISH = "ocean.fish";
    public static final String CARD_REWARD = "reward";
    public static final String CARD_FISH = "fish";
    private static final String TAG = "OceanTaskLedger";

    private OceanTaskLedger() {
    }

    /** 只读抓取当天海洋任务列表并落账本。不完成任务、不领奖。 */
    public static boolean capture(String userId) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.queryTaskList());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray array = jo.optJSONArray("antOceanTaskVOList");
            if (array == null) {
                return false;
            }
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = new LinkedHashMap<>();
            for (LedgerTask task : TaskLedgerStore.loadTasks(userId, day, DOMAIN)) {
                existing.put(task.id, task);
            }
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < array.length(); i++) {
                JSONObject raw = array.optJSONObject(i);
                if (raw == null) {
                    continue;
                }
                LedgerTask task = parse(raw, existing.remove(taskId(raw)), now);
                tasks.add(task);
            }
            // 服务端已不再下发的任务保留在账本里，标为已跳过，避免面板数字忽大忽小。
            for (LedgerTask old : existing.values()) {
                old.status = LedgerStatus.SKIPPED;
                old.manualReason = "SERVER_GONE";
                old.lastSeenAt = now;
                old.updatedAt = now;
                tasks.add(old);
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "capture err:", t);
            return false;
        }
    }

    /** 只读抓取当天摸鱼任务列表，写到独立的 ocean.fish 账本，不和奖励任务混在一起。 */
    public static boolean captureFish(String userId) {
        try {
            JSONObject jo = new JSONObject(AntOceanRpcCall.antfishListTask());
            if (!MessageUtil.checkResultCode(TAG, jo)) {
                return false;
            }
            JSONArray array = jo.optJSONArray("taskInfoList");
            if (array == null) {
                return false;
            }
            String day = TaskLedgerStore.today();
            Map<String, LedgerTask> existing = new LinkedHashMap<>();
            for (LedgerTask task : TaskLedgerStore.loadTasks(userId, day, DOMAIN_FISH)) {
                existing.put(task.id, task);
            }
            List<LedgerTask> tasks = new ArrayList<>();
            long now = System.currentTimeMillis();
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                LedgerTask task = parseFish(item, existing.remove(fishTaskId(item)), now);
                if (task != null) {
                    tasks.add(task);
                }
            }
            for (LedgerTask old : existing.values()) {
                old.status = LedgerStatus.SKIPPED;
                old.manualReason = "SERVER_GONE";
                old.lastSeenAt = now;
                old.updatedAt = now;
                tasks.add(old);
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN_FISH, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "captureFish err:", t);
            return false;
        }
    }

    public static boolean recordFailure(String userId, String sceneCode, String taskType, String reason) {
        return recordFailure(userId, sceneCode, taskType, reason, false);
    }

    /** immediate=true 用于结构性失败（外跳/真实游戏/接口不支持），当天直接标需手动。 */
    public static boolean recordFailure(String userId, String sceneCode, String taskType,
                                        String reason, boolean immediate) {
        try {
            String day = TaskLedgerStore.today();
            List<LedgerTask> tasks = TaskLedgerStore.loadTasks(userId, day, DOMAIN);
            String id = taskId(sceneCode, taskType);
            LedgerTask target = null;
            for (LedgerTask task : tasks) {
                if (id.equals(task.id)) {
                    target = task;
                    break;
                }
            }
            if (target == null) {
                target = new LedgerTask();
                target.id = id;
                target.title = taskType;
                target.group = CARD_REWARD;
                target.rawStatus = "TODO";
                target.firstSeenAt = System.currentTimeMillis();
                tasks.add(target);
            }
            target.failCount++;
            target.updatedAt = System.currentTimeMillis();
            target.lastSeenAt = target.updatedAt;
            if (immediate || target.failCount >= 3) {
                target.failCount = Math.max(3, target.failCount);
                target.status = LedgerStatus.MANUAL;
                target.manualReason = reason == null || reason.isEmpty() ? "AUTO_FAILED_3X" : reason;
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "recordFailure err:", t);
            return false;
        }
    }

    public static boolean recordSuccess(String userId, String sceneCode, String taskType, LedgerStatus status) {
        try {
            String day = TaskLedgerStore.today();
            List<LedgerTask> tasks = TaskLedgerStore.loadTasks(userId, day, DOMAIN);
            String id = taskId(sceneCode, taskType);
            for (LedgerTask task : tasks) {
                if (id.equals(task.id)) {
                    task.status = status;
                    task.failCount = 0;
                    task.manualReason = "";
                    task.updatedAt = System.currentTimeMillis();
                    task.lastSeenAt = task.updatedAt;
                    break;
                }
            }
            return TaskLedgerStore.saveDomainTasks(userId, day, DOMAIN, "ok", tasks, true);
        } catch (Throwable t) {
            Log.err(TAG, "recordSuccess err:", t);
            return false;
        }
    }

    /** 奖励目标的条件：只把“可不做就待领 + 可自动未做”算可执行；纯进度任务不触发计划。 */
    public static boolean isActionable(String userId) {
        JSONObject summary = TaskLedgerStore.loadDomainSummary(userId, TaskLedgerStore.today(), DOMAIN);
        if (summary == null) {
            return true;
        }
        return summary.optInt("claimable", 0) > 0 || summary.optInt("untouched", 0) > 0;
    }

    public static List<LedgerTask> loadTasks(String userId) {
        return TaskLedgerStore.loadTasks(userId, TaskLedgerStore.today(), DOMAIN);
    }

    private static LedgerTask parse(JSONObject raw, LedgerTask old, long now) {
        LedgerTask task = new LedgerTask();
        task.id = taskId(raw);
        task.group = CARD_REWARD;
        task.rawStatus = raw.optString("taskStatus", "");
        task.title = title(raw);
        task.progressCurrent = optInt(raw.optString("taskProgress", ""));
        task.progressTotal = optInt(raw.optString("taskRequire", ""));
        task.award = award(raw);
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
        } else if (!blacklistReason(task.title).isEmpty()) {
            String reason = blacklistReason(task.title);
            task.status = "PRESET_BLACKLIST".equals(reason) ? LedgerStatus.SKIPPED : LedgerStatus.MANUAL;
            task.manualReason = reason;
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

    private static String title(JSONObject raw) {
        try {
            JSONObject biz = new JSONObject(raw.optString("bizInfo", "{}"));
            String title = biz.optString("taskTitle", "");
            if (!title.isEmpty()) {
                return title;
            }
            String desc = biz.optString("taskDesc", "");
            if (!desc.isEmpty()) {
                return desc;
            }
        } catch (Throwable ignored) {
        }
        return raw.optString("taskType", "");
    }

    private static String award(JSONObject raw) {
        String type = raw.optString("awardType", "");
        int count = optInt(raw.optString("awardCount", ""));
        if (count <= 0) {
            return "";
        }
        String desc = LedgerAward.descOf(raw, null);
        if (LedgerAward.isNone(type)) {
            return LedgerAward.fallback(desc);
        }
        if ("RIGHTS".equals(type)) {
            return "拼图x" + count;
        }
        if ("ENERGY".equals(type)) {
            return "能量x" + count;
        }
        if (type.contains("VITALITY")) {
            return "活力值x" + count;
        }
        if (type.contains("DRAW")) {
            return "抽奖机会x" + count;
        }
        return "奖励x" + count;
    }

    private static String taskId(JSONObject raw) {
        return taskId(raw.optString("sceneCode", ""), raw.optString("taskType", ""));
    }

    private static String taskId(String sceneCode, String taskType) {
        return "ocean:" + (sceneCode == null ? "" : sceneCode) + ":" + (taskType == null ? "" : taskType);
    }

    private static int optInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static LedgerTask parseFish(JSONObject item, LedgerTask old, long now) {
        JSONObject base = item.optJSONObject("taskBaseInfo");
        if (base == null) {
            return null;
        }
        LedgerTask task = new LedgerTask();
        task.id = fishTaskId(item);
        task.group = CARD_FISH;
        task.rawStatus = base.optString("taskStatus", "");
        task.title = fishTitle(base);
        task.actionUrl = fishJumpUrl(base);
        task.progressCurrent = base.optInt("taskProgress", 0);
        task.progressTotal = base.optInt("taskRequire", 0);
        JSONObject rights = item.optJSONObject("taskRights");
        int award = rights == null ? 0 : rights.optInt("awardCount", 0);
        task.award = award > 0 ? "次数x" + award : "";
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
            String reason = blacklistReasonFish(task.title);
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

    private static String fishTitle(JSONObject base) {
        try {
            JSONObject biz = new JSONObject(base.optString("bizInfo", "{}"));
            String title = biz.optString("taskTitle", "");
            if (!title.isEmpty()) {
                return title;
            }
        } catch (Throwable ignored) {
        }
        return base.optString("taskType", "");
    }

    private static String fishJumpUrl(JSONObject base) {
        try {
            JSONObject biz = new JSONObject(base.optString("bizInfo", "{}"));
            String url = biz.optString("taskJumpUrl", "");
            if (url.isEmpty()) {
                url = biz.optString("targetUrl", "");
            }
            return url;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String fishTaskId(JSONObject item) {
        JSONObject base = item.optJSONObject("taskBaseInfo");
        if (base == null) {
            return "ocean.fish::";
        }
        return "ocean.fish:" + base.optString("sceneCode", "") + ":" + base.optString("taskType", "");
    }

    private static String blacklistReasonFish(String title) {
        if (MessageUtil.presetBlackList("AntOcean", "AntOceanFishBlackList").contains(title)) {
            return "PRESET_BLACKLIST";
        }
        try {
            ModelFields fields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntOcean");
            ModelField field = fields == null ? null : fields.get("AntOceanFishBlackList");
            if (field instanceof SelectModelField
                    && ((SelectModelField) field).getValue() != null
                    && ((SelectModelField) field).getValue().contains(title)) {
                return "BLACKLISTED";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /** 只认真正的黑名单：预置项或配置里的海洋任务黑名单；task 名称缓存表不是黑名单。 */
    private static String blacklistReason(String title) {
        if (MessageUtil.presetBlackList("AntOcean", "AntOceanAntiepTaskList").contains(title)) {
            return "PRESET_BLACKLIST";
        }
        try {
            ModelFields fields = ConfigV2.INSTANCE.getModelFieldsMap().get("AntOcean");
            ModelField field = fields == null ? null : fields.get("AntOceanAntiepTaskList");
            if (field instanceof SelectModelField
                    && ((SelectModelField) field).getValue() != null
                    && ((SelectModelField) field).getValue().contains(title)) {
                return "BLACKLISTED";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }
}
