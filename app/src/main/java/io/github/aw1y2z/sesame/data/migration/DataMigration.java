package io.github.aw1y2z.sesame.data.migration;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 4.0.0 数据迁移框架：schemaVersion + 迁移注册表 + 迁移前备份 + dry-run + 自定义计划保护。
 * <p>只做“补齐字段 / 规范化取值”这类无损迁移，不新增业务数据、不覆盖用户计划。
 * <p>迁移前把原文件复制到 {@code bak/migration-<时间戳>/}，dry-run 只出报告不落盘。
 */
public final class DataMigration {

    private static final String TAG = "DataMigration";

    public static final int CONFIG_VERSION = 2;
    public static final int PLAN_VERSION = 2;
    /** v3：账本任务补 classKey/机制/成本/端点（只读分类，不动状态）。 */
    public static final int LEDGER_VERSION = 3;

    private static final String REPORT_FILE = "migration_report.json";
    private static final int LEDGER_DAYS = 7;
    private static final Pattern DAY_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private DataMigration() {
    }

    /** 单条迁移：from → from+1，返回是否改动。 */
    interface Step {
        int from();

        String describe();

        boolean apply(JSONObject root) throws Exception;
    }

    private static final List<Step> CONFIG_STEPS = new ArrayList<>();
    private static final List<Step> PLAN_STEPS = new ArrayList<>();

    static {
        // 配置 v1 → v2：清理已废弃字段、夹紧节流参数
        CONFIG_STEPS.add(new Step() {
            @Override
            public int from() {
                return 1;
            }

            @Override
            public String describe() {
                return "config: 删除废弃 toastOffsetY，夹紧 planFlowMs/planFlowJitterPercent";
            }

            @Override
            public boolean apply(JSONObject root) throws Exception {
                boolean changed = false;
                if (root.has("toastOffsetY")) {
                    root.remove("toastOffsetY");
                    changed = true;
                }
                int flow = root.optInt("planFlowMs", 5000);
                int clamped = Math.max(0, Math.min(60_000, flow));
                if (clamped != flow) {
                    root.put("planFlowMs", clamped);
                    changed = true;
                }
                int jitter = root.optInt("planFlowJitterPercent", 30);
                int jitterClamped = Math.max(0, Math.min(100, jitter));
                if (jitterClamped != jitter) {
                    root.put("planFlowJitterPercent", jitterClamped);
                    changed = true;
                }
                return changed;
            }
        });
        // 计划 v1 → v2：轮次时间排序去重、夹紧节奏、剔除非法目标；绝不新增或替换计划
        PLAN_STEPS.add(new Step() {
            @Override
            public int from() {
                return 1;
            }

            @Override
            public String describe() {
                return "plans: 轮次时间排序去重、paceMinutes 夹紧、剔除非法目标（保留用户计划）";
            }

            @Override
            public boolean apply(JSONObject root) throws Exception {
                boolean changed = false;
                JSONArray plans = root.optJSONArray("plans");
                if (plans == null) {
                    return false;
                }
                JSONArray kept = new JSONArray();
                for (int i = 0; i < plans.length(); i++) {
                    JSONObject plan = plans.optJSONObject(i);
                    if (plan == null) {
                        changed = true;
                        continue;
                    }
                    String target = plan.optString("target", "");
                    if (!io.github.aw1y2z.sesame.data.plan.PlanTargets.isValidTarget(target)) {
                        changed = true;
                        continue;
                    }
                    JSONArray times = plan.optJSONArray("roundTimes");
                    if (times != null) {
                        Set<String> ordered = new LinkedHashSet<>();
                        for (int t = 0; t < times.length(); t++) {
                            String time = times.optString(t, "").trim();
                            if (time.matches("\\d{2}:\\d{2}")) {
                                ordered.add(time);
                            }
                        }
                        List<String> sorted = new ArrayList<>(ordered);
                        sorted.sort(String::compareTo);
                        JSONArray normalized = new JSONArray();
                        for (String time : sorted) {
                            normalized.put(time);
                        }
                        if (!normalized.toString().equals(times.toString())) {
                            plan.put("roundTimes", normalized);
                            changed = true;
                        }
                    }
                    int pace = plan.optInt("paceMinutes", 0);
                    int clamped = Math.max(0, Math.min(720, pace));
                    if (clamped != pace) {
                        plan.put("paceMinutes", clamped);
                        changed = true;
                    }
                    kept.put(plan);
                }
                if (kept.length() != plans.length()) {
                    root.put("plans", kept);
                }
                return changed;
            }
        });
    }

    /** 对当前账号跑全部迁移。 */
    public static JSONObject runForCurrentAccount(boolean dryRun) {
        try {
            String userId = currentUserId();
            if (userId == null || userId.isEmpty()) {
                JSONObject skipped = new JSONObject();
                skipped.put("skipped", true);
                skipped.put("reason", "no-current-account");
                return skipped;
            }
            return runAll(userId, dryRun);
        } catch (Throwable t) {
            Log.err(TAG, "迁移入口失败", t);
            JSONObject error = new JSONObject();
            try {
                error.put("error", String.valueOf(t));
            } catch (Throwable ignored) {
            }
            return error;
        }
    }

    private static String currentUserId() {
        try {
            File file = FileUtil.getCurrentAccountFile();
            if (!file.exists()) {
                return "";
            }
            String value = FileUtil.readFromFile(file);
            return value == null ? "" : value.trim();
        } catch (Throwable t) {
            return "";
        }
    }

    public static JSONObject runAll(String userId, boolean dryRun) {
        JSONObject report = new JSONObject();
        try {
            report.put("at", System.currentTimeMillis());
            report.put("userId", userId == null ? "" : userId);
            report.put("dryRun", dryRun);
            JSONArray items = new JSONArray();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(new Date());

            File configFile = FileUtil.getConfigV2File(userId);
            items.put(migrateFile(configFile, "config", CONFIG_STEPS, CONFIG_VERSION, dryRun, stamp));

            File planFile = FileUtil.getPlanFile(userId);
            items.put(migrateFile(planFile, "plans", PLAN_STEPS, PLAN_VERSION, dryRun, stamp));

            items.put(migrateLedger(userId, dryRun, stamp));

            report.put("items", items);
            boolean changed = false;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && item.optBoolean("changed")) {
                    changed = true;
                }
            }
            report.put("changed", changed);
            if (!dryRun) {
                FileUtil.write2File(report.toString(), new File(FileUtil.MAIN_DIRECTORY_FILE, REPORT_FILE));
                if (changed) {
                    EventLog.system("migration_done", "user", userId == null ? "" : userId);
                    Log.record("数据迁移🔄已完成 schema 升级（配置 v" + CONFIG_VERSION
                            + " / 计划 v" + PLAN_VERSION + " / 账本 v" + LEDGER_VERSION + "）");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "迁移失败", t);
            try {
                report.put("error", String.valueOf(t));
            } catch (Throwable ignored) {
            }
        }
        return report;
    }

    private static JSONObject migrateFile(File file, String kind, List<Step> steps, int targetVersion,
                                          boolean dryRun, String stamp) throws Exception {
        JSONObject item = new JSONObject();
        item.put("kind", kind);
        item.put("path", file == null ? "" : file.getName());
        if (file == null || !file.exists()) {
            item.put("state", "missing");
            return item;
        }
        String raw = FileUtil.readFromFile(file);
        if (raw == null || raw.trim().isEmpty()) {
            item.put("state", "empty");
            return item;
        }
        JSONObject root = new JSONObject(raw);
        int version = root.optInt("schemaVersion", 1);
        item.put("from", version);
        item.put("to", targetVersion);
        if (version >= targetVersion) {
            item.put("state", "up-to-date");
            return item;
        }
        List<String> applied = new ArrayList<>();
        int current = version;
        while (current < targetVersion) {
            Step step = findStep(steps, current);
            if (step == null) {
                item.put("state", "no-step");
                item.put("stoppedAt", current);
                return item;
            }
            step.apply(root);
            applied.add(step.describe());
            current++;
        }
        root.put("schemaVersion", targetVersion);
        item.put("changed", true);
        item.put("applied", new JSONArray(applied));
        if (dryRun) {
            item.put("state", "would-migrate");
            return item;
        }
        backup(file, stamp);
        if (!FileUtil.write2File(root.toString(), file)) {
            item.put("state", "write-failed");
            return item;
        }
        item.put("state", "migrated");
        return item;
    }

    private static Step findStep(List<Step> steps, int from) {
        for (Step step : steps) {
            if (step.from() == from) {
                return step;
            }
        }
        return null;
    }

    /** 账本按天分目录，只迁移最近 7 天，避免扫全量历史。 */
    private static JSONObject migrateLedger(String userId, boolean dryRun, String stamp) throws Exception {
        JSONObject item = new JSONObject();
        item.put("kind", "ledger");
        int migrated = 0;
        int scanned = 0;
        List<String> days = recentDays();
        File root = new File(FileUtil.MAIN_DIRECTORY_FILE, "ledger");
        File userDir = new File(root, safeUser(userId));
        if (!userDir.exists()) {
            item.put("state", "missing");
            return item;
        }
        for (String day : days) {
            File dayDir = new File(userDir, day);
            if (!dayDir.exists() || !dayDir.isDirectory()) {
                continue;
            }
            File[] files = dayDir.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (!file.isFile() || !file.getName().endsWith(".json")) {
                    continue;
                }
                scanned++;
                if (migrateLedgerFile(file, dryRun, stamp)) {
                    migrated++;
                }
            }
        }
        item.put("scanned", scanned);
        item.put("changed", migrated > 0);
        item.put("migrated", migrated);
        item.put("state", migrated > 0 ? (dryRun ? "would-migrate" : "migrated") : "up-to-date");
        return item;
    }

    private static boolean migrateLedgerFile(File file, boolean dryRun, String stamp) {
        try {
            String raw = FileUtil.readFromFile(file);
            if (raw == null || raw.trim().isEmpty()) {
                return false;
            }
            JSONObject root = new JSONObject(raw);
            if (root.optInt("schemaVersion", 1) >= LEDGER_VERSION) {
                return false;
            }
            boolean changed = false;
            JSONObject domains = root.optJSONObject("domains");
            if (domains != null) {
                Iterator<String> keys = domains.keys();
                while (keys.hasNext()) {
                    String domain = keys.next();
                    JSONObject domainObject = domains.optJSONObject(domain);
                    if (domainObject == null) {
                        continue;
                    }
                    JSONArray tasks = domainObject.optJSONArray("tasks");
                    if (tasks == null) {
                        continue;
                    }
                    for (int i = 0; i < tasks.length(); i++) {
                        JSONObject task = tasks.optJSONObject(i);
                        if (task == null) {
                            continue;
                        }
                        if (!task.has("domain")) {
                            task.put("domain", domain);
                            changed = true;
                        }
                        if (!task.has("actionUrl")) {
                            task.put("actionUrl", "");
                            changed = true;
                        }
                        if (!task.has("userDoneAt")) {
                            task.put("userDoneAt", 0L);
                            changed = true;
                        }
                        if (task.optString("classKey", "").isEmpty()) {
                            io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.Spec spec =
                                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.resolve(
                                            domain, task.optString("id", ""), task.optString("title", ""),
                                            task.optString("manualReason", ""),
                                            task.optInt("progressCurrent", 0), task.optInt("progressTotal", 0));
                            task.put("classKey", spec.classKey);
                            task.put("mechanism", spec.mechanism.name());
                            task.put("costType", spec.costType.name());
                            task.put("endpoint", spec.endpoint);
                            changed = true;
                        }
                    }
                }
            }
            if (!changed) {
                // 没有字段要补也把版本抬上去，避免每天重复扫描同一份文件
                root.put("schemaVersion", LEDGER_VERSION);
                if (dryRun) {
                    return true;
                }
                backup(file, stamp);
                return FileUtil.write2File(root.toString(), file);
            }
            root.put("schemaVersion", LEDGER_VERSION);
            if (dryRun) {
                return true;
            }
            backup(file, stamp);
            return FileUtil.write2File(root.toString(), file);
        } catch (Throwable t) {
            Log.err(TAG, "迁移账本失败 " + file.getName(), t);
            return false;
        }
    }

    private static void backup(File file, String stamp) {
        try {
            File dir = new File(FileUtil.getBackupDirectoryFile(), "migration-" + stamp);
            if (!dir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            }
            File target = new File(dir, file.getParentFile().getName() + "_" + file.getName());
            java.nio.file.Files.copy(file.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable t) {
            Log.err(TAG, "迁移备份失败", t);
        }
    }

    private static List<String> recentDays() {
        List<String> days = new ArrayList<>();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long now = System.currentTimeMillis();
        for (int i = 0; i < LEDGER_DAYS; i++) {
            String day = format.format(new Date(now - i * 86_400_000L));
            if (DAY_PATTERN.matcher(day).matches()) {
                days.add(day);
            }
        }
        return days;
    }

    private static String safeUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return "default";
        }
        return userId.replaceAll("[^0-9A-Za-z_-]", "_");
    }

    public static JSONObject lastReport() {
        try {
            File file = new File(FileUtil.MAIN_DIRECTORY_FILE, REPORT_FILE);
            if (!file.exists()) {
                return new JSONObject();
            }
            return new JSONObject(FileUtil.readFromFile(file));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /** dry-run 报告：只返回计划，不落盘（设置页/CI 用）。 */
    public static JSONObject dryRunForCurrentAccount() {
        return runForCurrentAccount(true);
    }
}
