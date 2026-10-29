package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ai.AiSafety;

/**
 * 4.0.0 未知任务样本采集：遇到分类不出来的任务就记一条，**只在本地**。
 * <p>采在 4.0.0、分类在 4.1：没有样本，之后给 kind/risk/manualFallback 定规则就是猜。
 * <p>只记域、服务端类型码、标题、时间型字段，标题里不会出现账号；长数字与链接一律脱敏。
 */
public final class UnknownTaskSamples {

    private static final String TAG = "UnknownTaskSamples";
    private static final String FILE = "unknown_tasks.jsonl";
    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private UnknownTaskSamples() {
    }

    private static File file() {
        return new File(AiDailySummary.dir(), FILE);
    }

    /**
     * @param domain       归属域（水印/森林/庄园…）
     * @param taskType     服务端 taskType / sceneCode，可能是空
     * @param title        任务标题
     * @param timeCount    时间型字段
     * @param taskRequire  要求数
     * @param taskProgress 进度
     * @param autoComplete 服务端是否自动完成
     */
    public static void record(String domain, String taskType, String title, long timeCount,
                              int taskRequire, int taskProgress, boolean autoComplete) {
        try {
            if (title == null || title.trim().isEmpty()) {
                return;
            }
            JSONObject item = new JSONObject();
            item.put("at", System.currentTimeMillis());
            item.put("day", io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore.today());
            item.put("domain", nullToEmpty(domain));
            item.put("taskType", nullToEmpty(taskType));
            item.put("title", AiSafety.redact(title.trim()));
            item.put("timeCount", timeCount);
            item.put("taskRequire", taskRequire);
            item.put("taskProgress", taskProgress);
            item.put("autoComplete", autoComplete);
            append(item.toString());
        } catch (Throwable t) {
            Log.err(TAG, "记录未知任务失败", t);
        }
    }

    private static void append(String line) {
        File file = file();
        try {
            if (file.exists() && file.length() > MAX_BYTES) {
                return;
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable t) {
            Log.err(TAG, "写入未知任务失败", t);
        }
    }

    /** 按 key 去重后的样本（key = 域|taskType|标题），带出现次数与最近时间。 */
    public static JSONArray aggregated() {
        JSONArray result = new JSONArray();
        try {
            File file = file();
            if (!file.exists()) {
                return result;
            }
            java.util.Map<String, JSONObject> map = new java.util.LinkedHashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    try {
                        JSONObject item = new JSONObject(trimmed);
                        String key = item.optString("domain") + '|' + item.optString("taskType")
                                + '|' + item.optString("title");
                        JSONObject entry = map.get(key);
                        if (entry == null) {
                            entry = new JSONObject();
                            entry.put("domain", item.optString("domain"));
                            entry.put("taskType", item.optString("taskType"));
                            entry.put("title", item.optString("title"));
                            entry.put("timeCount", item.optLong("timeCount"));
                            entry.put("taskRequire", item.optInt("taskRequire"));
                            entry.put("taskProgress", item.optInt("taskProgress"));
                            entry.put("autoComplete", item.optBoolean("autoComplete"));
                            entry.put("count", 0);
                            entry.put("lastAt", 0L);
                            map.put(key, entry);
                        }
                        entry.put("count", entry.optInt("count") + 1);
                        entry.put("lastAt", Math.max(entry.optLong("lastAt"), item.optLong("at")));
                    } catch (Throwable ignored) {
                    }
                }
            }
            List<JSONObject> values = new ArrayList<>(map.values());
            values.sort((a, b) -> Integer.compare(b.optInt("count"), a.optInt("count")));
            for (JSONObject value : values) {
                result.put(value);
            }
        } catch (Throwable t) {
            Log.err(TAG, "读取未知任务失败", t);
        }
        return result;
    }

    /** 供摘要/期末报告使用的精简清单。 */
    public static JSONArray topUnknown(int limit) {
        JSONArray aggregated = aggregated();
        JSONArray result = new JSONArray();
        for (int i = 0; i < aggregated.length() && i < limit; i++) {
            JSONObject item = aggregated.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject trimmed = new JSONObject();
            try {
                trimmed.put("domain", item.optString("domain"));
                trimmed.put("title", item.optString("title"));
                trimmed.put("timeCount", item.optLong("timeCount"));
                trimmed.put("count", item.optInt("count"));
            } catch (Throwable ignored) {
            }
            result.put(trimmed);
        }
        return result;
    }

    public static int totalCount() {
        return aggregated().length();
    }

    public static File exportFile() {
        return file();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** 测试/排障用：读原始行数。 */
    public static int rawLineCount() {
        try {
            File file = file();
            if (!file.exists()) {
                return 0;
            }
            String text = FileUtil.readFromFile(file);
            if (text == null || text.isEmpty()) {
                return 0;
            }
            int count = 0;
            Iterator<String> lines = text.lines().iterator();
            while (lines.hasNext()) {
                if (!lines.next().trim().isEmpty()) {
                    count++;
                }
            }
            return count;
        } catch (Throwable t) {
            return 0;
        }
    }
}