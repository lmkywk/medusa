package io.github.aw1y2z.sesame.data.metrics;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.RunScope;

/**
 * 4.0.0 请求级指标：每个域 / 每个 RPC 方法的请求数、耗时、1009/1004、失败原因。
 * <p>只记方法名与状态，不记任务标题、不记 URL、不记请求参数。按天聚合，保留 7 天。
 * <p>记录走内存累加 + 节流落盘（25 条或 60 秒），单次请求不做 IO。
 */
public final class RpcMetrics {

    private static final String TAG = "RpcMetrics";
    private static final String DIR_NAME = "metrics";
    private static final String FILE_NAME = "rpc_metrics.json";
    private static final int RETENTION_DAYS = 7;
    private static final int MAX_ENTRIES_PER_DAY = 1500;
    private static final int METHOD_MAX_LEN = 96;
    private static final int FLUSH_EVERY = 25;
    private static final long FLUSH_INTERVAL_MS = 60_000L;

    private static final Object LOCK = new Object();
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();

    private static long lastFlushAt = 0L;
    private static int pending = 0;
    private static String cachedDay = "";

    private RpcMetrics() {
    }

    public static final class Entry {
        public long count;
        public long ok;
        public long empty;
        public long c1009;
        public long c1004;
        public long fail;
        public long blocked;
        public long totalMs;
        public long maxMs;

        synchronized void add(long ms, Outcome outcome) {
            count++;
            totalMs += Math.max(0L, ms);
            if (ms > maxMs) {
                maxMs = ms;
            }
            switch (outcome) {
                case OK:
                    ok++;
                    break;
                case EMPTY:
                    empty++;
                    break;
                case C1009:
                    c1009++;
                    break;
                case C1004:
                    c1004++;
                    break;
                case BLOCKED:
                    blocked++;
                    break;
                default:
                    fail++;
                    break;
            }
        }

        synchronized Entry copy() {
            Entry e = new Entry();
            e.count = count;
            e.ok = ok;
            e.empty = empty;
            e.c1009 = c1009;
            e.c1004 = c1004;
            e.fail = fail;
            e.blocked = blocked;
            e.totalMs = totalMs;
            e.maxMs = maxMs;
            return e;
        }

        synchronized long avgMs() {
            return count == 0 ? 0 : totalMs / count;
        }

        synchronized boolean hasRisk() {
            return c1009 > 0 || c1004 > 0;
        }
    }

    public enum Outcome {
        OK,
        EMPTY,
        C1009,
        C1004,
        FAIL,
        /** 被本地风险预算拦下，没有真正发请求 */
        BLOCKED
    }

    /** 记录一次请求；任何异常都不向调用方抛出。 */
    public static void record(String method, long elapsedMs, String response) {
        record(method, elapsedMs, classify(response));
    }

    public static void record(String method, long elapsedMs, Outcome outcome) {
        try {
            String safeMethod = normalizeMethod(method);
            String scope = scopeOf(safeMethod);
            String day = dayKey();
            Entry entry = entryOf(day + '|' + scope + '|' + safeMethod);
            entry.add(elapsedMs, outcome == null ? Outcome.FAIL : outcome);
            boolean flush;
            synchronized (LOCK) {
                pending++;
                flush = pending >= FLUSH_EVERY || System.currentTimeMillis() - lastFlushAt >= FLUSH_INTERVAL_MS;
            }
            if (flush) {
                flush();
            }
        } catch (Throwable t) {
            Log.err(TAG, "记录请求指标失败", t);
        }
    }

    /** 记录一次“本应发出但被预算拦下”的请求。 */
    public static void recordBlocked(String method, String reason) {
        try {
            String safeMethod = normalizeMethod(method);
            String scope = scopeOf(safeMethod);
            Entry entry = entryOf(dayKey() + '|' + scope + '|' + safeMethod);
            entry.add(0L, Outcome.BLOCKED);
            EventLog.risk("rpc_blocked", "scope", scope, "method", safeMethod,
                    "reason", reason == null ? "" : reason);
            flush();
        } catch (Throwable t) {
            Log.err(TAG, "记录拦截失败", t);
        }
    }

    private static Entry entryOf(String key) {
        Entry entry = ENTRIES.get(key);
        if (entry == null) {
            Entry created = new Entry();
            Entry old = ENTRIES.putIfAbsent(key, created);
            entry = old == null ? created : old;
        }
        return entry;
    }

    /** 归域：优先本线程绑定的执行目标，否则按方法名兜底。 */
    public static String scopeOf(String method) {
        try {
            String bound = RunScope.bound();
            if (bound != null && !bound.isEmpty()) {
                String mapped = RiskGuard.scopeForTarget(bound);
                return mapped == null || mapped.isEmpty() ? bound : mapped;
            }
            return RiskGuard.scopeForMethod(method);
        } catch (Throwable t) {
            return "unknown";
        }
    }

    public static Outcome classify(String response) {
        if (response == null || response.trim().isEmpty()) {
            return Outcome.EMPTY;
        }
        JSONObject jo = null;
        try {
            jo = new JSONObject(response);
        } catch (Throwable ignored) {
        }
        String code = jo != null ? extractCode(jo) : envelopeCode(response);
        if ("1009".equals(code)) {
            return Outcome.C1009;
        }
        if ("1004".equals(code)) {
            return Outcome.C1004;
        }
        if (!code.isEmpty()) {
            Boolean ok = codeIsOk(code);
            if (ok != null) {
                return ok ? Outcome.OK : Outcome.FAIL;
            }
            // 非数字且无法判断的码（例如业务自定义字符串）：不当失败，避免把正常响应当故障
            return Outcome.OK;
        }
        // 只有**顶层** success=false 才算失败；嵌套业务数据里的 success 不参与判断
        if (jo != null && jo.has("success") && !jo.optBoolean("success", true)) {
            return Outcome.FAIL;
        }
        if (jo == null && (response.contains("\"success\":false") || response.contains("\"success\": false"))) {
            return Outcome.FAIL;
        }
        return Outcome.OK;
    }

    /** @return true=成功码，false=失败码，null=无法判断（非数字的未知字符串） */
    private static Boolean codeIsOk(String code) {
        String value = code.trim();
        if (value.matches("-?\\d+")) {
            long number;
            try {
                number = Long.parseLong(value);
            } catch (Throwable t) {
                return null;
            }
            // 0 / 100 / 200 成功；102 是「稍后重试」，按可重试不算失败
            return number == 0L || number == 100L || number == 200L || number == 102L;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if ("SUCCESS".equals(upper) || "OK".equals(upper) || "TRUE".equals(upper)) {
            return true;
        }
        if ("FAIL".equals(upper) || "FAILED".equals(upper) || "ERROR".equals(upper)
                || "FALSE".equals(upper)) {
            return false;
        }
        return null;
    }

    /** 只认完整的信封，避免把任务 ID、标题里的数字当风控。 */
    private static String envelopeCode(String response) {
        if (response.contains("\"error\":1009") || response.contains("\"errorTip\":\"1009\"")
                || response.contains("\"error\":\"1009\"")) {
            return "1009";
        }
        if (response.contains("\"error\":1004") || response.contains("\"errorTip\":\"1004\"")
                || response.contains("\"error\":\"1004\"")) {
            return "1004";
        }
        return "";
    }

    private static String extractCode(JSONObject jo) {
        if (jo == null) {
            return "";
        }
        try {
            String[] keys = {"errorTip", "error", "errorCode", "resultCode"};
            for (String key : keys) {
                String value = jo.optString(key, "").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
            JSONObject result = jo.optJSONObject("result");
            if (result != null) {
                for (String key : keys) {
                    String value = result.optString(key, "").trim();
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return "unknown";
        }
        String m = method.trim();
        int pos = m.indexOf('?');
        if (pos >= 0) {
            m = m.substring(0, pos);
        }
        return m.length() > METHOD_MAX_LEN ? m.substring(0, METHOD_MAX_LEN) : m;
    }

    private static String dayKey() {
        long now = System.currentTimeMillis();
        String day = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(now));
        if (!day.equals(cachedDay)) {
            cachedDay = day;
        }
        return day;
    }

    public static File metricsFile() {
        File dir = new File(FileUtil.MAIN_DIRECTORY_FILE, DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, FILE_NAME);
    }

    /** 落盘并把超过 7 天的桶丢掉；失败只记日志。 */
    public static void flush() {
        synchronized (LOCK) {
            try {
                JSONObject root = new JSONObject();
                File file = metricsFile();
                if (file.exists()) {
                    try {
                        String old = FileUtil.readFromFile(file);
                        if (old != null && !old.trim().isEmpty()) {
                            root = new JSONObject(old);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                JSONObject days = root.optJSONObject("days");
                if (days == null) {
                    days = new JSONObject();
                }
                int dayCount = 0;
                for (String key : new ArrayList<>(ENTRIES.keySet())) {
                    int sep = key.indexOf('|');
                    if (sep <= 0) {
                        continue;
                    }
                    String day = key.substring(0, sep);
                    String rest = key.substring(sep + 1);
                    JSONObject dayObject = days.optJSONObject(day);
                    if (dayObject == null) {
                        dayObject = new JSONObject();
                        days.put(day, dayObject);
                    }
                    if (dayObject.length() >= MAX_ENTRIES_PER_DAY) {
                        continue;
                    }
                    Entry entry = ENTRIES.get(key);
                    if (entry == null) {
                        continue;
                    }
                    Entry copy = entry.copy();
                    if (copy.count == 0) {
                        continue;
                    }
                    JSONObject item = new JSONObject();
                    item.put("n", copy.count);
                    item.put("ok", copy.ok);
                    item.put("empty", copy.empty);
                    item.put("c1009", copy.c1009);
                    item.put("c1004", copy.c1004);
                    item.put("fail", copy.fail);
                    item.put("blocked", copy.blocked);
                    item.put("ms", copy.totalMs);
                    item.put("max", copy.maxMs);
                    dayObject.put(rest, item);
                }
                prune(days);
                root.put("schemaVersion", 1);
                root.put("days", days);
                root.put("updatedAt", System.currentTimeMillis());
                if (FileUtil.write2File(root.toString(), file)) {
                    pending = 0;
                    lastFlushAt = System.currentTimeMillis();
                }
            } catch (Throwable t) {
                Log.err(TAG, "写入请求指标失败", t);
            }
        }
    }

    private static void prune(JSONObject days) {
        List<String> names = new ArrayList<>();
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        if (names.size() <= RETENTION_DAYS) {
            return;
        }
        names.sort(String::compareTo);
        int remove = names.size() - RETENTION_DAYS;
        for (int i = 0; i < remove; i++) {
            days.remove(names.get(i));
        }
        EventLog.system("metrics_pruned", "removed", remove);
    }

    /** 读取最近 N 天（含今天）的聚合；供状态页与 AI 摘要使用。 */
    public static JSONObject summary(int days) {
        flush();
        JSONObject result = new JSONObject();
        try {
            File file = metricsFile();
            if (!file.exists()) {
                return result;
            }
            JSONObject root = new JSONObject(FileUtil.readFromFile(file));
            JSONObject allDays = root.optJSONObject("days");
            if (allDays == null) {
                return result;
            }
            List<String> names = new ArrayList<>();
            Iterator<String> keys = allDays.keys();
            while (keys.hasNext()) {
                names.add(keys.next());
            }
            names.sort(String::compareTo);
            int from = Math.max(0, names.size() - Math.max(1, days));
            JSONObject byScope = new JSONObject();
            JSONObject byDay = new JSONObject();
            long totalRequests = 0L;
            long total1009 = 0L;
            long totalFail = 0L;
            for (int i = from; i < names.size(); i++) {
                String day = names.get(i);
                JSONObject dayObject = allDays.optJSONObject(day);
                if (dayObject == null) {
                    continue;
                }
                Iterator<String> entryKeys = dayObject.keys();
                JSONObject daySummary = new JSONObject();
                long dayN = 0L;
                long day1009 = 0L;
                long dayFail = 0L;
                while (entryKeys.hasNext()) {
                    String key = entryKeys.next();
                    int sep = key.indexOf('|');
                    String scope = sep > 0 ? key.substring(0, sep) : "unknown";
                    String method = sep > 0 ? key.substring(sep + 1) : key;
                    JSONObject item = dayObject.optJSONObject(key);
                    if (item == null) {
                        continue;
                    }
                    long n = item.optLong("n");
                    long c1009 = item.optLong("c1009");
                    long c1004 = item.optLong("c1004");
                    long fail = item.optLong("fail");
                    long empty = item.optLong("empty");
                    long blocked = item.optLong("blocked");
                    long ms = item.optLong("ms");
                    JSONObject scopeObject = byScope.optJSONObject(scope);
                    if (scopeObject == null) {
                        scopeObject = new JSONObject();
                        scopeObject.put("n", 0L);
                        scopeObject.put("ok", 0L);
                        scopeObject.put("fail", 0L);
                        scopeObject.put("c1009", 0L);
                        scopeObject.put("c1004", 0L);
                        scopeObject.put("empty", 0L);
                        scopeObject.put("blocked", 0L);
                        scopeObject.put("ms", 0L);
                        byScope.put(scope, scopeObject);
                    }
                    scopeObject.put("n", scopeObject.optLong("n") + n);
                    scopeObject.put("ok", scopeObject.optLong("ok") + item.optLong("ok"));
                    scopeObject.put("fail", scopeObject.optLong("fail") + fail);
                    scopeObject.put("c1009", scopeObject.optLong("c1009") + c1009);
                    scopeObject.put("c1004", scopeObject.optLong("c1004") + c1004);
                    scopeObject.put("empty", scopeObject.optLong("empty") + empty);
                    scopeObject.put("blocked", scopeObject.optLong("blocked") + blocked);
                    scopeObject.put("ms", scopeObject.optLong("ms") + ms);
                    dayN += n;
                    day1009 += c1009;
                    dayFail += fail + empty;
                    totalRequests += n;
                    total1009 += c1009;
                    totalFail += fail + empty;
                    // 方法级明细只保留有风险或失败的方法，控制摘要体积
                    if (c1009 > 0 || c1004 > 0 || fail > 0 || empty > 0) {
                        JSONObject methodItem = new JSONObject();
                        methodItem.put("n", n);
                        methodItem.put("c1009", c1009);
                        methodItem.put("c1004", c1004);
                        methodItem.put("fail", fail);
                        methodItem.put("empty", empty);
                        JSONObject methods = daySummary.optJSONObject("methods");
                        if (methods == null) {
                            methods = new JSONObject();
                            daySummary.put("methods", methods);
                        }
                        if (methods.length() < 30) {
                            methods.put(method, methodItem);
                        }
                    }
                }
                daySummary.put("n", dayN);
                daySummary.put("c1009", day1009);
                daySummary.put("fail", dayFail);
                byDay.put(day, daySummary);
            }
            result.put("days", byDay);
            result.put("byScope", byScope);
            result.put("totalRequests", totalRequests);
            result.put("total1009", total1009);
            result.put("totalFail", totalFail);
        } catch (Throwable t) {
            Log.err(TAG, "读取请求指标失败", t);
        }
        return result;
    }

    /** 今日单域统计（状态页用）。 */
    public static JSONObject todayScope(String scope) {
        JSONObject summary = summary(1);
        JSONObject byScope = summary.optJSONObject("byScope");
        JSONObject item = byScope == null ? null : byScope.optJSONObject(scope);
        return item == null ? new JSONObject() : item;
    }

    /** 测试用：清空内存累加（不删除文件）。 */
    static void resetForTest() {
        ENTRIES.clear();
        synchronized (LOCK) {
            pending = 0;
            lastFlushAt = 0L;
        }
    }
}
