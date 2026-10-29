package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.ai.AiClient;
import io.github.aw1y2z.sesame.util.ai.AiSafety;

/**
 * 4.0.0 AI 每日/异常摘要：把当天聚合数字交给模型归因叙述，落盘可导出，并按需通知。
 * <p>幂等：同一天只生成一次（除非强制）。失败只记本地记录，不影响任何执行。
 */
public final class AiDailySummary {

    private static final String TAG = "AiDailySummary";
    private static final String DIR = "ai";
    private static final int KEEP_DAYS = 30;

    private static final String SYSTEM_PROMPT =
            "你是「美杜莎」（支付宝任务自动化工具）的只读分析助手。"
                    + "你只能依据给定的聚合数字做归因和叙述，不得编造数字，不得建议执行任何操作，"
                    + "不得输出账号、好友、链接、原始日志。指标口径必须先看清：empty=本地无响应"
                    + "（宿主离线、写闸门、运行被停止），**不是任务目标耗尽**；blocked=本地闸门拦截；"
                    + "timeouts=计划墙钟超时，常见于进程被系统冻结/挂起，而不是接口失败；"
                    + "不要把 empty 或 timeouts 归因成“没有可做任务”。只输出一个 JSON 对象，字段固定为："
                    + "headline（一句话结论）、failedDomains（数组，失败的域）、causes（数组，原因）、"
                    + "suggestions（数组，建议动作）。不要输出 JSON 以外的任何文字。";

    private AiDailySummary() {
    }

    /** 没传账号时用当前账号；都没有就按 default 目录读（不会崩）。 */
    private static String resolveUser(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            return userId.trim();
        }
        try {
            File file = FileUtil.getCurrentAccountFile();
            if (file.exists()) {
                String value = FileUtil.readFromFile(file);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    public static File dir() {
        File dir = new File(FileUtil.MAIN_DIRECTORY_FILE, DIR);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    public static File jsonFile(String day) {
        return new File(dir(), "daily-" + day + ".json");
    }

    public static File textFile(String day) {
        return new File(dir(), "daily-" + day + ".md");
    }

    /**
     * 异步版：绝不阻塞计划线程。AI 调用最坏要 180 秒×重试，不能在执行线程里等。
     */
    public static void maybeRunAsync(String userId) {
        try {
            if (AppConfig.INSTANCE.getAiSummaryEnabled() == Boolean.FALSE) {
                return;
            }
            String day = TaskLedgerStore.today();
            if (jsonFile(day).exists()) {
                return;
            }
            int hour = AppConfig.INSTANCE.getAiSummaryHour() == null ? 22 : AppConfig.INSTANCE.getAiSummaryHour();
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < hour) {
                return;
            }
            Thread worker = new Thread(() -> maybeRun(userId), "medusa-ai-summary");
            worker.setDaemon(true);
            worker.start();
        } catch (Throwable t) {
            Log.err(TAG, "启动摘要任务失败", t);
        }
    }

    /** 到点且今天还没有摘要时生成；由调度器/启动时调用。 */
    public static JSONObject maybeRun(String userId) {
        try {
            if (AppConfig.INSTANCE.getAiSummaryEnabled() == Boolean.FALSE) {
                return null;
            }
            String day = TaskLedgerStore.today();
            if (jsonFile(day).exists()) {
                return load(day);
            }
            int hour = AppConfig.INSTANCE.getAiSummaryHour() == null ? 22 : AppConfig.INSTANCE.getAiSummaryHour();
            if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < hour) {
                return null;
            }
            return generate(userId, day, false);
        } catch (Throwable t) {
            Log.err(TAG, "生成每日摘要失败", t);
            return null;
        }
    }

    /** 强制生成（设置页按钮用）。 */
    public static JSONObject generate(String userId, String day, boolean force) {
        JSONObject result = new JSONObject();
        try {
            if (!force && jsonFile(day).exists()) {
                return load(day);
            }
            userId = resolveUser(userId);
            result.put("day", day);
            result.put("at", System.currentTimeMillis());
            result.put("configured", AiClient.ready());

            JSONObject digest = AiDigest.today(userId, day);
            String payload = AiDigest.redactedJson(digest);
            result.put("digest", digest);

            if (!AiClient.ready()) {
                result.put("ok", false);
                result.put("reason", "AI 配置未就绪（" + AiClient.describeReadiness() + "）");
                write(result, day);
                return result;
            }

            String reply = AiClient.chat(SYSTEM_PROMPT,
                    "日期：" + day + "\n以下是当天聚合数据（数字由本地计算，请勿改动）：\n" + payload);
            if (reply == null) {
                result.put("ok", false);
                String detail = AiClient.lastError();
                result.put("reason", detail.isEmpty() ? "调用失败（已重试），已记录本地数据"
                        : detail + "；已记录本地数据");
                write(result, day);
                EventLog.system("ai_summary_failed", "day", day, "reason", detail);
                return result;
            }

            AiSafety.Validation validation = AiSafety.validate(reply);
            result.put("ok", true);
            result.put("structured", validation.valid);
            if (!validation.valid) {
                result.put("reason", "回复未通过校验：" + validation.reason);
                result.put("raw", reply);
            } else {
                result.put("headline", validation.json.optString("headline", ""));
                result.put("failedDomains", validation.json.optJSONArray("failedDomains"));
                result.put("causes", validation.json.optJSONArray("causes"));
                result.put("suggestions", validation.json.optJSONArray("suggestions"));
            }
            write(result, day);
            notifySummary(result);
            EventLog.system("ai_summary_ok", "day", day, "structured", validation.valid);
        } catch (Throwable t) {
            Log.err(TAG, "AI 摘要生成异常", t);
            try {
                result.put("ok", false);
                result.put("reason", "异常：" + t.getClass().getSimpleName());
                write(result, day);
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    private static void notifySummary(JSONObject result) {
        try {
            String headline = result.optString("headline", "");
            if (headline.isEmpty()) {
                return;
            }
            String detail = buildText(result)
                    .replace("\n", " ")
                    .trim();
            NotificationUtil.notifyAiSummary("美杜莎 · 今日摘要", detail);
        } catch (Throwable ignored) {
        }
    }

    private static void write(JSONObject result, String day) {
        try {
            FileUtil.write2File(result.toString(), jsonFile(day));
            FileUtil.write2File(buildText(result), textFile(day));
            prune();
        } catch (Throwable t) {
            Log.err(TAG, "写入摘要失败", t);
        }
    }

    /** 人类可读文本（导出/查看用）。 */
    public static String buildText(JSONObject result) {
        StringBuilder builder = new StringBuilder();
        String day = result.optString("day", "");
        builder.append("美杜莎 AI 摘要 · ").append(day).append('\n');
        builder.append("生成时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                .format(new Date(result.optLong("at", System.currentTimeMillis())))).append('\n');
        if (!result.optBoolean("ok", false)) {
            builder.append("状态：未生成（").append(result.optString("reason", "")).append("）\n");
        }
        String headline = result.optString("headline", "");
        if (!headline.isEmpty()) {
            builder.append("结论：").append(headline).append('\n');
        }
        appendArray(builder, "失败域", result.optJSONArray("failedDomains"));
        appendArray(builder, "原因", result.optJSONArray("causes"));
        appendArray(builder, "建议", result.optJSONArray("suggestions"));
        if (!result.optBoolean("structured", true) && result.optString("raw", "").length() > 0) {
            builder.append("原始回复（未通过结构化校验，仅供查看）：\n")
                    .append(result.optString("raw", "")).append('\n');
        }
        JSONObject digest = result.optJSONObject("digest");
        JSONObject metrics = digest == null ? null : digest.optJSONObject("metrics");
        if (metrics != null) {
            builder.append("本地数字：请求 ").append(metrics.optLong("totalRequests"))
                    .append(" · 失败 ").append(metrics.optLong("totalFail"))
                    .append(" · 1009 ").append(metrics.optLong("total1009")).append('\n');
        }
        return builder.toString();
    }

    private static void appendArray(StringBuilder builder, String label, JSONArray array) {
        if (array == null || array.length() == 0) {
            return;
        }
        builder.append(label).append("：");
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) {
                builder.append("；");
            }
            builder.append(String.valueOf(array.opt(i)));
        }
        builder.append('\n');
    }

    public static JSONObject load(String day) {
        try {
            File file = jsonFile(day);
            if (!file.exists()) {
                return null;
            }
            return new JSONObject(FileUtil.readFromFile(file));
        } catch (Throwable t) {
            return null;
        }
    }

    public static JSONObject latest() {
        try {
            File[] files = dir().listFiles((dir, name) -> name.startsWith("daily-") && name.endsWith(".json"));
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

    /** 最近 N 天的摘要（只取结论字段，供期末汇总）。 */
    public static List<JSONObject> recent(int days) {
        List<JSONObject> result = new ArrayList<>();
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long now = System.currentTimeMillis();
            for (int i = 0; i < Math.max(1, days); i++) {
                String day = format.format(new Date(now - i * 86_400_000L));
                JSONObject item = load(day);
                if (item == null) {
                    continue;
                }
                JSONObject trimmed = new JSONObject();
                trimmed.put("day", day);
                trimmed.put("ok", item.optBoolean("ok", false));
                trimmed.put("headline", item.optString("headline", ""));
                trimmed.put("suggestions", item.optJSONArray("suggestions"));
                result.add(trimmed);
            }
        } catch (Throwable t) {
            Log.err(TAG, "读取近期摘要失败", t);
        }
        return result;
    }

    private static void prune() {
        try {
            File[] files = dir().listFiles((dir, name) -> name.startsWith("daily-"));
            if (files == null || files.length <= KEEP_DAYS * 2) {
                return;
            }
            List<File> sorted = new ArrayList<>();
            for (File file : files) {
                sorted.add(file);
            }
            sorted.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            int remove = sorted.size() - KEEP_DAYS * 2;
            for (int i = 0; i < remove; i++) {
                //noinspection ResultOfMethodCallIgnored
                sorted.get(i).delete();
            }
        } catch (Throwable ignored) {
        }
    }
}