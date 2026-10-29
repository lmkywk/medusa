package io.github.aw1y2z.sesame.util;

import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import io.github.aw1y2z.sesame.data.plan.PlanTargets;

/**
 * 按风险域保存暂停状态。
 * <p>1004 只暂停当前域（如 forest.collect）；1009 默认暂停整个账号。
 * 1009 只能手动恢复；1004 可由调度器在次日 00:10 做单轮试探。
 */
public final class RiskGuard {

    /**
     * 一次风险信号的处理级别。
     * <p>1009/限流只影响发起请求的那个域；只有支付宝真的弹出验证码界面才升级为账号级待验证。
     */
    public enum Signal {
        NONE,
        /** 1004/限流：暂停当前域，次日零点试探 */
        LIMIT,
        /** 1009/服务端风控拒绝：暂停当前域，其他域继续；不要求用户去找一个并不存在的滑块 */
        SERVER_REFUSAL,
        /** 真实验证码弹窗：账号级待验证，用户完成前禁止自动执行 */
        VERIFY_REQUIRED
    }

    private static final String TAG = "RiskGuard";
    private static final String STATE_FILE = "risk_state.json";
    private static final Object LOCK = new Object();

    private static long loadedMtime = -1L;
    private static boolean globalPaused = false;
    private static long globalPausedAt = 0L;
    private static String globalReason = "";
    private static String globalPausedMethod = "";
    /** 最近一次风控信号：即使选择“仅提示”，也保留给状态页/诊断。 */
    private static long lastSignalAt = 0L;
    private static String lastSignalMethod = "";
    private static String lastSignalReason = "";
    /** 1009/安全验证：必须由用户在支付宝完成滑块后手动确认，期间禁止一切自动执行。 */
    private static boolean verificationRequired = false;
    private static long verificationAt = 0L;
    private static String verificationMethod = "";
    private static String verificationReason = "";
    private static final JSONObject scopes = new JSONObject();

    /** 3.9.0：1009 先短冷却，连续命中再升级；不再一次锁整天。升级规则见 {@link RiskCooldown}。 */

    private RiskGuard() {
    }

    private static File stateFile() {
        return new File(FileUtil.MAIN_DIRECTORY_FILE, STATE_FILE);
    }

    private static void reloadIfNeeded() {
        File file = stateFile();
        long mtime = file.exists() ? file.lastModified() : 0L;
        if (mtime == loadedMtime) {
            return;
        }
        loadedMtime = mtime;
        globalPaused = false;
        globalPausedAt = 0L;
        globalReason = "";
        globalPausedMethod = "";
        lastSignalAt = 0L;
        lastSignalMethod = "";
        lastSignalReason = "";
        verificationRequired = false;
        verificationAt = 0L;
        verificationMethod = "";
        verificationReason = "";
        clearScopes();
        if (mtime == 0L) {
            return;
        }
        try {
            String json = FileUtil.readFromFile(file);
            if (json == null || json.trim().isEmpty()) {
                return;
            }
            JSONObject root = new JSONObject(json);
            // 兼容旧的全局格式 {paused,pausedAt,reason}
            if (root.has("paused") && !root.has("globalPaused")) {
                globalPaused = root.optBoolean("paused", false);
                globalPausedAt = root.optLong("pausedAt", 0L);
                globalReason = root.optString("reason", "");
                return;
            }
            globalPaused = root.optBoolean("globalPaused", false);
            globalPausedAt = root.optLong("globalPausedAt", 0L);
            globalReason = root.optString("globalReason", "");
            globalPausedMethod = root.optString("globalPausedMethod", "");
            lastSignalAt = root.optLong("lastSignalAt", 0L);
            lastSignalMethod = root.optString("lastSignalMethod", "");
            lastSignalReason = root.optString("lastSignalReason", "");
            verificationRequired = root.optBoolean("verificationRequired", false);
            verificationAt = root.optLong("verificationAt", 0L);
            verificationMethod = root.optString("verificationMethod", "");
            verificationReason = root.optString("verificationReason", "");
            // 当天出现过运动侧 1009：加载时就补上域级保护，避免验证后立刻又被自动请求撞回。
            String signalMethod = !verificationMethod.isEmpty() ? verificationMethod : lastSignalMethod;
            long signalAt = verificationAt > 0 ? verificationAt : lastSignalAt;
            if (!signalMethod.isEmpty() && signalAt > 0
                    && android.text.format.DateUtils.isToday(signalAt)
                    && (signalMethod.contains("sportshealth")
                    || signalMethod.toLowerCase().contains("antsports"))
                    && !scopes.has("sports")) {
                JSONObject sportsScope = new JSONObject();
                sportsScope.put("paused", true);
                sportsScope.put("pausedAt", System.currentTimeMillis());
                sportsScope.put("reason", "1009 后运动域保护性暂停，次日再试");
                sportsScope.put("kind", "VERIFY");
                scopes.put("sports", sportsScope);
            }
            JSONObject scopeObject = root.optJSONObject("scopes");
            if (scopeObject != null) {
                Iterator<String> keys = scopeObject.keys();
                while (keys.hasNext()) {
                    String scope = keys.next();
                    JSONObject value = scopeObject.optJSONObject(scope);
                    if (value != null) {
                        scopes.put(scope, value);
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "读取风控状态失败", t);
        }
    }

    private static void clearScopes() {
        Iterator<String> keys = scopes.keys();
        List<String> names = new ArrayList<>();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        for (String name : names) {
            scopes.remove(name);
        }
    }

    public static boolean isGlobalPaused() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return globalPaused;
        }
    }

    /** 1009/安全验证待处理：期间禁止计划、手动任务和自动建账。 */
    public static boolean isVerificationRequired() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return verificationRequired;
        }
    }

    public static String getVerificationReason() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return verificationReason;
        }
    }

    /** 1009 后立即写入待验证并暂停账号；不再区分“仅提示”策略。 */
    public static void markVerificationRequired(String method, String reason) {
        synchronized (LOCK) {
            reloadIfNeeded();
            verificationRequired = true;
            verificationAt = System.currentTimeMillis();
            verificationMethod = method == null ? "" : method;
            verificationReason = reason == null || reason.isEmpty() ? "检测到风控（1009）" : reason;
            globalPaused = true;
            globalPausedAt = verificationAt;
            globalReason = "需要验证：" + verificationReason;
            globalPausedMethod = verificationMethod;
            if (!verificationMethod.isEmpty()
                    && (verificationMethod.contains("sportshealth")
                    || verificationMethod.toLowerCase().contains("antsports"))
                    && !scopes.has("sports")) {
                try {
                    JSONObject sportsScope = new JSONObject();
                    sportsScope.put("paused", true);
                    sportsScope.put("pausedAt", System.currentTimeMillis());
                    sportsScope.put("reason", "1009 后运动域保护性暂停，次日再试");
                    sportsScope.put("kind", "VERIFY");
                    scopes.put("sports", sportsScope);
                } catch (Throwable ignored) {
                }
            }
            writeLocked();
            Log.record("风控保护🛡️需要验证，已暂停整个账号：" + verificationReason + " · " + verificationMethod);
            EventLog.risk("verify_required", "method", verificationMethod, "reason", verificationReason);
        }
    }

    /** 用户确认已完成支付宝验证后调用：清除待验证和暂停状态，但保留验证后仍易复发的域保护。 */
    public static void verifyDone() {
        synchronized (LOCK) {
            reloadIfNeeded();
            verificationRequired = false;
            verificationAt = 0L;
            verificationMethod = "";
            verificationReason = "";
            globalPaused = false;
            globalPausedAt = 0L;
            globalReason = "";
            globalPausedMethod = "";
            int kept = clearScopesExceptVerify();
            writeLocked();
            Log.record("风控保护🛡️用户已确认完成验证，自动任务恢复"
                    + (kept > 0 ? "；运动域今日保护性暂停，不再自动请求" : ""));
            EventLog.risk("verify_done", "keptScopes", kept);
        }
    }

    private static int clearScopesExceptVerify() {
        Iterator<String> keys = scopes.keys();
        List<String> names = new ArrayList<>();
        while (keys.hasNext()) {
            names.add(keys.next());
        }
        int kept = 0;
        for (String name : names) {
            JSONObject value = scopes.optJSONObject(name);
            if (value != null && "VERIFY".equals(value.optString("kind"))) {
                kept++;
                continue;
            }
            scopes.remove(name);
        }
        return kept;
    }

    /** 清理到期的域冷却；只清 pausedUntil>0 的条目，手动/账号级暂停不受影响。 */
    private static boolean clearExpiredLocked() {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        Iterator<String> keys = scopes.keys();
        while (keys.hasNext()) {
            String scope = keys.next();
            JSONObject value = scopes.optJSONObject(scope);
            if (value == null || !value.optBoolean("paused", false)) {
                continue;
            }
            long until = value.optLong("pausedUntil", 0L);
            if (until > 0L && now >= until) {
                expired.add(scope);
            }
        }
        if (expired.isEmpty()) {
            return false;
        }
        for (String scope : expired) {
            scopes.remove(scope);
        }
        writeLocked();
        Log.record("风控保护⏱已到期解除 " + expired.size() + " 个域冷却");
        EventLog.risk("cooldown_expired", "count", expired.size(), "scopes", expired.toString());
        return true;
    }

    public static boolean isPaused(String scope) {
        synchronized (LOCK) {
            reloadIfNeeded();
            clearExpiredLocked();
            if (globalPaused) {
                return true;
            }
            if (scope == null) {
                return false;
            }
            JSONObject value = scopes.optJSONObject(scope);
            return value != null && value.optBoolean("paused", false);
        }
    }

    /** 森林没有单一计划目标，手动/全局轮询跑森林时统一用这个域级 scope。 */
    public static final String SCOPE_FOREST = "forest";

    /** 计划目标 → 风控域。farm.draw / species.task 这类子目标必须映射到域，否则域暂停拦不住它。 */
    public static String scopeForTarget(String target) {
        if (target == null || target.trim().isEmpty()) {
            return null;
        }
        if (target.startsWith("forest.")) {
            return target;
        }
        if (target.startsWith("farm.")) {
            return PlanTargets.FARM;
        }
        if (target.startsWith("ocean.")) {
            return PlanTargets.OCEAN;
        }
        if (target.startsWith("orchard.")) {
            return PlanTargets.ORCHARD;
        }
        if (target.startsWith("species.")) {
            return PlanTargets.DODO;
        }
        if (target.startsWith("sports.")) {
            return PlanTargets.SPORTS;
        }
        if (target.startsWith("member.")) {
            return PlanTargets.MEMBER;
        }
        return target;
    }

    /** 目标或其所属域任一暂停即视为暂停；森林子目标还看域级 forest。 */
    public static boolean isTargetPaused(String target) {
        if (target == null || target.trim().isEmpty()) {
            return false;
        }
        if (isPaused(target)) {
            return true;
        }
        String scope = scopeForTarget(target);
        if (scope != null && !scope.equals(target) && isPaused(scope)) {
            return true;
        }
        return target.startsWith("forest.") && isPaused(SCOPE_FOREST);
    }

    /** 兼容旧调用：任意域或全账号暂停都算暂停。 */
    public static boolean isPaused() {
        return isAnyPaused();
    }

    public static boolean isAnyPaused() {
        synchronized (LOCK) {
            reloadIfNeeded();
            clearExpiredLocked();
            if (globalPaused) {
                return true;
            }
            Iterator<String> keys = scopes.keys();
            while (keys.hasNext()) {
                JSONObject value = scopes.optJSONObject(keys.next());
                if (value != null && value.optBoolean("paused", false)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static void pauseScope(String scope, String reason) {
        pauseScope(scope, reason, "LIMIT");
    }

    public static void pauseScope(String scope, String reason, String kind) {
        if (scope == null || scope.trim().isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            reloadIfNeeded();
            JSONObject value = scopes.optJSONObject(scope);
            if (value != null && value.optBoolean("paused", false)) {
                return;
            }
            try {
                value = new JSONObject();
                value.put("paused", true);
                value.put("pausedAt", System.currentTimeMillis());
                value.put("reason", reason == null ? "检测到限流" : reason);
                value.put("kind", kind == null ? "LIMIT" : kind);
                scopes.put(scope, value);
                writeLocked();
                Log.record("风控保护🛡️已暂停 " + labelOf(scope) + "：" + value.optString("reason"));
                EventLog.risk("pause_scope", "scope", scope, "kind", kind == null ? "" : kind);
            } catch (Throwable t) {
                Log.err(TAG, "暂停风险域失败", t);
            }
        }
    }

    public static void pauseGlobal(String reason) {
        pauseGlobal(reason, "");
    }

    public static void pauseGlobal(String reason, String method) {
        synchronized (LOCK) {
            reloadIfNeeded();
            if (globalPaused) {
                return;
            }
            globalPaused = true;
            globalPausedAt = System.currentTimeMillis();
            globalReason = reason == null ? "检测到账号风控" : reason;
            globalPausedMethod = method == null ? "" : method;
            writeLocked();
            Log.record("风控保护🛡️已暂停整个账号：" + globalReason + "；请在首页确认后手动恢复");
            EventLog.risk("pause_global", "method", method == null ? "" : method);
        }
    }

    public static void resumeScope(String scope) {
        synchronized (LOCK) {
            reloadIfNeeded();
            if (scope == null) {
                return;
            }
            if (scopes.remove(scope) != null) {
                writeLocked();
                Log.record("风控保护🛡️已恢复 " + labelOf(scope));
                EventLog.risk("resume_scope", "scope", scope);
            }
        }
    }

    /** 用户主动暂停全部自动化；与风控暂停共用闸门，但原因标为手动。 */
    public static void pauseAll(String reason) {
        synchronized (LOCK) {
            reloadIfNeeded();
            globalPaused = true;
            globalPausedAt = System.currentTimeMillis();
            globalReason = reason == null || reason.isEmpty() ? "手动暂停全部自动化" : reason;
            globalPausedMethod = "manual";
            writeLocked();
            Log.record("风控保护⏸" + globalReason);
            EventLog.risk("manual_pause", "reason", globalReason);
        }
    }

    public static boolean isManualPause() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return globalPaused && "manual".equals(globalPausedMethod);
        }
    }

    /** 总开关恢复：只解除用户手动暂停，不动 1009 域冷却与待验证状态。 */
    public static void resumeManual() {
        synchronized (LOCK) {
            reloadIfNeeded();
            if (!(globalPaused && "manual".equals(globalPausedMethod))) {
                return;
            }
            globalPaused = false;
            globalPausedAt = 0L;
            globalReason = "";
            globalPausedMethod = "";
            writeLocked();
            Log.record("风控保护▶手动恢复自动化");
            EventLog.risk("manual_resume", "reason", "master switch");
        }
    }

    public static void resume() {
        synchronized (LOCK) {
            reloadIfNeeded();
            boolean changed = globalPaused || scopes.length() > 0 || verificationRequired;
            globalPaused = false;
            globalPausedAt = 0L;
            globalReason = "";
            globalPausedMethod = "";
            verificationRequired = false;
            verificationAt = 0L;
            verificationMethod = "";
            verificationReason = "";
            clearScopes();
            if (changed) {
                writeLocked();
                Log.record("风控保护🛡️已手动恢复全部自动任务");
                EventLog.risk("resume", "changed", true);
            }
        }
    }

    /** 00:10 试探前调用：只清除 1004 产生的限流域，保留 1009/手动暂停。 */
    public static void resetLimitPauses() {
        synchronized (LOCK) {
            reloadIfNeeded();
            List<String> remove = new ArrayList<>();
            Iterator<String> keys = scopes.keys();
            while (keys.hasNext()) {
                String scope = keys.next();
                JSONObject value = scopes.optJSONObject(scope);
                if (value != null && "LIMIT".equals(value.optString("kind"))) {
                    remove.add(scope);
                }
            }
            for (String scope : remove) {
                scopes.remove(scope);
            }
            if (!remove.isEmpty()) {
                writeLocked();
                Log.record("风控保护🌅零点试探：已解除 1004 限流域 " + remove.size() + " 个");
            }
        }
    }

    public static List<String> getPausedScopes() {
        synchronized (LOCK) {
            reloadIfNeeded();
            clearExpiredLocked();
            List<String> result = new ArrayList<>();
            Iterator<String> keys = scopes.keys();
            while (keys.hasNext()) {
                String scope = keys.next();
                JSONObject value = scopes.optJSONObject(scope);
                if (value != null && value.optBoolean("paused", false)) {
                    result.add(scope);
                }
            }
            return result;
        }
    }

    public static String getScopeReason(String scope) {
        synchronized (LOCK) {
            reloadIfNeeded();
            clearExpiredLocked();
            JSONObject value = scopes.optJSONObject(scope);
            return value == null ? "" : value.optString("reason", "");
        }
    }

    public static String getReason() {
        synchronized (LOCK) {
            reloadIfNeeded();
            clearExpiredLocked();
            if (globalPaused) {
                return globalReason;
            }
            Iterator<String> keys = scopes.keys();
            while (keys.hasNext()) {
                JSONObject value = scopes.optJSONObject(keys.next());
                if (value != null && value.optBoolean("paused", false)) {
                    return value.optString("reason", "");
                }
            }
            return "";
        }
    }

    public static String labelOf(String scope) {
        return PlanTargets.labelOf(scope);
    }

    /**
     * 解析一次 RPC 响应里的风险信号。
     * <p>只认服务端明确返回的字段（error/errorTip/resultCode 与错误文案），不再对整段响应做裸子串匹配；
     * 1009 属于“这一发被拒”，只暂停当前域，避免一个任务领奖失败把整晚计划全部作废。
     */
    public static Signal observe(String method, String response) {
        Observation observation = classify(response);
        switch (observation.signal) {
            case SERVER_REFUSAL:
                recordSignal(method, observation.reason);
                String scope = scopeForCurrent(method);
                pauseServerRefusal(scope, observation.reason);
                EventLog.risk("server_refusal", "method", method, "scope", scope, "reason", observation.reason);
                return Signal.SERVER_REFUSAL;
            case LIMIT:
                limit(method);
                return Signal.LIMIT;
            default:
                return Signal.NONE;
        }
    }

    /** 纯解析结果：信号 + 原因，供 observe 与回放 harness 共用（无副作用）。 */
    public static final class Observation {
        public final Signal signal;
        public final String reason;

        Observation(Signal signal, String reason) {
            this.signal = signal;
            this.reason = reason;
        }
    }

    /** 只看响应内容，不写状态、不暂停、不发事件。 */
    public static Observation classify(String response) {
        if (response == null || response.length() < 8) {
            return new Observation(Signal.NONE, "");
        }
        JSONObject jo = null;
        try {
            jo = new JSONObject(response);
        } catch (Throwable ignored) {
        }
        if (jo == null) {
            // 非 JSON：只认完整的 1009/1004 信封，避免把任务 ID、标题里的数字当成风控
            if (response.contains("\"error\":1009") || response.contains("\"errorTip\":\"1009\"")
                    || response.contains("\"error\":\"1009\"")) {
                return new Observation(Signal.SERVER_REFUSAL, "服务端要求验证（1009）");
            }
            if (response.contains("\"error\":1004") || response.contains("\"errorTip\":\"1004\"")
                    || response.contains("\"error\":\"1004\"")) {
                return new Observation(Signal.LIMIT, "1004 限流");
            }
            return new Observation(Signal.NONE, "");
        }
        String code = extractCode(jo);
        if ("1009".equals(code)) {
            return new Observation(Signal.SERVER_REFUSAL, "服务端要求验证（1009）");
        }
        if ("1004".equals(code)) {
            return new Observation(Signal.LIMIT, "1004 限流");
        }
        String text = extractRiskText(jo);
        if (text.contains("操作频繁") || text.contains("操作异常")
                || text.contains("账号存在风险") || text.contains("安全限制")
                || text.contains("风控")) {
            return new Observation(Signal.SERVER_REFUSAL,
                    "服务端提示风控" + (code.isEmpty() ? "" : "（" + code + "）"));
        }
        return new Observation(Signal.NONE, "");
    }

    /**
     * 3.9.0：1009 先给 30 分钟冷却；同一域 1 小时内再次 1009 升到 2 小时；第三次 8 小时。
     * 到点自动解除，不再“本域今日暂停”；需要立刻执行时仍可手动 resumeScope。
     */
    private static void pauseServerRefusal(String scope, String reason) {
        if (scope == null || scope.trim().isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            reloadIfNeeded();
            long now = System.currentTimeMillis();
            JSONObject old = scopes.optJSONObject(scope);
            RiskCooldown.State previous = null;
            if (old != null && old.optBoolean("paused", false)) {
                long until = old.optLong("pausedUntil", 0L);
                if (until == 0L || now < until) {
                    int prevStrikes = Math.max(1, old.optInt("strikes", 1));
                    long lastStrikeAt = old.optLong("lastStrikeAt", old.optLong("pausedAt", 0L));
                    previous = new RiskCooldown.State(prevStrikes, lastStrikeAt);
                }
            }
            RiskCooldown.State next = RiskCooldown.next(previous, now);
            int strikes = next.strikes;
            long cooldown = RiskCooldown.cooldownFor(strikes);
            long until = now + cooldown;
            try {
                JSONObject value = new JSONObject();
                value.put("paused", true);
                value.put("pausedAt", old == null || old.optLong("pausedAt", 0L) == 0L ? now : old.optLong("pausedAt", now));
                value.put("lastStrikeAt", now);
                value.put("strikes", strikes);
                value.put("pausedUntil", until);
                value.put("reason", (reason == null ? "服务端要求验证（1009）" : reason)
                        + "，本域冷却至 " + new java.text.SimpleDateFormat("HH:mm", Locale.CHINA)
                        .format(new java.util.Date(until)));
                value.put("kind", "SERVER");
                scopes.put(scope, value);
                writeLocked();
                Log.record("风控保护🛡️" + labelOf(scope) + " 1009 冷却"
                        + RiskCooldown.describe(cooldown) + "（第" + strikes + "次）");
                EventLog.risk("pause_scope", "scope", scope, "kind", "SERVER",
                        "strikes", strikes, "until", until);
            } catch (Throwable t) {
                Log.err(TAG, "暂停风险域失败", t);
            }
        }
    }

    private static Signal limit(String method) {
        String scope = scopeForCurrent(method);
        pauseScope(scope, "1004 限流", "LIMIT");
        EventLog.risk("limit_scope", "method", method, "scope", scope);
        return Signal.LIMIT;
    }

    /** 优先取当前运行上下文，保证 forest/farm 共用的 doFarmTask 不会归错域。 */
    private static String scopeForCurrent(String method) {
        String current = RunScope.current();
        if (current != null && !current.trim().isEmpty()) {
            return current;
        }
        return scopeForMethod(method);
    }

    private static String extractCode(JSONObject jo) {
        String code = jo.optString("errorTip", "").trim();
        if (code.isEmpty()) {
            code = jo.optString("error", "").trim();
        }
        if (code.isEmpty()) {
            code = jo.optString("errorCode", "").trim();
        }
        if (code.isEmpty()) {
            code = jo.optString("resultCode", "").trim();
        }
        JSONObject result = jo.optJSONObject("result");
        if (code.isEmpty() && result != null) {
            code = result.optString("resultCode", "").trim();
        }
        return code;
    }

    private static String extractRiskText(JSONObject jo) {
        StringBuilder text = new StringBuilder();
        text.append(jo.optString("resultView", ""))
                .append(jo.optString("resultDesc", ""))
                .append(jo.optString("memo", ""))
                .append(jo.optString("errorMsg", ""))
                .append(jo.optString("errorMessage", ""));
        JSONObject result = jo.optJSONObject("result");
        if (result != null) {
            text.append(result.optString("resultView", ""))
                    .append(result.optString("resultDesc", ""));
        }
        return text.toString();
    }

    /** 记录最近一次风控信号（不改变暂停状态，供“仅提示”策略和状态页使用）。 */
    private static void recordSignal(String method, String reason) {
        synchronized (LOCK) {
            reloadIfNeeded();
            lastSignalAt = System.currentTimeMillis();
            lastSignalMethod = method == null ? "" : method;
            lastSignalReason = reason == null ? "" : reason;
            writeLocked();
        }
        Log.record("风控信号🛡️" + (reason == null ? "" : reason)
                + (method == null || method.isEmpty() ? "" : " · " + method));
    }

    public static String getPausedByMethod() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return globalPausedMethod;
        }
    }

    public static long getLastSignalAt() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return lastSignalAt;
        }
    }

    public static String getLastSignalMethod() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return lastSignalMethod;
        }
    }

    public static String getLastSignalReason() {
        synchronized (LOCK) {
            reloadIfNeeded();
            return lastSignalReason;
        }
    }

    public static String scopeForMethod(String method) {
        if (method == null) {
            return PlanTargets.FOREST_EXTERNAL;
        }
        String m = method.toLowerCase(Locale.ROOT);
        // doFarmTask 是庄园/森林/果园/新村共用的任务申报接口，单看方法名无法归域；
        // 有运行上下文时 scopeForCurrent 已经取到正确域，这里只做无上下文时的兜底。
        if (m.contains("dofarmtask")) {
            return PlanTargets.FOREST_EXTERNAL;
        }
        if (m.contains("antforest") || m.contains("forest.h5")) {
            if (m.contains("whackmole")) {
                return "forest.game";
            }
            if (m.contains("hunt") || m.contains("chouchoule") || m.contains("draw")) {
                return PlanTargets.FOREST_HUNT;
            }
            if (m.contains("collectenergy")
                    || m.contains("queryenergyranking")
                    || m.contains("wateringbubble")
                    || m.contains("collectprop")
                    || m.contains("collectanimalrobenergy")
                    || m.contains("collectrebornenergy")) {
                return PlanTargets.FOREST_COLLECT;
            }
            return "forest.other";
        }
        if (m.contains("antfarm")) {
            return PlanTargets.FARM;
        }
        if (m.contains("antocean")) {
            return PlanTargets.OCEAN;
        }
        if (m.contains("antmember")) {
            return PlanTargets.MEMBER;
        }
        if (m.contains("sportshealth") || m.contains("antsports")) {
            return PlanTargets.SPORTS;
        }
        if (m.contains("ancient")) {
            return PlanTargets.ANCIENT;
        }
        if (m.contains("antdodo")) {
            return PlanTargets.DODO;
        }
        if (m.contains("antstall")) {
            return PlanTargets.STALL;
        }
        if (m.contains("antorchard")) {
            return PlanTargets.ORCHARD;
        }
        if (m.contains("greenfinance")) {
            return PlanTargets.GREEN;
        }
        if (m.contains("goldenbeans")) {
            return PlanTargets.GOLDEN;
        }
        if (m.contains("answerai")) {
            return PlanTargets.ANSWER_AI;
        }
        return PlanTargets.FOREST_EXTERNAL;
    }

    public static String scopeForModel(String simpleName) {
        if (simpleName == null) {
            return null;
        }
        switch (simpleName) {
            case "AntFarm":
                return PlanTargets.FARM;
            case "AntOcean":
                return PlanTargets.OCEAN;
            case "AntMember":
                return PlanTargets.MEMBER;
            case "AntSports":
                return PlanTargets.SPORTS;
            case "AncientTree":
                return PlanTargets.ANCIENT;
            case "AntDodo":
                return PlanTargets.DODO;
            case "ProtectEcology":
                return PlanTargets.PROTECT;
            case "AntStall":
                return PlanTargets.STALL;
            case "AntOrchard":
                return PlanTargets.ORCHARD;
            case "GreenFinance":
                return PlanTargets.GREEN;
            case "goldenbeans":
                return PlanTargets.GOLDEN;
            case "AnswerAI":
                return PlanTargets.ANSWER_AI;
            default:
                // 森林在 run() 内按 forest.collect / forest.hunt 细分
                return null;
        }
    }

    private static void writeLocked() {
        try {
            JSONObject root = new JSONObject();
            root.put("globalPaused", globalPaused);
            root.put("globalPausedAt", globalPausedAt);
            root.put("globalReason", globalReason);
            root.put("globalPausedMethod", globalPausedMethod);
            root.put("lastSignalAt", lastSignalAt);
            root.put("lastSignalMethod", lastSignalMethod);
            root.put("lastSignalReason", lastSignalReason);
            root.put("verificationRequired", verificationRequired);
            root.put("verificationAt", verificationAt);
            root.put("verificationMethod", verificationMethod);
            root.put("verificationReason", verificationReason);
            root.put("scopes", scopes);
            File file = stateFile();
            if (FileUtil.write2File(root.toString(), file)) {
                loadedMtime = file.lastModified();
            }
        } catch (Throwable t) {
            Log.err(TAG, "写入风控状态失败", t);
        }
    }
}
