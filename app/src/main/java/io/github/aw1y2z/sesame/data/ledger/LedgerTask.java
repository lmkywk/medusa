package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONObject;

/** 账本里的一条叶子任务；父任务只作为分组，不单独计数。 */
public class LedgerTask {

    public String id = "";
    public String title = "";
    public String group = "";
    /** 账本文件所在域（ocean.fish / farm / forest.reward…）；加载时填充，便于跨域手动队列。 */
    public String domain = "";
    public String rawStatus = "";
    public LedgerStatus status = LedgerStatus.UNTOUCHED;
    public int progressCurrent;
    public int progressTotal;
    public String award = "";
    public String manualReason = "";
    /** 手动任务的服务端跳转链接；可能过期，展示前重新拉账本。 */
    public String actionUrl = "";
    /** 用户点“我已完成”的时间；>0 表示待下次账本复核。 */
    public long userDoneAt;
    /** 能力分类：机制|域|端点。同端点才能互相推断通不通。 */
    public String classKey = "";
    public String mechanism = "";
    public String costType = "";
    public String endpoint = "";
    public int failCount;
    public long firstSeenAt;
    public long lastSeenAt;
    public long updatedAt;
    public String lastRunId = "";

    public JSONObject toJson() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("id", id);
            jo.put("title", title);
            jo.put("group", group);
            jo.put("domain", domain);
            jo.put("rawStatus", rawStatus);
            jo.put("status", status.name());
            jo.put("progressCurrent", progressCurrent);
            jo.put("progressTotal", progressTotal);
            jo.put("award", award);
            jo.put("manualReason", manualReason);
            jo.put("actionUrl", actionUrl);
            jo.put("userDoneAt", userDoneAt);
            jo.put("classKey", classKey);
            jo.put("mechanism", mechanism);
            jo.put("costType", costType);
            jo.put("endpoint", endpoint);
            jo.put("failCount", failCount);
            jo.put("firstSeenAt", firstSeenAt);
            jo.put("lastSeenAt", lastSeenAt);
            jo.put("updatedAt", updatedAt);
            jo.put("lastRunId", lastRunId);
        } catch (Throwable ignored) {
        }
        return jo;
    }

    public static LedgerTask fromJson(JSONObject jo) {
        LedgerTask task = new LedgerTask();
        if (jo == null) {
            return task;
        }
        task.id = jo.optString("id", "");
        task.title = jo.optString("title", "");
        task.group = jo.optString("group", "");
        task.domain = jo.optString("domain", "");
        task.rawStatus = jo.optString("rawStatus", "");
        task.status = LedgerStatus.fromName(jo.optString("status", ""));
        task.progressCurrent = jo.optInt("progressCurrent", 0);
        task.progressTotal = jo.optInt("progressTotal", 0);
        task.award = jo.optString("award", "");
        task.manualReason = jo.optString("manualReason", "");
        task.actionUrl = jo.optString("actionUrl", "");
        task.userDoneAt = jo.optLong("userDoneAt", 0L);
        task.classKey = jo.optString("classKey", "");
        task.mechanism = jo.optString("mechanism", "");
        task.costType = jo.optString("costType", "");
        task.endpoint = jo.optString("endpoint", "");
        task.failCount = jo.optInt("failCount", 0);
        task.firstSeenAt = jo.optLong("firstSeenAt", 0L);
        task.lastSeenAt = jo.optLong("lastSeenAt", 0L);
        task.updatedAt = jo.optLong("updatedAt", 0L);
        task.lastRunId = jo.optString("lastRunId", "");
        return task;
    }

    public String progressText() {
        if (progressTotal <= 0) {
            return "";
        }
        return progressCurrent + "/" + progressTotal;
    }
}
