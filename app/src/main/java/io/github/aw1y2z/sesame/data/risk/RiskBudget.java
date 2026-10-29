package io.github.aw1y2z.sesame.data.risk;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

import io.github.aw1y2z.sesame.data.metrics.RpcMetrics;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;

/**
 * 4.0.0 风险预算框架。四道闸：每日硬上限、令牌桶、最小间隔、试探单发。
 * <p><b>默认影子模式</b>：只计算“本应拦截”并记录，不改变任何执行。切到 enforce 才真正返回拦截。
 * <p>手动 override 按域放行一段时间，绕过令牌桶/间隔/上限，但会记录。
 */
public final class RiskBudget {

    private static final String TAG = "RiskBudget";
    private static final String CONFIG_FILE = "risk_budget.json";
    private static final String SHADOW_FILE = "budget_shadow.json";
    private static final String MODE_SHADOW = "shadow";
    private static final String MODE_ENFORCE = "enforce";

    private static final Object LOCK = new Object();
    private static final Map<String, Bucket> BUCKETS = new HashMap<>();
    private static final Map<String, Long> LAST_AT = new HashMap<>();
    private static final Map<String, long[]> DAILY = new HashMap<>();
    private static final Map<String, Long> OVERRIDES = new HashMap<>();
    private static final Map<String, ProbeState> PROBES = new HashMap<>();

    private static String mode = MODE_SHADOW;
    private static String cachedDay = "";
    private static long loadedMtime = -1L;
    /** 配置存在性检查节流：请求路径上不每次都 stat 文件。 */
    private static long lastConfigCheckAt = 0L;
    private static final long CONFIG_CHECK_INTERVAL_MS = 5000L;
    private static JSONObject scopesConfig = new JSONObject();
    private static LongSupplier clock = System::currentTimeMillis;

    private RiskBudget() {
    }

    /** 每域默认参数；无对应项时用 DEFAULT。 */
    private static final ScopeLimit DEFAULT = new ScopeLimit(300, 5.0, 10, 4000L);
    private static final Map<String, ScopeLimit> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("forest.collect", new ScopeLimit(400, 8.0, 16, 3000L));
        DEFAULTS.put("forest.hunt", new ScopeLimit(180, 4.0, 8, 4000L));
        DEFAULTS.put("forest.daily", new ScopeLimit(420, 8.0, 16, 3000L));
        DEFAULTS.put("forest.ip", new ScopeLimit(60, 2.0, 4, 6000L));
        DEFAULTS.put("forest", new ScopeLimit(600, 8.0, 16, 3000L));
        DEFAULTS.put("farm", new ScopeLimit(400, 6.0, 12, 4000L));
        DEFAULTS.put("ocean", new ScopeLimit(300, 5.0, 10, 4000L));
        DEFAULTS.put("orchard", new ScopeLimit(350, 5.0, 10, 4000L));
        DEFAULTS.put("dodo", new ScopeLimit(200, 4.0, 8, 5000L));
        DEFAULTS.put("sports", new ScopeLimit(200, 4.0, 8, 5000L));
        DEFAULTS.put("member", new ScopeLimit(150, 3.0, 6, 6000L));
        DEFAULTS.put("golden", new ScopeLimit(150, 3.0, 6, 6000L));
        DEFAULTS.put("green", new ScopeLimit(80, 2.0, 4, 8000L));
        DEFAULTS.put("stall", new ScopeLimit(80, 2.0, 4, 8000L));
    }

    public static final class ScopeLimit {
        public final int dailyCap;
        public final double tokensPerMinute;
        public final int burst;
        public final long minIntervalMs;

        public ScopeLimit(int dailyCap, double tokensPerMinute, int burst, long minIntervalMs) {
            this.dailyCap = dailyCap;
            this.tokensPerMinute = tokensPerMinute;
            this.burst = burst;
            this.minIntervalMs = minIntervalMs;
        }
    }

    public static final class Decision {
        public final boolean allowed;
        public final boolean wouldBlock;
        public final String reason;

        Decision(boolean allowed, boolean wouldBlock, String reason) {
            this.allowed = allowed;
            this.wouldBlock = wouldBlock;
            this.reason = reason;
        }

        public static Decision allow() {
            return new Decision(true, false, "");
        }
    }

    private static final class Bucket {
        double tokens;
        long lastRefillAt;
    }

    private static final class ProbeState {
        int allowed;
        boolean pending;
        long openedAt;
    }

    public static File configFile() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, CONFIG_FILE);
    }

    private static File shadowFile() {
        File dir = new File(FileUtil.MAIN_DIRECTORY_FILE, "metrics");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, SHADOW_FILE);
    }

    public static void setClockForTest(LongSupplier supplier) {
        clock = supplier == null ? System::currentTimeMillis : supplier;
    }

    private static long now() {
        return clock.getAsLong();
    }

    private static void reloadIfNeeded() {
        long nowAt = now();
        if (nowAt - lastConfigCheckAt < CONFIG_CHECK_INTERVAL_MS) {
            return;
        }
        lastConfigCheckAt = nowAt;
        File file = configFile();
        long mtime = file.exists() ? file.lastModified() : 0L;
        if (mtime == loadedMtime) {
            return;
        }
        loadedMtime = mtime;
        mode = MODE_SHADOW;
        scopesConfig = new JSONObject();
        OVERRIDES.clear();
        if (mtime == 0L) {
            return;
        }
        try {
            JSONObject root = new JSONObject(FileUtil.readFromFile(file));
            String m = root.optString("mode", MODE_SHADOW).trim().toLowerCase(Locale.ROOT);
            mode = MODE_ENFORCE.equals(m) ? MODE_ENFORCE : MODE_SHADOW;
            JSONObject scopes = root.optJSONObject("scopes");
            if (scopes != null) {
                scopesConfig = scopes;
            }
            JSONObject overrides = root.optJSONObject("overrides");
            if (overrides != null) {
                Iterator<String> keys = overrides.keys();
                long at = now();
                while (keys.hasNext()) {
                    String scope = keys.next();
                    JSONObject value = overrides.optJSONObject(scope);
                    if (value == null) {
                        continue;
                    }
                    long until = value.optLong("until", 0L);
                    if (until > at) {
                        OVERRIDES.put(scope, until);
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "读取风险预算配置失败，按影子模式运行", t);
        }
    }

    public static boolean isEnforcing() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return MODE_ENFORCE.equals(mode);
        }
    }

    public static String mode() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return mode;
        }
    }

    /** 查询/列表类接口永远不拦：状态探针被挡住会让整个面板失联，且它们几乎不触发风控。 */
    static boolean isReadOnly(String method) {
        if (method == null) {
            return false;
        }
        String m = method.toLowerCase(Locale.ROOT);
        return m.contains("query") || m.contains("list") || m.contains("detail")
                || m.contains("status") || m.contains(".info") || m.contains("config")
                || m.contains("home") || m.contains("ranking") || m.contains("record");
    }

    private static ScopeLimit limitOf(String scope) {        ScopeLimit base = DEFAULTS.get(scope);
        if (base == null) {
            base = DEFAULT;
        }
        JSONObject custom = scopesConfig.optJSONObject(scope);
        if (custom == null) {
            return base;
        }
        return new ScopeLimit(
                custom.optInt("dailyCap", base.dailyCap),
                custom.optDouble("tokensPerMinute", base.tokensPerMinute),
                custom.optInt("burst", base.burst),
                custom.optLong("minIntervalMs", base.minIntervalMs));
    }

    /**
     * 请求前判定。影子模式下永远放行，只记录“本应拦截”。
     * 注意：影子模式同样消耗模拟令牌，这样统计出的“本应拦截次数”才有参考价值。
     */
    public static Decision decide(String method, String scope) {
        synchronized (LOCK) {
            reloadIfNeeded();
            String s = scope == null || scope.trim().isEmpty() ? "unknown" : scope;
            long at = now();
            rollDayIfNeeded(at);

            String overrideReason = "";
            Long until = OVERRIDES.get(s);
            if (until != null && until > at) {
                overrideReason = "override";
            } else if (until != null) {
                OVERRIDES.remove(s);
            }

            ScopeLimit limit = limitOf(s);
            String reason = "";
            boolean blocked = false;
            boolean readOnly = isReadOnly(method);

            long[] daily = DAILY.get(s);
            if (daily == null) {
                daily = new long[1];
                DAILY.put(s, daily);
            }

            if (overrideReason.isEmpty() && !readOnly) {
                if (daily[0] >= limit.dailyCap) {
                    blocked = true;
                    reason = "dailyCap";
                } else {
                    Bucket bucket = BUCKETS.get(s);
                    if (bucket == null) {
                        bucket = new Bucket();
                        bucket.tokens = limit.burst;
                        bucket.lastRefillAt = at;
                        BUCKETS.put(s, bucket);
                    }
                    refill(bucket, limit, at);
                    if (bucket.tokens < 1.0) {
                        blocked = true;
                        reason = "tokenBucket";
                    } else {
                        Long last = LAST_AT.get(s);
                        if (last != null && at - last < limit.minIntervalMs) {
                            blocked = true;
                            reason = "minInterval";
                        }
                    }
                    if (!blocked) {
                        bucket.tokens -= 1.0;
                        LAST_AT.put(s, at);
                    }
                }
            }

            ProbeState probe = PROBES.get(s);
            if (!blocked && !readOnly && probe != null) {
                if (probe.pending) {
                    blocked = true;
                    reason = "probePending";
                } else if (probe.allowed >= 1) {
                    blocked = true;
                    reason = "probeUsed";
                }
            }

            if (!readOnly) {
                daily[0]++;
            }

            if (blocked) {
                recordShadow(s, method, reason, at);
                if (MODE_ENFORCE.equals(mode)) {
                    Log.i(TAG, "风险预算拦截 " + s + " · " + reason + " · " + method);
                    EventLog.risk("budget_block", "scope", s, "reason", reason, "method", method);
                    return new Decision(false, true, reason);
                }
                return new Decision(true, true, reason);
            }
            if (probe != null && !probe.pending && probe.allowed < 1) {
                probe.allowed++;
                probe.pending = true;
            }
            return new Decision(true, false, overrideReason);
        }
    }

    /** 请求后回填试探结果：试探那一发若 1009，本域进入“试探失败”，等下次冷却结束再开一发。 */
    public static void onResult(String scope, RpcMetrics.Outcome outcome) {
        if (scope == null || outcome == null) {
            return;
        }
        synchronized (LOCK) {
            ProbeState probe = PROBES.get(scope);
            if (probe == null || !probe.pending) {
                return;
            }
            probe.pending = false;
            if (outcome == RpcMetrics.Outcome.C1009) {
                probe.allowed = 1;
                EventLog.risk("budget_probe_failed", "scope", scope);
            } else {
                PROBES.remove(scope);
            }
        }
    }

    /** 冷却结束后由调度器/风控恢复路径调用：开启单次试探。 */
    public static void beginProbe(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            ProbeState state = new ProbeState();
            state.openedAt = now();
            PROBES.put(scope, state);
        }
    }

    public static void clearProbe(String scope) {
        synchronized (LOCK) {
            PROBES.remove(scope);
        }
    }

    /** 手动放行：绕过令牌桶/间隔/上限，minutes<=0 表示清除。 */
    public static void override(String scope, int minutes, String note) {
        if (scope == null || scope.trim().isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            reloadIfNeeded();
            long at = now();
            try {
                JSONObject root = new JSONObject();
                File file = configFile();
                if (file.exists()) {
                    try {
                        root = new JSONObject(FileUtil.readFromFile(file));
                    } catch (Throwable ignored) {
                    }
                }
                JSONObject overrides = root.optJSONObject("overrides");
                if (overrides == null) {
                    overrides = new JSONObject();
                }
                if (minutes <= 0) {
                    overrides.remove(scope);
                    OVERRIDES.remove(scope);
                } else {
                    long until = at + minutes * 60_000L;
                    JSONObject item = new JSONObject();
                    item.put("until", until);
                    item.put("note", note == null ? "" : note);
                    item.put("at", at);
                    overrides.put(scope, item);
                    OVERRIDES.put(scope, until);
                }
                root.put("schemaVersion", 1);
                root.put("mode", mode);
                root.put("overrides", overrides);
                if (!root.has("scopes")) {
                    root.put("scopes", new JSONObject());
                }
                if (FileUtil.write2File(root.toString(), file)) {
                    loadedMtime = file.lastModified();
                }
                EventLog.risk("budget_override", "scope", scope, "minutes", minutes);
            } catch (Throwable t) {
                Log.err(TAG, "写入手动放行失败", t);
            }
        }
    }

    public static List<String> activeOverrides() {
        synchronized (LOCK) {
            reloadIfNeeded();
            long at = now();
            List<String> result = new ArrayList<>();
            for (Map.Entry<String, Long> entry : OVERRIDES.entrySet()) {
                if (entry.getValue() > at) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }
    }

    private static void refill(Bucket bucket, ScopeLimit limit, long at) {
        long elapsed = Math.max(0L, at - bucket.lastRefillAt);
        if (elapsed <= 0L) {
            return;
        }
        double add = elapsed * (limit.tokensPerMinute / 60_000.0);
        bucket.tokens = Math.min(limit.burst, bucket.tokens + add);
        bucket.lastRefillAt = at;
    }

    private static void rollDayIfNeeded(long at) {
        String day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                .format(new java.util.Date(at));
        if (!day.equals(cachedDay)) {
            cachedDay = day;
            DAILY.clear();
        }
    }

    /** 影子统计按天聚合：{days:{day:{scope:{wouldBlock, tokenBucket, minInterval, dailyCap, probe, lastMethod}}}} */
    private static void recordShadow(String scope, String method, String reason, long at) {
        try {
            File file = shadowFile();
            JSONObject root = new JSONObject();
            if (file.exists()) {
                try {
                    root = new JSONObject(FileUtil.readFromFile(file));
                } catch (Throwable ignored) {
                }
            }
            JSONObject days = root.optJSONObject("days");
            if (days == null) {
                days = new JSONObject();
            }
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                    .format(new java.util.Date(at));
            JSONObject dayObject = days.optJSONObject(day);
            if (dayObject == null) {
                dayObject = new JSONObject();
                days.put(day, dayObject);
            }
            JSONObject item = dayObject.optJSONObject(scope);
            if (item == null) {
                item = new JSONObject();
                dayObject.put(scope, item);
            }
            item.put("wouldBlock", item.optInt("wouldBlock") + 1);
            item.put(reason, item.optInt(reason) + 1);
            item.put("lastMethod", method == null ? "" : method);
            item.put("lastAt", at);
            pruneDays(days);
            root.put("schemaVersion", 1);
            root.put("mode", mode);
            root.put("days", days);
            FileUtil.write2File(root.toString(), file);
        } catch (Throwable t) {
            Log.err(TAG, "记录影子预算失败", t);
        }
    }

    private static void pruneDays(JSONObject days) {
        List<String> names = new ArrayList<>();
        Iterator<String> keys = days.keys();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        if (names.size() <= 7) {
            return;
        }
        names.sort(String::compareTo);
        for (int i = 0; i < names.size() - 7; i++) {
            days.remove(names.get(i));
        }
    }

    /** 最近的影子统计（供状态页/AI 摘要）。 */
    public static JSONObject shadowSummary(int days) {
        JSONObject result = new JSONObject();
        try {
            File file = shadowFile();
            if (!file.exists()) {
                return result;
            }
            JSONObject root = new JSONObject(FileUtil.readFromFile(file));
            JSONObject all = root.optJSONObject("days");
            if (all == null) {
                return result;
            }
            List<String> names = new ArrayList<>();
            Iterator<String> keys = all.keys();
            while (keys.hasNext()) {
                names.add(keys.next());
            }
            names.sort(String::compareTo);
            int from = Math.max(0, names.size() - Math.max(1, days));
            JSONObject merged = new JSONObject();
            for (int i = from; i < names.size(); i++) {
                JSONObject dayObject = all.optJSONObject(names.get(i));
                if (dayObject == null) {
                    continue;
                }
                Iterator<String> scopes = dayObject.keys();
                while (scopes.hasNext()) {
                    String scope = scopes.next();
                    JSONObject item = dayObject.optJSONObject(scope);
                    if (item == null) {
                        continue;
                    }
                    JSONObject target = merged.optJSONObject(scope);
                    if (target == null) {
                        target = new JSONObject();
                        merged.put(scope, target);
                    }
                    target.put("wouldBlock", target.optInt("wouldBlock") + item.optInt("wouldBlock"));
                    for (String reason : new String[]{"tokenBucket", "minInterval", "dailyCap", "probePending", "probeUsed"}) {
                        target.put(reason, target.optInt(reason) + item.optInt(reason));
                    }
                }
            }
            result.put("byScope", merged);
            result.put("mode", mode);
        } catch (Throwable t) {
            Log.err(TAG, "读取影子预算失败", t);
        }
        return result;
    }

    /** 给“建议预算”：把影子拦截按原因归到最紧的那道闸。 */
    public static String suggestionFor(String scope) {
        JSONObject shadow = shadowSummary(7).optJSONObject("byScope");
        JSONObject item = shadow == null ? null : shadow.optJSONObject(scope);
        if (item == null || item.optInt("wouldBlock") <= 0) {
            return "";
        }
        int bucket = item.optInt("tokenBucket");
        int interval = item.optInt("minInterval");
        int cap = item.optInt("dailyCap");
        int probe = item.optInt("probePending") + item.optInt("probeUsed");
        int max = Math.max(Math.max(bucket, interval), Math.max(cap, probe));
        if (max == cap) {
            return "每日上限偏紧";
        }
        if (max == bucket) {
            return "令牌桶偏紧";
        }
        if (max == interval) {
            return "最小间隔偏紧";
        }
        return "试探受限";
    }

    static void resetForTest() {
        synchronized (LOCK) {
            BUCKETS.clear();
            LAST_AT.clear();
            DAILY.clear();
            PROBES.clear();
            OVERRIDES.clear();
            scopesConfig = new JSONObject();
            mode = MODE_SHADOW;
            cachedDay = "";
            lastConfigCheckAt = 0L;
            clock = System::currentTimeMillis;
        }
    }
}
