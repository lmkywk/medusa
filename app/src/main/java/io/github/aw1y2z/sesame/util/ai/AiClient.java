package io.github.aw1y2z.sesame.util.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.Log;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 4.0.0 AI 只读层的传输层：OpenAI 兼容的 /chat/completions 调用。
 * <p>约束（按约定实现）：
 * <ul>
 *   <li>根地址 / 模型名 / Bearer 令牌三项任一为空就不调用；</li>
 *   <li>每次只发一条 user prompt；stream=false；读超时默认 180 秒；失败重试 2 次；</li>
 *   <li>令牌只在请求头里，日志打码；响应内容不整段落日志，只记长度与耗时；</li>
 *   <li>限流：两次调用最小间隔 20 秒，每天最多 120 次，超出直接放弃（防止跑飞烧钱）。</li>
 * </ul>
 */
public final class AiClient {

    private static final String TAG = "AiClient";
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final long MIN_INTERVAL_MS = 20_000L;
    private static final int MAX_CALLS_PER_DAY = 120;

    private static final Object LOCK = new Object();
    private static long lastCallAt = 0L;
    private static String callDay = "";
    private static int callsToday = 0;
    private static OkHttpClient client;

    private AiClient() {
    }

    /** 配置是否齐全（三项不齐就不调用）。 */
    public static boolean ready() {
        AppConfig config = AppConfig.INSTANCE;
        if (config.getAiSummaryEnabled() == Boolean.FALSE) {
            return false;
        }
        return notEmpty(config.getAiBaseUrl()) && notEmpty(config.getAiModel()) && notEmpty(config.getAiToken());
    }

    public static String describeReadiness() {
        AppConfig config = AppConfig.INSTANCE;
        if (config.getAiSummaryEnabled() == Boolean.FALSE) {
            return "未开启";
        }
        int missing = 0;
        if (!notEmpty(config.getAiBaseUrl())) {
            missing++;
        }
        if (!notEmpty(config.getAiModel())) {
            missing++;
        }
        if (!notEmpty(config.getAiToken())) {
            missing++;
        }
        return missing == 0 ? "已就绪" : "缺少 " + missing + " 项配置";
    }

    /** 令牌打码：只留前 3 后 2 位。 */
    public static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "";
        }
        if (token.length() <= 6) {
            return "***";
        }
        return token.substring(0, 3) + "***" + token.substring(token.length() - 2);
    }

    public static String hostOf(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "<invalid-url>";
        }
        try {
            String value = baseUrl.trim();
            int scheme = value.indexOf("://");
            String rest = scheme >= 0 ? value.substring(scheme + 3) : value;
            int slash = rest.indexOf('/');
            String host = slash >= 0 ? rest.substring(0, slash) : rest;
            return host.isEmpty() ? "<invalid-url>" : host;
        } catch (Throwable t) {
            return "<invalid-url>";
        }
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * @return 模型回复的正文；失败返回 null（调用方必须有降级路径，不能因为 AI 失败影响执行）。
     */
    public static String chat(String systemPrompt, String userPrompt) {
        return chat(systemPrompt, userPrompt, true, 0);
    }

    /**
     * @param recordUsage      是否计入本地用量（连通性测试不计）
     * @param maxTokensOverride 临时覆盖回复上限；0=用配置
     */
    public static String chat(String systemPrompt, String userPrompt,
                              boolean recordUsage, int maxTokensOverride) {
        ChatResult result = chatDetailed(systemPrompt, userPrompt, recordUsage, maxTokensOverride);
        lastError = result.error == null ? "" : result.error;
        return result.error == null ? result.content : null;
    }

    /** 最近一次失败原因（给设置页/摘要页展示，不含内容）。 */
    private static volatile String lastError = "";

    public static String lastError() {
        return lastError == null ? "" : lastError;
    }

    public static final class ChatResult {
        public final String content;
        public final String finishReason;
        public final long totalTokens;
        public final long reasoningChars;
        public final String error;
        public final long ms;

        ChatResult(String content, String finishReason, long totalTokens, long reasoningChars,
                   String error, long ms) {
            this.content = content;
            this.finishReason = finishReason;
            this.totalTokens = totalTokens;
            this.reasoningChars = reasoningChars;
            this.error = error;
            this.ms = ms;
        }
    }

    /**
     * 带诊断信息的调用。
     * <p>推理模型（如 deepseek-flash）的思考过程也计入 max_tokens：上限太小时会出现
     * finish_reason=length 且正文为空。这里检测到这种情况会自动用两倍上限重试一次（最多 8192），
     * 并把真实 token 数记进本地用量。
     */
    public static ChatResult chatDetailed(String systemPrompt, String userPrompt,
                                          boolean recordUsage, int maxTokensOverride) {
        if (!ready()) {
            return new ChatResult(null, "", 0L, 0L, "配置不齐：" + describeReadiness(), 0L);
        }
        if (!allowCall()) {
            return new ChatResult(null, "", 0L, 0L, "限流中，请稍后再试", 0L);
        }
        AppConfig config = AppConfig.INSTANCE;
        int timeout = config.getAiTimeoutSeconds() == null ? 180 : config.getAiTimeoutSeconds();
        int configuredMax = maxTokensOverride > 0
                ? maxTokensOverride
                : (config.getAiMaxTokens() == null ? 2048 : config.getAiMaxTokens());
        int maxTokens = configuredMax;
        boolean escalated = false;

        int retries = config.getAiRetries() == null ? 2 : config.getAiRetries();
        int attempts = Math.max(1, retries + 1);
        String url = endpoint(config.getAiBaseUrl());
        String error = "调用失败";

        for (int attempt = 1; attempt <= attempts; attempt++) {
            String body = buildBody(config, systemPrompt, userPrompt, maxTokens);
            if (body == null) {
                return new ChatResult(null, "", 0L, 0L, "请求体构造失败", 0L);
            }
            long started = System.currentTimeMillis();
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + config.getAiToken().trim())
                        .header("Content-Type", "application/json")
                        .post(RequestBody.create(body, JSON_TYPE))
                        .build();
                try (Response response = client(timeout).newCall(request).execute()) {
                    String text = response.body() == null ? "" : response.body().string();
                    long elapsed = System.currentTimeMillis() - started;
                    if (!response.isSuccessful()) {
                        error = "HTTP " + response.code() + "（检查根地址是否需要 /v1、模型名、令牌）";
                        Log.i(TAG, "AI 调用失败 http=" + response.code() + " 第" + attempt + "次 "
                                + elapsed + "ms host=" + hostOf(url));
                        if (response.code() < 500 || attempt == attempts) {
                            EventLog.system("ai_call_failed", "http", response.code(), "attempt", attempt);
                            return new ChatResult(null, "", 0L, 0L, error, elapsed);
                        }
                    } else {
                        JSONObject parsed = safeObject(text);
                        String content = extractContent(parsed);
                        String finishReason = extractFinishReason(parsed);
                        long totalTokens = extractTotalTokens(parsed);
                        long reasoningChars = extractReasoningChars(parsed);
                        int contentLength = content == null ? 0 : content.length();
                        Log.i(TAG, "AI 调用完成 host=" + hostOf(url) + " model=" + config.getAiModel()
                                + " 回复" + contentLength + "字 finish=" + finishReason
                                + " tokens=" + totalTokens + " 第" + attempt + "次 " + elapsed + "ms");
                        EventLog.system("ai_call_ok", "chars", contentLength, "ms", elapsed,
                                "finish", finishReason, "tokens", totalTokens);

                        boolean empty = content == null || content.trim().isEmpty();
                        if (empty && "length".equalsIgnoreCase(finishReason)
                                && !escalated && maxTokens < 8192) {
                            // 推理占满了额度：加倍再试一次，避免把“模型思考太长”误报成“调用失败”
                            escalated = true;
                            maxTokens = Math.min(8192, maxTokens * 2);
                            Log.i(TAG, "正文为空且 finish=length，上限提到 " + maxTokens + " 重试");
                            error = "模型思考占满了 max_tokens（已用 " + maxTokens + " 重试仍为空）";
                            sleep(1200L);
                            continue;
                        }
                        if (recordUsage) {
                            String input = (systemPrompt == null ? "" : systemPrompt) + userPrompt;
                            if (totalTokens > 0L) {
                                io.github.aw1y2z.sesame.data.ai.AiUsage.recordWithTokens(
                                        input, content, totalTokens);
                            } else {
                                io.github.aw1y2z.sesame.data.ai.AiUsage.record(input, content);
                            }
                        }
                        if (empty) {
                            error = "length".equalsIgnoreCase(finishReason)
                                    ? "模型思考占满了 max_tokens，正文为空；把上限调到 2048+ 再试"
                                    : "模型返回空内容（finish=" + finishReason + "）";
                            return new ChatResult(null, finishReason, totalTokens, reasoningChars,
                                    error, elapsed);
                        }
                        return new ChatResult(content, finishReason, totalTokens, reasoningChars,
                                null, elapsed);
                    }
                }
            } catch (Throwable t) {
                error = "网络异常：" + t.getClass().getSimpleName();
                Log.i(TAG, "AI 调用异常 第" + attempt + "次：" + t.getClass().getSimpleName());
                if (attempt == attempts) {
                    EventLog.system("ai_call_error", "type", t.getClass().getSimpleName());
                    return new ChatResult(null, "", 0L, 0L, error, 0L);
                }
            }
            sleep(1500L * attempt);
        }
        return new ChatResult(null, "", 0L, 0L, error, 0L);
    }

    private static JSONObject safeObject(String text) {
        try {
            return new JSONObject(text);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String extractFinishReason(JSONObject jo) {
        try {
            JSONArray choices = jo == null ? null : jo.optJSONArray("choices");
            JSONObject first = choices == null ? null : choices.optJSONObject(0);
            return first == null ? "" : first.optString("finish_reason", "");
        } catch (Throwable t) {
            return "";
        }
    }

    private static long extractTotalTokens(JSONObject jo) {
        try {
            JSONObject usage = jo == null ? null : jo.optJSONObject("usage");
            if (usage == null) {
                return 0L;
            }
            long total = usage.optLong("total_tokens", 0L);
            if (total > 0L) {
                return total;
            }
            return usage.optLong("prompt_tokens", 0L) + usage.optLong("completion_tokens", 0L);
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static long extractReasoningChars(JSONObject jo) {
        try {
            JSONArray choices = jo == null ? null : jo.optJSONArray("choices");
            JSONObject first = choices == null ? null : choices.optJSONObject(0);
            JSONObject message = first == null ? null : first.optJSONObject("message");
            if (message == null) {
                return 0L;
            }
            String reasoning = message.optString("reasoning_content", "");
            return reasoning == null ? 0L : reasoning.length();
        } catch (Throwable t) {
            return 0L;
        }
    }

    private static String endpoint(String baseUrl) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        return base + "/chat/completions";
    }

    private static String buildBody(AppConfig config, String systemPrompt, String userPrompt,
                                    int maxTokensOverride) {
        try {
            JSONObject root = new JSONObject();
            root.put("model", config.getAiModel().trim());
            root.put("stream", false);
            root.put("temperature", 0);
            int maxTokens = maxTokensOverride > 0
                    ? maxTokensOverride
                    : (config.getAiMaxTokens() == null ? 2048 : config.getAiMaxTokens());
            root.put("max_tokens", maxTokens);
            JSONArray messages = new JSONArray();
            if (notEmpty(systemPrompt)) {
                JSONObject system = new JSONObject();
                system.put("role", "system");
                system.put("content", systemPrompt);
                messages.put(system);
            }
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", userPrompt);
            messages.put(user);
            root.put("messages", messages);
            return root.toString();
        } catch (Throwable t) {
            Log.err(TAG, "构造 AI 请求体失败", t);
            return null;
        }
    }

    private static String extractContent(JSONObject jo) {
        try {
            JSONArray choices = jo == null ? null : jo.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            JSONObject first = choices.optJSONObject(0);
            if (first == null) {
                return null;
            }
            JSONObject message = first.optJSONObject("message");
            if (message != null) {
                return message.optString("content", "");
            }
            return first.optString("text", "");
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean allowCall() {
        synchronized (LOCK) {
            long now = System.currentTimeMillis();
            String day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(new java.util.Date(now));
            if (!day.equals(callDay)) {
                callDay = day;
                callsToday = 0;
            }
            if (callsToday >= MAX_CALLS_PER_DAY) {
                return false;
            }
            if (now - lastCallAt < MIN_INTERVAL_MS) {
                return false;
            }
            lastCallAt = now;
            callsToday++;
            return true;
        }
    }

    private static OkHttpClient client(int timeoutSeconds) {
        synchronized (LOCK) {
            if (client == null) {
                client = new OkHttpClient.Builder()
                        .connectTimeout(20, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(180, TimeUnit.SECONDS)
                        .build();
            }
            return client.newBuilder()
                    .readTimeout(Math.max(10, timeoutSeconds), TimeUnit.SECONDS)
                    .build();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /** 测试用：重置限流状态。 */
    public static void resetLimiterForTest() {
        synchronized (LOCK) {
            lastCallAt = 0L;
            callDay = "";
            callsToday = 0;
            client = null;
        }
    }

    public static final class TestResult {
        public final boolean ok;
        public final String detail;
        public final long ms;

        TestResult(boolean ok, String detail, long ms) {
            this.ok = ok;
            this.detail = detail;
            this.ms = ms;
        }
    }

    /**
     * 连通性测试：只发一句“回复 OK”，回复上限 16 tokens，不计入本地用量。
     * <p>总开关没开也能测——用户需要先确认配置写对了，再决定开不开。
     */
    public static TestResult testConnection() {
        AppConfig config = AppConfig.INSTANCE;
        boolean complete = notEmpty(config.getAiBaseUrl()) && notEmpty(config.getAiModel())
                && notEmpty(config.getAiToken());
        if (!complete) {
            return new TestResult(false, "三项配置不齐（根地址 / 模型名 / 令牌）", 0L);
        }
        long wait = secondsUntilAllowed();
        if (wait > 0) {
            return new TestResult(false, "限流中，请 " + wait + " 秒后再试", 0L);
        }
        long start = System.currentTimeMillis();
        String reply = chat("你是连通性测试助手。", "只回复 OK", false, 16);
        long ms = System.currentTimeMillis() - start;
        if (reply == null) {
            return new TestResult(false, "调用失败：检查根地址是否需要 /v1、模型名、令牌、网络", ms);
        }
        String text = reply.trim();
        if (text.length() > 40) {
            text = text.substring(0, 40) + "…";
        }
        return new TestResult(true, "连接正常 " + ms + "ms，回复：" + text, ms);
    }

    /** 距离下一次允许调用还有几秒（0=可以调用）。 */
    public static long secondsUntilAllowed() {
        synchronized (LOCK) {
            long wait = MIN_INTERVAL_MS - (System.currentTimeMillis() - lastCallAt);
            return wait <= 0L ? 0L : (wait + 999L) / 1000L;
        }
    }
}