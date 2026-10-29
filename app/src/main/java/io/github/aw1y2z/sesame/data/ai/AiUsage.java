package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 4.0.0 AI 用量估算（本地）：只记次数与字符数，不记内容。
 * <p>token 用「汉字数 + 其他字符/4」估算——中文为主的内容这个口径比较接近真实计费，
 * 但仍标“估算”，不当作账单。
 */
public final class AiUsage {

    private static final String TAG = "AiUsage";
    private static final String FILE = "usage.json";
    private static final int KEEP_DAYS = 30;

    private AiUsage() {
    }

    private static File file() {
        return new File(AiDailySummary.dir(), FILE);
    }

    /** 记一次成功调用。 */
    public static void record(String inputText, String outputText) {
        recordInternal(inputText, outputText, 0L);
    }

    /** 服务端返回了真实 token 数时优先用它（比本地估算准）。 */
    public static void recordWithTokens(String inputText, String outputText, long realTokens) {
        recordInternal(inputText, outputText, realTokens);
    }

    private static void recordInternal(String inputText, String outputText, long realTokens) {
        try {
            int inChars = inputText == null ? 0 : inputText.length();
            int outChars = outputText == null ? 0 : outputText.length();
            long tokens = realTokens > 0L
                    ? realTokens
                    : estimateTokens(inputText) + estimateTokens(outputText);
            String day = io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore.today();
            JSONObject root = load();
            JSONObject days = root.optJSONObject("days");
            if (days == null) {
                days = new JSONObject();
            }
            JSONObject item = days.optJSONObject(day);
            if (item == null) {
                item = new JSONObject();
                days.put(day, item);
            }
            item.put("calls", item.optInt("calls") + 1);
            item.put("inChars", item.optLong("inChars") + inChars);
            item.put("outChars", item.optLong("outChars") + outChars);
            item.put("estTokens", item.optLong("estTokens") + tokens);
            if (realTokens > 0L) {
                item.put("realTokens", item.optLong("realTokens") + realTokens);
            }
            prune(days);
            root.put("schemaVersion", 1);
            root.put("days", days);
            root.put("updatedAt", System.currentTimeMillis());
            FileUtil.write2File(root.toString(), file());
        } catch (Throwable t) {
            Log.err(TAG, "记录 AI 用量失败", t);
        }
    }

    private static JSONObject load() {
        try {
            File file = file();
            if (!file.exists()) {
                return new JSONObject();
            }
            return new JSONObject(FileUtil.readFromFile(file));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    /** 今日用量：{calls, inChars, outChars, estTokens} */
    public static JSONObject today() {
        try {
            JSONObject days = load().optJSONObject("days");
            if (days == null) {
                return empty();
            }
            JSONObject item = days.optJSONObject(
                    io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore.today());
            return item == null ? empty() : item;
        } catch (Throwable t) {
            return empty();
        }
    }

    /** 最近 N 天合计。 */
    public static JSONObject recent(int days) {
        JSONObject result = empty();
        try {
            JSONObject all = load().optJSONObject("days");
            if (all == null) {
                return result;
            }
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            long now = System.currentTimeMillis();
            long calls = 0;
            long tokens = 0;
            long inChars = 0;
            long outChars = 0;
            for (int i = 0; i < Math.max(1, days); i++) {
                String day = format.format(new Date(now - i * 86_400_000L));
                JSONObject item = all.optJSONObject(day);
                if (item == null) {
                    continue;
                }
                calls += item.optInt("calls");
                tokens += item.optLong("estTokens");
                inChars += item.optLong("inChars");
                outChars += item.optLong("outChars");
            }
            result.put("calls", calls);
            result.put("estTokens", tokens);
            result.put("inChars", inChars);
            result.put("outChars", outChars);
        } catch (Throwable t) {
            Log.err(TAG, "读取 AI 用量失败", t);
        }
        return result;
    }

    /**
     * 估算 token：汉字按 1:1，其余字符按 4:1。
     * <p>中文占绝大多数的 prompt/回复，这个口径比“按字数直接除”更接近真实计费。
     */
    public static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        long hanzi = 0;
        long other = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') {
                hanzi++;
            } else {
                other++;
            }
        }
        return hanzi + (other + 3) / 4;
    }

    /** 按中文习惯显示量级：1,234 → 1,234；12,345 → 1.2 万；1,234,567 → 123.5 万；1.2 亿。 */
    public static String formatTokens(long tokens) {
        if (tokens < 10_000L) {
            return String.format(Locale.CHINA, "%,d", tokens);
        }
        if (tokens < 100_000_000L) {
            return String.format(Locale.CHINA, "%.1f 万", tokens / 10_000.0);
        }
        return String.format(Locale.CHINA, "%.2f 亿", tokens / 100_000_000.0);
    }

    /**
     * 给设置页的预估：按“每天 N 条摘要 + M 条手动说明”的典型量算。
     * <p>数字来自本地估算口径，不是账单承诺。
     */
    public static String estimateMonthly(int summaryPerDay, int hintsPerDay,
                                         long summaryTokensPerCall, long hintTokensPerCall) {
        long perDay = (long) summaryPerDay * summaryTokensPerCall
                + (long) hintsPerDay * hintTokensPerCall;
        long month = perDay * 30L;
        return "每次摘要约 " + formatTokens(summaryTokensPerCall) + " tokens，"
                + "每条手动说明约 " + formatTokens(hintTokensPerCall) + " tokens；"
                + "按每天 " + summaryPerDay + " 次摘要 + " + hintsPerDay + " 条说明估，"
                + "每天约 " + formatTokens(perDay) + "，每月约 " + formatTokens(month) + " tokens";
    }

    private static JSONObject empty() {
        JSONObject result = new JSONObject();
        try {
            result.put("calls", 0);
            result.put("inChars", 0L);
            result.put("outChars", 0L);
            result.put("estTokens", 0L);
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static void prune(JSONObject days) {
        Iterator<String> keys = days.keys();
        java.util.List<String> names = new java.util.ArrayList<>();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        if (names.size() <= KEEP_DAYS) {
            return;
        }
        names.sort(String::compareTo);
        for (int i = 0; i < names.size() - KEEP_DAYS; i++) {
            days.remove(names.get(i));
        }
    }

    /** 测试用：清空本地用量。 */
    static void resetForTest() {
        try {
            File file = file();
            if (file.exists()) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        } catch (Throwable ignored) {
        }
    }
}