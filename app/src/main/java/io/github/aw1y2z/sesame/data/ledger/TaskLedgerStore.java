package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 每日任务账本存储：snapshot 给任务列表，summary 给面板数字。
 * <p>写入由 {@link FileUtil#write2File} 做临时文件 + 替换，避免模块 UI 与注入进程交叉写坏。
 */
public final class TaskLedgerStore {

    /** v3：任务补 capability（classKey/机制/成本/端点），供按类分组与类级推断。 */
    public static final int SCHEMA_VERSION = 3;

    /** 跨域手动队列的虚拟域；不落盘。 */
    public static final String DOMAIN_MANUAL = "__manual__";

    private static final String TAG = "TaskLedgerStore";
    private static final Object LOCK = new Object();

    private TaskLedgerStore() {
    }

    public static String today() {
        return dayOf(System.currentTimeMillis());
    }

    public static String dayOf(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(millis));
    }

    public static File getLedgerRoot() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, "ledger");
    }

    public static File getDayDir(String userId, String day) {
        return new File(new File(getLedgerRoot(), safeUser(userId)), day);
    }

    public static File getSnapshotFile(String userId, String day) {
        return new File(getDayDir(userId, day), "snapshot.json");
    }

    public static File getSummaryFile(String userId, String day) {
        return new File(getDayDir(userId, day), "summary.json");
    }

    public static JSONObject loadSnapshot(String userId, String day) {
        synchronized (LOCK) {
            try {
                File file = getSnapshotFile(userId, day);
                if (!file.exists()) {
                    return null;
                }
                String json = FileUtil.readFromFile(file);
                if (json == null || json.trim().isEmpty()) {
                    return null;
                }
                return new JSONObject(json);
            } catch (Throwable t) {
                Log.err(TAG, "loadSnapshot err:", t);
                return null;
            }
        }
    }

    public static File getReportFile(String userId, String day) {
        return new File(getDayDir(userId, day), "report.json");
    }

    public static File getProgressFile(String userId, String day) {
        return new File(getDayDir(userId, day), "progress.json");
    }

    /**
     * 当天已被执行器处理过的任务（覆盖层）：账本只在同步时刷新，执行中的进度先落这里，
     * 让计划卡/首页不必等下一次抓取。按 title 去重，跨进程可读。
     */
    public static JSONObject loadResolved(String userId, String day) {
        synchronized (LOCK) {
            try {
                File file = getProgressFile(userId, day);
                if (!file.exists()) {
                    return null;
                }
                String json = FileUtil.readFromFile(file);
                return json == null || json.trim().isEmpty() ? null : new JSONObject(json);
            } catch (Throwable t) {
                Log.err(TAG, "loadResolved err:", t);
                return null;
            }
        }
    }

    public static boolean addResolved(String userId, String day, String title, String outcome) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            try {
                JSONObject root = loadResolved(userId, day);
                if (root == null) {
                    root = new JSONObject();
                    root.put("day", day);
                    root.put("handled", new JSONObject());
                }
                JSONObject handled = root.optJSONObject("handled");
                if (handled == null) {
                    handled = new JSONObject();
                    root.put("handled", handled);
                }
                JSONObject item = new JSONObject();
                item.put("at", System.currentTimeMillis());
                item.put("outcome", outcome == null ? "" : outcome);
                handled.put(title.trim(), item);
                root.put("updatedAt", System.currentTimeMillis());
                root.put("schemaVersion", SCHEMA_VERSION);
                return FileUtil.write2File(root.toString(), getProgressFile(userId, day));
            } catch (Throwable t) {
                Log.err(TAG, "addResolved err:", t);
                return false;
            }
        }
    }

    public static JSONObject loadReport(String userId, String day) {
        synchronized (LOCK) {
            try {
                File file = getReportFile(userId, day);
                if (!file.exists()) {
                    return null;
                }
                String json = FileUtil.readFromFile(file);
                return json == null || json.trim().isEmpty() ? null : new JSONObject(json);
            } catch (Throwable t) {
                Log.err(TAG, "loadReport err:", t);
                return null;
            }
        }
    }

    /**
     * 每日账本报告：今日各域计数 + 与昨天快照对比的新增/下架/状态变化。
     * 只读快照，不触发任何业务 RPC。
     */
    public static boolean writeDailyReport(String userId, String day) {
        synchronized (LOCK) {
            try {
                JSONObject today = loadSnapshot(userId, day);
                if (today == null) {
                    return false;
                }
                String previousDay = dayOf(System.currentTimeMillis() - 24L * 60L * 60L * 1000L);
                JSONObject previous = loadSnapshot(userId, previousDay);
                JSONObject todayDomains = today.optJSONObject("domains");
                if (todayDomains == null) {
                    return false;
                }
                JSONObject previousDomains = previous == null ? null : previous.optJSONObject("domains");
                JSONObject reportDomains = new JSONObject();
                int totalAll = 0;
                int pendingAll = 0;
                int manualAll = 0;
                java.util.Iterator<String> keys = todayDomains.keys();
                while (keys.hasNext()) {
                    String domain = keys.next();
                    JSONObject domainObj = todayDomains.optJSONObject(domain);
                    JSONArray tasks = domainObj == null ? null : domainObj.optJSONArray("tasks");
                    if (tasks == null) {
                        continue;
                    }
                    JSONObject previousDomain = previousDomains == null ? null : previousDomains.optJSONObject(domain);
                    JSONArray previousTasks = previousDomain == null ? null : previousDomain.optJSONArray("tasks");
                    java.util.Map<String, JSONObject> before = new java.util.LinkedHashMap<>();
                    for (int i = 0; previousTasks != null && i < previousTasks.length(); i++) {
                        JSONObject task = previousTasks.optJSONObject(i);
                        if (task != null) {
                            before.put(task.optString("id", ""), task);
                        }
                    }
                    int total = 0;
                    int pending = 0;
                    int claimable = 0;
                    int claimed = 0;
                    int manual = 0;
                    int skipped = 0;
                    int inProgress = 0;
                    int untouched = 0;
                    JSONArray newTasks = new JSONArray();
                    JSONArray removedTasks = new JSONArray();
                    int newCount = 0;
                    int removedCount = 0;
                    java.util.Set<String> todayIds = new java.util.HashSet<>();
                    for (int i = 0; i < tasks.length(); i++) {
                        JSONObject task = tasks.optJSONObject(i);
                        if (task == null) {
                            continue;
                        }
                        total++;
                        todayIds.add(task.optString("id", ""));
                        String status = task.optString("status", "");
                        if ("UNTOUCHED".equals(status)) untouched++;
                        else if ("IN_PROGRESS".equals(status)) inProgress++;
                        else if ("CLAIMABLE".equals(status)) claimable++;
                        else if ("CLAIMED".equals(status)) claimed++;
                        else if ("MANUAL".equals(status)) manual++;
                        else if ("SKIPPED".equals(status)) skipped++;
                        if ("UNTOUCHED".equals(status) || "IN_PROGRESS".equals(status) || "CLAIMABLE".equals(status)) {
                            pending++;
                        }
                        if (previousTasks != null) {
                            JSONObject old = before.get(task.optString("id", ""));
                            if (old == null) {
                                newCount++;
                                if (newTasks.length() < 8) {
                                    newTasks.put(task.optString("title", task.optString("id", "")));
                                }
                            }
                        }
                    }
                    if (previousTasks != null) {
                        for (String oldId : before.keySet()) {
                            if (!todayIds.contains(oldId)) {
                                removedCount++;
                                if (removedTasks.length() < 8) {
                                    JSONObject old = before.get(oldId);
                                    removedTasks.put(old == null ? oldId : old.optString("title", oldId));
                                }
                            }
                        }
                    }
                    JSONObject item = new JSONObject();
                    item.put("total", total);
                    item.put("pending", pending);
                    item.put("claimable", claimable);
                    item.put("claimed", claimed);
                    item.put("manual", manual);
                    item.put("skipped", skipped);
                    item.put("inProgress", inProgress);
                    item.put("untouched", untouched);
                    item.put("newTasks", newTasks);
                    item.put("removedTasks", removedTasks);
                    item.put("newCount", newCount);
                    item.put("removedCount", removedCount);
                    reportDomains.put(domain, item);
                    totalAll += total;
                    pendingAll += pending;
                    manualAll += manual;
                }
                JSONObject report = new JSONObject();
                report.put("schemaVersion", SCHEMA_VERSION);
                report.put("day", day);
                report.put("syncedAt", System.currentTimeMillis());
                report.put("total", totalAll);
                report.put("pending", pendingAll);
                report.put("manual", manualAll);
                report.put("domains", reportDomains);
                return FileUtil.write2File(report.toString(), getReportFile(userId, day));
            } catch (Throwable t) {
                Log.err(TAG, "writeDailyReport err:", t);
                return false;
            }
        }
    }

    public static JSONObject loadSummary(String userId, String day) {
        synchronized (LOCK) {
            try {
                File file = getSummaryFile(userId, day);
                if (!file.exists()) {
                    return null;
                }
                String json = FileUtil.readFromFile(file);
                if (json == null || json.trim().isEmpty()) {
                    return null;
                }
                return new JSONObject(json);
            } catch (Throwable t) {
                Log.err(TAG, "loadSummary err:", t);
                return null;
            }
        }
    }

    public static List<LedgerTask> loadTasks(String userId, String day, String domain) {
        List<LedgerTask> result = new ArrayList<>();
        JSONObject snapshot = loadSnapshot(userId, day);
        if (snapshot == null) {
            return result;
        }
        JSONObject domains = snapshot.optJSONObject("domains");
        JSONObject domainObj = domains == null ? null : domains.optJSONObject(domain);
        JSONArray tasks = domainObj == null ? null : domainObj.optJSONArray("tasks");
        if (tasks == null) {
            return result;
        }
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null) {
                LedgerTask item = LedgerTask.fromJson(task);
                item.domain = domain;
                result.add(item);
            }
        }
        return result;
    }

    /** 跨域手动队列：读取当天所有域的 MANUAL 任务；只读，不改账本。 */
    public static List<LedgerTask> loadManualTasks(String userId, String day) {
        List<LedgerTask> result = new ArrayList<>();
        JSONObject snapshot = loadSnapshot(userId, day);
        if (snapshot == null) {
            return result;
        }
        JSONObject domains = snapshot.optJSONObject("domains");
        if (domains == null) {
            return result;
        }
        java.util.Iterator<String> keys = domains.keys();
        while (keys.hasNext()) {
            String domain = keys.next();
            for (LedgerTask task : loadTasks(userId, day, domain)) {
                if (task.status == LedgerStatus.MANUAL) {
                    result.add(task);
                }
            }
        }
        result.sort((a, b) -> {
            int byDomain = a.domain.compareTo(b.domain);
            return byDomain != 0 ? byDomain : a.title.compareTo(b.title);
        });
        return result;
    }

    /**
     * “需你操作”只保留值得今天处理的任务：
     * 真实操作/游戏、外跳、需授权、已标记复核；永久拉黑的支付/履约/接口不支持不反复打扰。
     */
    public static boolean isActionableManual(LedgerTask task) {
        if (task == null || task.status != LedgerStatus.MANUAL) {
            return false;
        }
        String reason = task.manualReason == null ? "" : task.manualReason;
        return reason.isEmpty()
                || "REAL_ACTION".equals(reason)
                || "REAL_GAMEPLAY".equals(reason)
                || "EXTERNAL_JUMP".equals(reason)
                || "AUTH_REQUIRED".equals(reason)
                || reason.startsWith("USER_DONE");
    }

    public static List<LedgerTask> loadActionableManualTasks(String userId, String day) {
        List<LedgerTask> actionable = new ArrayList<>();
        for (LedgerTask task : loadManualTasks(userId, day)) {
            if (isActionableManual(task)) {
                actionable.add(task);
            }
        }
        return actionable;
    }

    /** 用户点“我已完成”：先标记待复核，下一次账本同步仍 MANUAL 就显示未确认。 */
    public static boolean markManualDone(String userId, String day, String domain, String taskId) {
        if (domain == null || domain.isEmpty() || taskId == null || taskId.isEmpty()) {
            return false;
        }
        synchronized (LOCK) {
            List<LedgerTask> tasks = loadTasks(userId, day, domain);
            boolean found = false;
            for (LedgerTask task : tasks) {
                if (taskId.equals(task.id)) {
                    task.userDoneAt = System.currentTimeMillis();
                    if (task.status == LedgerStatus.MANUAL) {
                        task.manualReason = "USER_DONE_PENDING";
                        task.updatedAt = task.userDoneAt;
                    }
                    found = true;
                    break;
                }
            }
            return found && saveDomainTasks(userId, day, domain, "ok", tasks, true);
        }
    }

    /** 覆盖某个域的任务列表，并同步刷新 summary。 */
    public static boolean saveDomainTasks(String userId, String day, String domain,
                                          String state, List<LedgerTask> tasks, boolean complete) {
        synchronized (LOCK) {
            try {
                JSONObject snapshot = loadSnapshot(userId, day);
                if (snapshot == null) {
                    snapshot = new JSONObject();
                    snapshot.put("day", day);
                    snapshot.put("domains", new JSONObject());
                }
                snapshot.put("schemaVersion", SCHEMA_VERSION);
                snapshot.put("capturedAt", System.currentTimeMillis());
                snapshot.put("complete", complete);
                JSONObject domains = snapshot.optJSONObject("domains");
                if (domains == null) {
                    domains = new JSONObject();
                    snapshot.put("domains", domains);
                }
                JSONObject domainObj = new JSONObject();
                domainObj.put("state", state == null ? "" : state);
                domainObj.put("capturedAt", System.currentTimeMillis());
                JSONArray array = new JSONArray();
                // 3.10.0：跨轮保留手动状态、跳转链接和“我已完成”标记；
                // 服务端仍 TODO 时不把手动任务吞回 UNTOUCHED。
                Map<String, LedgerTask> previous = new LinkedHashMap<>();
                JSONObject oldDomain = domains.optJSONObject(domain);
                JSONArray oldArray = oldDomain == null ? null : oldDomain.optJSONArray("tasks");
                if (oldArray != null) {
                    for (int i = 0; i < oldArray.length(); i++) {
                        JSONObject oldJo = oldArray.optJSONObject(i);
                        if (oldJo != null) {
                            LedgerTask oldTask = LedgerTask.fromJson(oldJo);
                            previous.put(oldTask.id, oldTask);
                        }
                    }
                }
                if (tasks != null) {
                    for (LedgerTask task : tasks) {
                        if (task != null) {
                            LedgerMerge.merge(previous.get(task.id), task, domain);
                            fillCapability(task, domain);
                            array.put(task.toJson());
                        }
                    }
                }
                domainObj.put("tasks", array);
                domains.put(domain, domainObj);

                if (!FileUtil.write2File(snapshot.toString(), getSnapshotFile(userId, day))) {
                    return false;
                }
                return saveSummary(userId, day, domain, array);
            } catch (Throwable t) {
                Log.err(TAG, "saveDomainTasks err:", t);
                return false;
            }
        }
    }

    /**
     * 补能力分类。每次保存都重算：注册表或原因变化后，旧任务不必等下次迁移。
     * 只写分类字段，不碰状态与用户标记。
     */
    private static void fillCapability(LedgerTask task, String domain) {
        try {
            TaskCapabilityRegistry.Spec spec = TaskCapabilityRegistry.resolve(domain, task.id,
                    task.title, task.manualReason, task.progressCurrent, task.progressTotal);
            task.classKey = spec.classKey;
            task.mechanism = spec.mechanism.name();
            task.costType = spec.costType.name();
            task.endpoint = spec.endpoint;
        } catch (Throwable ignored) {
        }
    }

    private static boolean saveSummary(String userId, String day, String domain, JSONArray tasks) {
        try {
            JSONObject summary = loadSummary(userId, day);
            if (summary == null) {
                summary = new JSONObject();
                summary.put("day", day);
                summary.put("domains", new JSONObject());
            }
            summary.put("schemaVersion", SCHEMA_VERSION);
            summary.put("capturedAt", System.currentTimeMillis());
            JSONObject domains = summary.optJSONObject("domains");
            if (domains == null) {
                domains = new JSONObject();
                summary.put("domains", domains);
            }
            domains.put(domain, count(tasks));
            return FileUtil.write2File(summary.toString(), getSummaryFile(userId, day));
        } catch (Throwable t) {
            Log.err(TAG, "saveSummary err:", t);
            return false;
        }
    }

    public static JSONObject loadDomainSummary(String userId, String day, String domain) {
        JSONObject summary = loadSummary(userId, day);
        if (summary == null) {
            return null;
        }
        JSONObject domains = summary.optJSONObject("domains");
        return domains == null ? null : domains.optJSONObject(domain);
    }

    public static JSONObject count(JSONArray tasks) {
        int pending = 0, claimable = 0, untouched = 0, inProgress = 0;
        int claimed = 0, manual = 0, skipped = 0, blacklisted = 0;
        JSONObject classes = new JSONObject();
        if (tasks != null) {
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                countClass(classes, task);
                LedgerStatus status = LedgerStatus.fromName(task.optString("status", ""));
                switch (status) {
                    case CLAIMABLE:
                        claimable++;
                        pending++;
                        break;
                    case CLAIMED:
                        claimed++;
                        break;
                    case MANUAL:
                        manual++;
                        if ("BLACKLISTED".equals(task.optString("manualReason", ""))) {
                            blacklisted++;
                        }
                        break;
                    case SKIPPED:
                        skipped++;
                        break;
                    case IN_PROGRESS:
                        inProgress++;
                        pending++;
                        break;
                    case UNTOUCHED:
                    default:
                        untouched++;
                        pending++;
                        break;
                }
            }
        }
        JSONObject jo = new JSONObject();
        try {
            jo.put("pending", pending);
            jo.put("claimable", claimable);
            jo.put("untouched", untouched);
            jo.put("inProgress", inProgress);
            jo.put("claimed", claimed);
            jo.put("manual", manual);
            jo.put("blacklisted", blacklisted);
            jo.put("skipped", skipped);
            jo.put("total", tasks == null ? 0 : tasks.length());
            jo.put("classes", classes);
        } catch (Throwable ignored) {
        }
        return jo;
    }

    /** 按 classKey 汇总：每个类多少条、其中待办/需手动/已领各多少。 */
    private static void countClass(JSONObject classes, JSONObject task) {
        try {
            String key = task.optString("classKey", "");
            if (key.isEmpty()) {
                TaskCapabilityRegistry.Spec spec = TaskCapabilityRegistry.resolve(
                        task.optString("domain", ""), task.optString("id", ""),
                        task.optString("title", ""), task.optString("manualReason", ""),
                        task.optInt("progressCurrent", 0), task.optInt("progressTotal", 0));
                key = spec.classKey;
            }
            JSONObject item = classes.optJSONObject(key);
            if (item == null) {
                item = new JSONObject();
                item.put("mechanism", TaskCapabilityRegistry.mechanismOfClassKey(key));
                item.put("total", 0);
                item.put("pending", 0);
                item.put("manual", 0);
                item.put("claimed", 0);
                classes.put(key, item);
            }
            item.put("total", item.optInt("total", 0) + 1);
            LedgerStatus status = LedgerStatus.fromName(task.optString("status", ""));
            if (status == LedgerStatus.MANUAL) {
                item.put("manual", item.optInt("manual", 0) + 1);
            } else if (status == LedgerStatus.CLAIMED) {
                item.put("claimed", item.optInt("claimed", 0) + 1);
            } else if (status != LedgerStatus.SKIPPED) {
                item.put("pending", item.optInt("pending", 0) + 1);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String safeUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "default";
        }
        return userId.trim().replaceAll("[^0-9A-Za-z_-]", "_");
    }
}
