package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ai.AiClient;
import io.github.aw1y2z.sesame.util.ai.AiSafety;

/**
 * 4.0.0 期末汇总：数据期（7–14 天）结束后一次性生成，产出 4.1 需要的三样输入——
 * 未知任务清单、真实耗时分布、失败原因排行。
 * <p>数字仍由本地算；模型只做归因与叙述。产出落盘为 Markdown，可直接看/导出。
 */
public final class AiRollup {

    private static final String TAG = "AiRollup";

    private static final String SYSTEM_PROMPT =
            "你是「美杜莎」的只读分析助手。给你一段时间的聚合数据与每日摘要结论，"
                    + "请只输出一个 JSON 对象，字段固定为："
                    + "headline（一句话总结）、topFailures（数组，失败最多的域与原因）、"
                    + "slowest（数组，耗时最长的域）、unknownTaskAdvice（数组，对未知任务分类的建议）、"
                    + "budgetAdvice（数组，对风险预算参数的建议）、nextSteps（数组，建议下一步做什么）。"
                    + "不得编造数字，不得建议自动执行，不得输出账号/好友/链接。";

    private AiRollup() {
    }

    public static File reportFile(int days) {
        String stamp = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        return new File(AiDailySummary.dir(), "rollup-" + days + "d-" + stamp + ".md");
    }

    /** 已有多少天摘要（用于判断是否够生成期末报告）。 */
    public static int availableDays() {
        return AiDailySummary.recent(30).size();
    }

    public static JSONObject generate(String userId, int days, boolean force) {
        JSONObject result = new JSONObject();
        try {
            File file = reportFile(days);
            if (file.exists() && !force) {
                result.put("ok", true);
                result.put("skipped", true);
                result.put("path", file.getAbsolutePath());
                return result;
            }
            result.put("days", days);
            result.put("at", System.currentTimeMillis());

            JSONObject digest = AiDigest.window(userId, days);
            JSONArray unknown = UnknownTaskSamples.topUnknown(20);
            digest.put("unknownTasks", unknown);
            result.put("unknownTaskCount", UnknownTaskSamples.totalCount());

            String payload = AiDigest.redactedJson(digest);
            boolean ready = AiClient.ready();
            result.put("configured", ready);

            JSONObject analysis = null;
            if (ready) {
                String reply = AiClient.chat(SYSTEM_PROMPT,
                        "统计窗口：" + days + " 天\n聚合数据（数字由本地计算）：\n" + payload);
                if (reply != null) {
                    AiSafety.Validation validation = AiSafety.validate(reply);
                    if (validation.valid) {
                        analysis = validation.json;
                    } else {
                        result.put("reason", "回复未通过校验：" + validation.reason);
                    }
                } else {
                    result.put("reason", "调用失败（已重试）");
                }
            } else {
                result.put("reason", "AI 配置未就绪（" + AiClient.describeReadiness() + "）");
            }

            String text = buildText(days, digest, analysis);
            FileUtil.write2File(text, file);
            result.put("ok", true);
            result.put("analysis", analysis);
            result.put("path", file.getAbsolutePath());
            result.put("text", text);
            EventLog.system("ai_rollup_ok", "days", days, "configured", ready);
        } catch (Throwable t) {
            Log.err(TAG, "生成期末汇总失败", t);
            try {
                result.put("ok", false);
                result.put("reason", "异常：" + t.getClass().getSimpleName());
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static String buildText(int days, JSONObject digest, JSONObject analysis) {
        StringBuilder builder = new StringBuilder();
        builder.append("美杜莎 ").append(days).append(" 天数据期汇总\n");
        builder.append("生成时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .format(new Date())).append("\n\n");

        JSONObject metrics = digest.optJSONObject("metrics");
        if (metrics != null) {
            builder.append("## 本地数字\n");
            builder.append("- 总请求 ").append(metrics.optLong("totalRequests"))
                    .append(" / 失败 ").append(metrics.optLong("totalFail"))
                    .append(" / 1009 ").append(metrics.optLong("total1009")).append('\n');
            JSONArray scopes = metrics.optJSONArray("scopes");
            if (scopes != null) {
                builder.append("- 分域：\n");
                for (int i = 0; i < scopes.length(); i++) {
                    JSONObject scope = scopes.optJSONObject(i);
                    if (scope == null) {
                        continue;
                    }
                    builder.append("  - ").append(scope.optString("domain"))
                            .append("：请求 ").append(scope.optLong("requests"))
                            .append("，失败 ").append(scope.optLong("fail"))
                            .append("，1009 ").append(scope.optLong("c1009"))
                            .append("，均耗时 ").append(scope.optLong("avgMs")).append("ms\n");
                }
            }
        }

        JSONArray unknown = digest.optJSONArray("unknownTasks");
        if (unknown != null && unknown.length() > 0) {
            builder.append("\n## 未知任务样本（4.1 分类的输入）\n");
            for (int i = 0; i < unknown.length(); i++) {
                JSONObject item = unknown.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                builder.append("- [").append(item.optString("domain")).append("] ")
                        .append(item.optString("title"))
                        .append("（timeCount=").append(item.optLong("timeCount"))
                        .append("，出现 ").append(item.optInt("count")).append(" 次）\n");
            }
        }

        JSONArray summaries = digest.optJSONArray("aiDailySummaries");
        if (summaries != null && summaries.length() > 0) {
            builder.append("\n## 每日摘要结论\n");
            for (int i = 0; i < summaries.length(); i++) {
                JSONObject item = summaries.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                builder.append("- ").append(item.optString("day")).append("：")
                        .append(item.optString("headline", "（无结论）")).append('\n');
            }
        }

        builder.append("\n## 分析建议\n");
        if (analysis == null) {
            builder.append("（本次未生成结构化建议；本地数字仍可用）\n");
        } else {
            builder.append("结论：").append(analysis.optString("headline", "")).append('\n');
            appendArray(builder, "失败集中", analysis.optJSONArray("topFailures"));
            appendArray(builder, "最慢", analysis.optJSONArray("slowest"));
            appendArray(builder, "未知任务建议", analysis.optJSONArray("unknownTaskAdvice"));
            appendArray(builder, "预算建议", analysis.optJSONArray("budgetAdvice"));
            appendArray(builder, "下一步", analysis.optJSONArray("nextSteps"));
        }
        builder.append("\n说明：以上数字来自本地指标，模型只做归因；本次结论不改变任何执行参数。\n");
        return builder.toString();
    }

    private static void appendArray(StringBuilder builder, String label, JSONArray array) {
        if (array == null || array.length() == 0) {
            return;
        }
        builder.append(label).append("：\n");
        for (int i = 0; i < array.length(); i++) {
            builder.append("  - ").append(String.valueOf(array.opt(i))).append('\n');
        }
    }
}