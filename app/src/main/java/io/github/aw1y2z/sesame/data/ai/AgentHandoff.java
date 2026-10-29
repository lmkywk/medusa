package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.ModelField;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore;
import io.github.aw1y2z.sesame.data.plan.PlanHistory;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 给开发 AI 的维护交接包：只读本地叶子事件/计划/账本/配置，产出压缩 JSON + Markdown。
 * <p>确定性生成，不调用大模型；不含账号、runId、原始日志。目标是把“哪个叶子坏了、
 * 属于哪一层、下一步查什么”交给开发 AI，而不是再写一份指标报告。
 */
public final class AgentHandoff {

    public static final int SCHEMA_VERSION = 1;
    private static final String TAG = "AgentHandoff";
    private static final int TOP_ANOMALIES = 8;
    private static final int TOP_PLANS = 5;
    private static final int TOP_LEDGERS = 8;
    private static final int TOP_CONFIG_OFF = 30;

    private AgentHandoff() {
    }

    public static File jsonFile(String day) {
        return new File(dir(), "handoff-" + day + ".json");
    }

    public static File textFile(String day) {
        return new File(dir(), "handoff-" + day + ".md");
    }

    public static File dir() {
        File dir = new File(FileUtil.MAIN_DIRECTORY_FILE, "ai");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** 生成并落盘；任何异常都返回一个最小 packet，不阻塞 UI/计划。 */
    public static JSONObject build(String userId, String day) {
        String safeDay = day == null || day.isEmpty() ? TaskLedgerStore.today() : day;
        String uid = userId;
        if (uid == null || uid.isEmpty()) {
            try {
                uid = UserIdMap.getCurrentUid();
            } catch (Throwable ignored) {
            }
        }
        if (uid == null || uid.isEmpty()) {
            // 模块 App 进程没有注入环境的 UserIdMap，从 current_account.txt 兜底。
            uid = FileUtil.readCurrentAccountId();
        }
        JSONObject packet = new JSONObject();
        try {
            packet.put("schemaVersion", SCHEMA_VERSION);
            packet.put("day", safeDay);
            packet.put("generatedAt", System.currentTimeMillis());
            packet.put("moduleVersion", io.github.aw1y2z.sesame.BuildConfig.VERSION_NAME);
            packet.put("fingerprint", io.github.aw1y2z.sesame.BuildConfig.MEDUSA_FINGERPRINT);
            packet.put("device", android.os.Build.MODEL);
            packet.put("android", android.os.Build.VERSION.RELEASE);
            packet.put("planLayoutVersion", planLayoutVersion(uid));
            packet.put("tokenEstimate", 0);

            JSONObject summary = new JSONObject();
            List<Agg> anomalies = readEvents(safeDay, summary);
            packet.put("summary", summary);
            packet.put("anomalies", anomaliesToJson(anomalies));
            JSONArray plans = plansToJson(uid, safeDay, summary);
            JSONArray ledger = ledgerToJson(uid, safeDay);
            packet.put("plans", plans);
            packet.put("ledger", ledger);
            packet.put("config", configToJson());
            packet.put("nextActions", nextActions(anomalies, summary, plans, ledger));
            packet.put("doNotTouch", doNotTouch());
            int estimate = packet.toString().length() / 4;
            packet.put("tokenEstimate", estimate);
            FileUtil.write2File(packet.toString(), jsonFile(safeDay));
            FileUtil.write2File(buildText(packet), textFile(safeDay));
            return packet;
        } catch (Throwable t) {
            Log.err(TAG, "build err:", t);
            try {
                packet.put("schemaVersion", SCHEMA_VERSION);
                packet.put("day", safeDay);
                packet.put("error", t.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
            return packet;
        }
    }

    /** 最近一份交接包；没有则返回 null。 */
    public static JSONObject latest() {
        try {
            File[] files = dir().listFiles((d, name) -> name.startsWith("handoff-") && name.endsWith(".json"));
            if (files == null || files.length == 0) {
                return null;
            }
            File newest = files[0];
            for (File file : files) {
                if (file.lastModified() > newest.lastModified()) {
                    newest = file;
                }
            }
            return new JSONObject(FileUtil.readFromFile(newest));
        } catch (Throwable t) {
            return null;
        }
    }

    /** 人类可读版：给用户/开发 AI 直接粘贴。 */
    public static String buildText(JSONObject packet) {
        if (packet == null) {
            return "还没有维护交接包";
        }
        StringBuilder b = new StringBuilder();
        b.append("美杜莎维护交接包 · ").append(packet.optString("day", "")).append('\n');
        b.append("模块 ").append(packet.optString("moduleVersion", ""))
                .append(" · 设备 ").append(packet.optString("device", ""))
                .append(" · Android ").append(packet.optString("android", ""))
                .append(" · 计划布局 v").append(packet.optInt("planLayoutVersion", 0))
                .append(" · 约 ").append(packet.optInt("tokenEstimate", 0)).append(" tokens\n");
        JSONObject summary = packet.optJSONObject("summary");
        if (summary != null) {
            b.append("事件 ").append(summary.optInt("events"))
                    .append(" · DONE ").append(summary.optInt("done"))
                    .append(" · 失败/重试 ").append(summary.optInt("failed"))
                    .append(" · 跳过 ").append(summary.optInt("skipped"))
                    .append(" · 计划未完成 ").append(summary.optInt("plansIncomplete"))
                    .append(" · 超时 ").append(summary.optInt("timeouts"))
                    .append(" · 1009 ").append(summary.optInt("riskStops")).append('\n');
        }
        JSONArray anomalies = packet.optJSONArray("anomalies");
        if (anomalies != null && anomalies.length() > 0) {
            b.append("\n叶子异常：\n");
            for (int i = 0; i < anomalies.length(); i++) {
                JSONObject a = anomalies.optJSONObject(i);
                if (a == null) continue;
                b.append("- [").append(a.optInt("count")).append("] ")
                        .append(a.optString("target")).append(" / ")
                        .append(a.optString("title")).append(" → ")
                        .append(a.optString("outcome")).append(' ')
                        .append(a.optString("reason")).append(" | ")
                        .append(a.optString("mechanism")).append(" / ")
                        .append(a.optString("endpoint")).append(" | ")
                        .append(a.optString("suspectedLayer")).append(" · ")
                        .append(a.optString("changeClass")).append('\n');
            }
        }
        JSONArray actions = packet.optJSONArray("nextActions");
        if (actions != null && actions.length() > 0) {
            b.append("\n下一步探针：\n");
            for (int i = 0; i < actions.length(); i++) {
                b.append("- ").append(actions.optString(i)).append('\n');
            }
        }
        JSONArray off = packet.optJSONObject("config") == null ? null
                : packet.optJSONObject("config").optJSONArray("offFields");
        if (off != null && off.length() > 0) {
            b.append("\n配置关闭项：");
            for (int i = 0; i < off.length(); i++) {
                if (i > 0) b.append("、");
                b.append(off.optString(i));
            }
            b.append('\n');
        }
        b.append("\n不要动：").append(packet.optJSONArray("doNotTouch") == null ? ""
                : packet.optJSONArray("doNotTouch").toString()).append('\n');
        return b.toString();
    }

    private static List<Agg> readEvents(String day, JSONObject summary) {
        List<Agg> list = new ArrayList<>();
        int events = 0, done = 0, failed = 0, skipped = 0, retried = 0;
        Map<String, Agg> map = new LinkedHashMap<>();
        try {
            File file = new File(FileUtil.LOG_DIRECTORY_FILE, "tasks." + day + ".jsonl");
            if (file.exists()) {
                String raw = FileUtil.readFromFile(file);
                if (raw != null) {
                    for (String line : raw.split("\\r?\\n")) {
                        if (line == null || line.trim().isEmpty()) continue;
                        JSONObject e;
                        try {
                            e = new JSONObject(line);
                        } catch (Throwable ignored) {
                            continue;
                        }
                        events++;
                        String outcome = e.optString("outcome", "");
                        if ("DONE".equals(outcome) || "AWARDED".equals(outcome)) done++;
                        if ("FAILED".equals(outcome) || "UNABLE".equals(outcome)
                                || "UNSUPPORTED".equals(outcome)) failed++;
                        if ("SKIPPED".equals(outcome)) skipped++;
                        if ("RETRY".equals(outcome)) retried++;
                        if (!isAnomaly(outcome)) continue;
                        String target = e.optString("target", "");
                        String taskId = e.optString("taskId", "");
                        String mechanism = e.optString("mechanism", "");
                        String endpoint = e.optString("endpoint", "");
                        String reason = e.optString("reason", "");
                        // 先按“问题类”聚合（缺配置/风控/契约），同类不因任务名或机制不同而拆行；
                        // 其余按“目标+机制+端点+结果+原因”聚合。task 只作样本。
                        String key;
                        if (reason.contains("taskConfigId")) {
                            key = target + "|taskConfigId";
                        } else if (reason.contains("1009") || reason.contains("1004")
                                || reason.contains("验证")) {
                            key = target + "|risk";
                        } else if (reason.contains("缺少") || reason.contains("列表")
                                || reason.contains("数字")) {
                            key = target + "|config";
                        } else if (reason.contains("PARAM_ILLEGAL")
                                || reason.contains("400000040")
                                || reason.contains("不支持")) {
                            key = target + "|contract";
                        } else {
                            key = target + "|" + mechanism + "|" + endpoint
                                    + "|" + outcome + "|" + reason;
                        }
                        Agg agg = map.get(key);
                        if (agg == null) {
                            agg = new Agg();
                            agg.target = target;
                            agg.taskId = taskId;
                            agg.title = e.optString("title", "");
                            agg.mechanism = mechanism;
                            agg.endpoint = endpoint;
                            agg.outcome = outcome;
                            agg.reason = reason;
                            agg.firstAt = e.optString("at", "");
                            map.put(key, agg);
                        }
                        agg.tasks.add(taskId);
                        agg.count++;
                        agg.totalMs += Math.max(0L, e.optLong("durationMs", 0L));
                        agg.lastAt = e.optString("at", "");
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "readEvents err:", t);
        }
        list.addAll(map.values());
        list.sort(Comparator.comparingInt((Agg a) -> a.count).reversed());
        if (list.size() > TOP_ANOMALIES) {
            list = new ArrayList<>(list.subList(0, TOP_ANOMALIES));
        }
        for (Agg a : list) {
            a.classify();
        }
        try {
            summary.put("events", events);
            summary.put("done", done);
            summary.put("failed", failed + retried);
            summary.put("skipped", skipped);
            summary.put("retried", retried);
        } catch (Throwable ignored) {
        }
        return list;
    }

    private static boolean isAnomaly(String outcome) {
        return "FAILED".equals(outcome) || "UNABLE".equals(outcome)
                || "UNSUPPORTED".equals(outcome) || "RETRY".equals(outcome)
                || "SKIPPED".equals(outcome);
    }

    private static JSONArray anomaliesToJson(List<Agg> list) {
        JSONArray array = new JSONArray();
        for (Agg a : list) {
            JSONObject item = new JSONObject();
            try {
                item.put("target", a.target);
                item.put("taskId", a.taskId);
                item.put("title", a.title);
                item.put("mechanism", a.mechanism);
                item.put("endpoint", a.endpoint);
                item.put("outcome", a.outcome);
                item.put("reason", a.reason);
                item.put("count", a.count);
                item.put("taskCount", a.tasks.size());
                item.put("avgMs", a.count == 0 ? 0 : a.totalMs / a.count);
                item.put("firstAt", a.firstAt);
                item.put("lastAt", a.lastAt);
                item.put("suspectedLayer", a.layer);
                item.put("changeClass", a.changeClass);
                item.put("safeToAutoFix", a.safeToAutoFix);
                item.put("nextProbe", a.nextProbe);
            } catch (Throwable ignored) {
            }
            array.put(item);
        }
        return array;
    }

    private static JSONArray plansToJson(String uid, String day, JSONObject summary) {
        JSONArray array = new JSONArray();
        int incomplete = 0, timeouts = 0, riskStops = 0;
        try {
            if (uid != null && !uid.isEmpty()) {
                List<PlanHistory.Record> records = PlanHistory.load(uid);
                List<PlanHistory.Record> today = new ArrayList<>();
                for (PlanHistory.Record record : records) {
                    if (day.equals(TaskLedgerStore.dayOf(record.startedAt))) {
                        today.add(record);
                    }
                }
                for (PlanHistory.Record record : today) {
                    if (PlanHistory.INCOMPLETE.equals(record.status)) incomplete++;
                    if (record.reason != null && record.reason.contains("超时")) timeouts++;
                    if (PlanHistory.RISK_STOPPED.equals(record.status)) riskStops++;
                    if (!PlanHistory.INCOMPLETE.equals(record.status)
                            && !PlanHistory.RISK_STOPPED.equals(record.status)) {
                        continue;
                    }
                    if (array.length() >= TOP_PLANS) continue;
                    JSONObject item = new JSONObject();
                    item.put("target", record.target);
                    item.put("status", record.status);
                    item.put("reasonCode", record.reasonCode);
                    item.put("reason", record.reason == null ? "" : record.reason);
                    item.put("actions", record.actionCount);
                    item.put("durationMs", record.durationMs());
                    array.put(item);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "plansToJson err:", t);
        }
        try {
            summary.put("plansIncomplete", incomplete);
            summary.put("timeouts", timeouts);
            summary.put("riskStops", riskStops);
        } catch (Throwable ignored) {
        }
        return array;
    }

    private static JSONArray ledgerToJson(String uid, String day) {
        JSONArray array = new JSONArray();
        try {
            if (uid == null || uid.isEmpty()) return array;
            JSONObject summary = TaskLedgerStore.loadSummary(uid, day);
            JSONObject domains = summary == null ? null : summary.optJSONObject("domains");
            if (domains == null) return array;
            List<JSONObject> items = new ArrayList<>();
            java.util.Iterator<String> keys = domains.keys();
            while (keys.hasNext()) {
                String name = keys.next();
                JSONObject counts = domains.optJSONObject(name);
                if (counts == null) continue;
                int pending = counts.optInt("pending");
                int claimable = counts.optInt("claimable");
                int manual = counts.optInt("manual");
                if (pending + claimable + manual <= 0) continue;
                JSONObject item = new JSONObject();
                item.put("domain", name);
                item.put("pending", pending);
                item.put("claimable", claimable);
                item.put("manual", manual);
                item.put("total", counts.optInt("total"));
                items.add(item);
            }
            items.sort((a, b) -> Integer.compare(
                    b.optInt("pending") + b.optInt("claimable"),
                    a.optInt("pending") + a.optInt("claimable")));
            for (int i = 0; i < Math.min(TOP_LEDGERS, items.size()); i++) {
                array.put(items.get(i));
            }
        } catch (Throwable t) {
            Log.err(TAG, "ledgerToJson err:", t);
        }
        return array;
    }

    private static JSONObject configToJson() {
        JSONObject result = new JSONObject();
        JSONArray off = new JSONArray();
        int count = 0;
        try {
            for (Map.Entry<String, ModelFields> entry : ConfigV2.INSTANCE.getModelFieldsMap().entrySet()) {
                String model = entry.getKey();
                ModelFields fields = entry.getValue();
                if (fields == null) continue;
                for (ModelField<?> field : fields.values()) {
                    if (field == null || !"BOOLEAN".equals(field.getType())) continue;
                    if (!Boolean.FALSE.equals(field.getValue())) continue;
                    count++;
                    if (off.length() < TOP_CONFIG_OFF) {
                        off.put(model + "." + field.getCode());
                    }
                }
            }
            result.put("offCount", count);
            result.put("offFields", off);
        } catch (Throwable t) {
            Log.err(TAG, "configToJson err:", t);
        }
        return result;
    }

    private static JSONArray nextActions(List<Agg> anomalies, JSONObject summary,
                                         JSONArray plans, JSONArray ledger) {
        JSONArray actions = new JSONArray();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (Agg a : anomalies) {
            String action = a.nextProbe;
            if (action != null && !action.isEmpty()) seen.add(action);
        }
        int timeouts = summary == null ? 0 : summary.optInt("timeouts");
        int riskStops = summary == null ? 0 : summary.optInt("riskStops");
        if (timeouts > 0) {
            String target = "";
            for (int i = 0; plans != null && i < plans.length(); i++) {
                JSONObject item = plans.optJSONObject(i);
                if (item != null && item.optString("reason").contains("超时")) {
                    target = item.optString("target", "");
                    break;
                }
            }
            seen.add("B: 计划超时 " + timeouts + " 次" + (target.isEmpty() ? ""
                    : "，优先复核 " + target) + "；先看设备冻结/叶子预算，再做一次短预算 canary。");
        }
        if (riskStops > 0) {
            seen.add("C: 风控中止 " + riskStops + " 次；只读复核 1009/1004 冷却与节奏，不要自动重试。");
        }
        for (int i = 0; ledger != null && i < ledger.length(); i++) {
            JSONObject item = ledger.optJSONObject(i);
            if (item == null) continue;
            int claimable = item.optInt("claimable");
            if (claimable <= 0) continue;
            seen.add("A: " + item.optString("domain") + " 有 " + claimable
                    + " 个可领未领；先查仓容/列表/冷却，再补领，不改任务策略。");
            break;
        }
        if (seen.isEmpty()) {
            seen.add("A: 当前没有叶子异常；保持只读观察，下一轮先看 plan_history 与 ledger 是否有新增 claimable。");
        }
        for (String action : seen) {
            actions.put(action);
            if (actions.length() >= 6) break;
        }
        return actions;
    }

    private static JSONArray doNotTouch() {
        JSONArray array = new JSONArray();
        array.put("支付/交易/捐赠/购买类开关与任务");
        array.put("宿主 APK、npatch 产物、签名密钥");
        array.put("风控 1009/1004 冷却与全局暂停");
        array.put("账号、好友、Token、原始日志");
        return array;
    }

    private static int planLayoutVersion(String uid) {
        try {
            if (uid == null || uid.isEmpty()) return 0;
            File file = FileUtil.getPlanFile(uid);
            if (!file.exists()) return 0;
            JSONObject root = new JSONObject(FileUtil.readFromFile(file));
            JSONObject defaults = root.optJSONObject("defaults");
            return defaults == null ? 0 : defaults.optInt("layoutVersion", 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static final class Agg {
        String target, taskId, title, mechanism, endpoint, outcome, reason, firstAt, lastAt;
        int count;
        long totalMs;
        final java.util.Set<String> tasks = new java.util.HashSet<>();
        String layer, changeClass, nextProbe;
        boolean safeToAutoFix;

        void classify() {
            String r = reason == null ? "" : reason;
            String o = outcome == null ? "" : outcome;
            if (r.contains("taskConfigId")) {
                layer = "task";
                changeClass = "B";
                safeToAutoFix = true;
                nextProbe = "B: 保留缺 taskConfigId 的跳过；若要执行，先只读核对任务配置映射（" + tasks.size()
                        + " 个任务），不要每 30 秒重试。";
            } else if (r.contains("1009") || r.contains("1004") || r.contains("验证")) {
                layer = "risk";
                changeClass = "C";
                safeToAutoFix = false;
                nextProbe = "C: 只读复核 " + target + " 的 1009/1004 冷却与节奏，不要自动重试；确认后再改风控参数。";
            } else if (r.contains("taskConfigId") || r.contains("列表") || r.contains("数字")
                    || r.contains("缺少")) {
                layer = "config";
                changeClass = "A";
                safeToAutoFix = true;
                nextProbe = "A: 复核 " + target + " 的开关依赖（数字/好友/任务配置），缺依赖就关闭或补配置，不改执行器。";
            } else if (r.contains("PARAM_ILLEGAL") || r.contains("400000040")
                    || r.contains("不支持")) {
                layer = "endpoint";
                changeClass = "B";
                safeToAutoFix = false;
                nextProbe = "B: 只读重放 " + endpoint + "，核对 payload/sceneCode；确认服务端契约后再改 fallback。";
            } else if (o.equals("SKIPPED")) {
                layer = "policy";
                changeClass = "A";
                safeToAutoFix = true;
                nextProbe = "A: 检查 " + target + " 的按类过滤/冷却/黑名单，先改规则再考虑代码。";
            } else if (o.equals("RETRY") || o.equals("FAILED")) {
                layer = "task";
                changeClass = "B";
                safeToAutoFix = false;
                nextProbe = "B: 对 " + target + " 的 " + title + " 做一次短预算 canary，确认是临时故障还是契约变化。";
            } else {
                layer = "unknown";
                changeClass = "B";
                safeToAutoFix = false;
                nextProbe = "B: 复核 " + target + " 的 " + title + " 事件与端点响应。";
            }
            if (safeToAutoFix && nextProbe != null && nextProbe.length() > 160) {
                nextProbe = nextProbe.substring(0, 159) + "…";
            }
        }
    }
}
