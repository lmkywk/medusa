package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore;
import io.github.aw1y2z.sesame.data.metrics.RpcMetrics;
import io.github.aw1y2z.sesame.data.plan.PlanHistory;
import io.github.aw1y2z.sesame.data.risk.RiskBudget;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ai.AiSafety;

/**
 * 4.0.0 AI 只读层的数据入口：只产出**聚合数字**，不产出原始日志、任务标题、URL、令牌、好友。
 * <p>数字全部由本地代码算好，模型只负责归因与叙述——它不做加减法，也就编不出假数字。
 */
public final class AiDigest {

    private static final String TAG = "AiDigest";

    private AiDigest() {
    }

    /** 今日聚合（脱敏后可直接发给模型）。 */
    public static JSONObject today(String userId, String day) {
        JSONObject digest = new JSONObject();
        try {
            digest.put("day", day);
            digest.put("metrics", metricsSummary(1));
            digest.put("budget", budgetSummary());
            digest.put("plans", planSummary(userId, day));
            digest.put("ledger", ledgerSummary(userId, day));
            digest.put("manualQueue", manualQueueCount(userId, day));
            // 给开发 AI 的压缩交接包：叶子异常 + 下一步探针；完整包另存 ai/handoff-<day>.json。
            JSONObject packet = AgentHandoff.build(userId, day);
            JSONObject handoff = new JSONObject();
            handoff.put("schemaVersion", packet.optInt("schemaVersion"));
            handoff.put("summary", packet.optJSONObject("summary"));
            JSONArray top = new JSONArray();
            JSONArray anomalies = packet.optJSONArray("anomalies");
            for (int i = 0; anomalies != null && i < anomalies.length() && i < 5; i++) {
                top.put(anomalies.opt(i));
            }
            handoff.put("anomalies", top);
            handoff.put("nextActions", packet.optJSONArray("nextActions"));
            handoff.put("plans", packet.optJSONArray("plans"));
            handoff.put("tokenEstimate", packet.optInt("tokenEstimate"));
            digest.put("handoff", handoff);
        } catch (Throwable t) {
            Log.err(TAG, "构造今日摘要失败", t);
        }
        return digest;
    }

    /** 最近 N 天聚合（期末汇总用）。 */
    public static JSONObject window(String userId, int days) {
        JSONObject digest = new JSONObject();
        try {
            digest.put("days", days);
            digest.put("metrics", metricsSummary(days));
            digest.put("budget", budgetSummary());
            digest.put("manualQueue", manualQueueCount(userId, TaskLedgerStore.today()));
            digest.put("aiDailySummaries", dailySummaryFiles(days));
        } catch (Throwable t) {
            Log.err(TAG, "构造周期摘要失败", t);
        }
        return digest;
    }

    private static JSONObject metricsSummary(int days) {
        JSONObject raw = RpcMetrics.summary(days);
        JSONObject result = new JSONObject();
        try {
            JSONObject byScope = raw.optJSONObject("byScope");
            JSONArray scopes = new JSONArray();
            if (byScope != null) {
                List<String> names = new ArrayList<>();
                Iterator<String> keys = byScope.keys();
                while (keys.hasNext()) {
                    names.add(keys.next());
                }
                names.sort(String::compareTo);
                for (String name : names) {
                    JSONObject item = byScope.optJSONObject(name);
                    if (item == null || item.optLong("n") <= 0) {
                        continue;
                    }
                    JSONObject scope = new JSONObject();
                    scope.put("domain", name);
                    long n = item.optLong("n");
                    scope.put("requests", n);
                    scope.put("ok", item.optLong("ok"));
                    scope.put("fail", item.optLong("fail"));
                    scope.put("empty", item.optLong("empty"));
                    scope.put("blocked", item.optLong("blocked"));
                    scope.put("c1009", item.optLong("c1009"));
                    scope.put("c1004", item.optLong("c1004"));
                    scope.put("avgMs", n == 0 ? 0 : item.optLong("ms") / n);
                    scopes.put(scope);
                }
            }
            result.put("scopes", scopes);
            result.put("totalRequests", raw.optLong("totalRequests"));
            result.put("total1009", raw.optLong("total1009"));
            result.put("totalFail", raw.optLong("totalFail"));
        } catch (Throwable t) {
            Log.err(TAG, "汇总指标失败", t);
        }
        return result;
    }

    private static JSONObject budgetSummary() {
        JSONObject result = new JSONObject();
        try {
            result.put("mode", RiskBudget.mode());
            JSONObject shadow = RiskBudget.shadowSummary(1);
            JSONObject byScope = shadow.optJSONObject("byScope");
            JSONArray scopes = new JSONArray();
            if (byScope != null) {
                Iterator<String> keys = byScope.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONObject item = byScope.optJSONObject(name);
                    if (item == null || item.optInt("wouldBlock") <= 0) {
                        continue;
                    }
                    JSONObject scope = new JSONObject();
                    scope.put("domain", name);
                    scope.put("wouldBlock", item.optInt("wouldBlock"));
                    scope.put("suggestion", RiskBudget.suggestionFor(name));
                    scopes.put(scope);
                }
            }
            result.put("shadow", scopes);
        } catch (Throwable t) {
            Log.err(TAG, "汇总预算失败", t);
        }
        return result;
    }

    private static JSONObject planSummary(String userId, String day) {
        JSONObject result = new JSONObject();
        try {
            List<PlanHistory.Record> records = PlanHistory.load(userId);
            java.util.Calendar today = java.util.Calendar.getInstance();
            int completed = 0;
            int incomplete = 0;
            int skipped = 0;
            int riskStopped = 0;
            int timeouts = 0;
            JSONArray domains = new JSONArray();
            JSONArray timeoutTargets = new JSONArray();
            java.util.Set<String> failedTargets = new java.util.TreeSet<>();
            java.util.Set<String> timeoutSet = new java.util.TreeSet<>();
            for (PlanHistory.Record record : records) {
                java.util.Calendar started = java.util.Calendar.getInstance();
                started.setTimeInMillis(record.startedAt);
                if (started.get(java.util.Calendar.YEAR) != today.get(java.util.Calendar.YEAR)
                        || started.get(java.util.Calendar.DAY_OF_YEAR) != today.get(java.util.Calendar.DAY_OF_YEAR)) {
                    continue;
                }
                if (PlanHistory.COMPLETED.equals(record.status)) {
                    completed++;
                } else if (PlanHistory.SKIPPED.equals(record.status)) {
                    skipped++;
                } else if (PlanHistory.RISK_STOPPED.equals(record.status)) {
                    riskStopped++;
                    if (record.target != null && !record.target.isEmpty()) {
                        failedTargets.add(record.target);
                    }
                } else {
                    incomplete++;
                    if (record.target != null && !record.target.isEmpty()) {
                        failedTargets.add(record.target);
                    }
                    if ("TIMEOUT".equals(record.reasonCode)
                            || (record.reason != null && record.reason.contains("超时"))) {
                        timeouts++;
                        if (record.target != null && !record.target.isEmpty()) {
                            timeoutSet.add(record.target);
                        }
                    }
                }
            }
            for (String target : failedTargets) {
                domains.put(target);
            }
            for (String target : timeoutSet) {
                timeoutTargets.put(target);
            }
            result.put("completed", completed);
            result.put("incomplete", incomplete);
            result.put("skipped", skipped);
            result.put("riskStopped", riskStopped);
            result.put("timeouts", timeouts);
            result.put("timeoutTargets", timeoutTargets);
            result.put("unfinishedTargets", domains);
        } catch (Throwable t) {
            Log.err(TAG, "汇总计划失败", t);
        }
        return result;
    }

    private static JSONObject ledgerSummary(String userId, String day) {
        JSONObject result = new JSONObject();
        try {
            JSONObject summary = TaskLedgerStore.loadSummary(userId, day);
            JSONObject domains = summary == null ? null : summary.optJSONObject("domains");
            JSONArray array = new JSONArray();
            if (domains != null) {
                Iterator<String> keys = domains.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    JSONObject counts = domains.optJSONObject(name);
                    if (counts == null) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("domain", name);
                    item.put("pending", counts.optInt("pending"));
                    item.put("claimed", counts.optInt("claimed"));
                    item.put("manual", counts.optInt("manual"));
                    item.put("skipped", counts.optInt("skipped"));
                    array.put(item);
                }
            }
            result.put("domains", array);
        } catch (Throwable t) {
            Log.err(TAG, "汇总账本失败", t);
        }
        return result;
    }

    private static int manualQueueCount(String userId, String day) {
        try {
            return TaskLedgerStore.loadActionableManualTasks(userId, day).size();
        } catch (Throwable t) {
            return 0;
        }
    }

    private static JSONArray dailySummaryFiles(int days) {
        JSONArray array = new JSONArray();
        try {
            List<JSONObject> history = AiDailySummary.recent(days);
            for (JSONObject item : history) {
                array.put(item);
            }
        } catch (Throwable t) {
            Log.err(TAG, "读取历史摘要失败", t);
        }
        return array;
    }

    /** 出站前统一脱敏。 */
    public static String redactedJson(JSONObject digest) {
        return AiSafety.redact(digest == null ? "{}" : digest.toString());
    }
}