package io.github.aw1y2z.sesame.util.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4.0.0 AI 只读层的两道边界：
 * <ol>
 *   <li>{@link #redact(String)}：出站内容再过一遍脱敏，长数字串与 URL 一律抹掉；</li>
 *   <li>{@link #validate(String)}：回来的必须是 JSON 对象，字段只许是叙述性内容；
 *       出现可执行意图（执行/点击/改计划/改队列）一律判为非法，绝不落到执行路径。</li>
 * </ol>
 */
public final class AiSafety {

    /** 16 位以上连续数字：uid / 订单号 / 手机号一类，不出去。 */
    private static final Pattern LONG_DIGITS = Pattern.compile("\\d{11,}");
    private static final Pattern URL = Pattern.compile("https?://[^\\s\"']+");

    /** 可执行意图关键词：模型一旦给这类建议，本层直接拒绝，不给上层展示成“可执行”。 */
    private static final String[] EXECUTABLE_WORDS = {
            "执行", "点击", "自动运行", "自动执行", "改计划", "修改计划", "改队列", "修改队列",
            "execute", "click", "auto_run", "autorun", "run_plan", "modify_plan", "enqueue"
    };

    /**
     * 否定句里的“执行/点击”不构成执行意图，例如“不执行领取”“不自动执行”“未执行”。
     * 只抹掉紧跟在否定词后的动作词，正向的“请执行/自动执行”仍会命中关键词。
     */
    private static final Pattern NEGATED_EXECUTION = Pattern.compile(
            "(不|未|没|没有|无需|禁止|不可|不能|无法|不要|不得|不再|不建议|不需要|不必|不应|不应该|不会|不打算|不准备)"
                    + "(自动)?(执行|点击|运行|修改计划|改计划|修改队列|改队列)");

    private AiSafety() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = LONG_DIGITS.matcher(text).replaceAll("<num>");
        Matcher matcher = URL.matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group();
            String host = AiClient.hostOf(url);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(host));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static final class Validation {
        public final boolean valid;
        public final JSONObject json;
        public final String reason;

        Validation(boolean valid, JSONObject json, String reason) {
            this.valid = valid;
            this.json = json;
            this.reason = reason;
        }
    }

    /** 校验模型回复：必须是 JSON 对象、无执行意图、无长数字。 */
    public static Validation validate(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new Validation(false, null, "空回复");
        }
        String trimmed = stripFence(content.trim());
        JSONObject json;
        try {
            json = new JSONObject(trimmed);
        } catch (Throwable t) {
            return new Validation(false, null, "不是 JSON");
        }
        if (containsExecutableIntent(json)) {
            return new Validation(false, json, "含可执行意图");
        }
        if (LONG_DIGITS.matcher(json.toString()).find()) {
            return new Validation(false, json, "含长数字");
        }
        return new Validation(true, json, "");
    }

    private static boolean containsExecutableIntent(JSONObject json) {
        String text = NEGATED_EXECUTION.matcher(json.toString()).replaceAll("");
        for (String word : EXECUTABLE_WORDS) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 模型常把 JSON 包在 ```json 里，剥掉围栏再校验。 */
    private static String stripFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        if (firstLineEnd < 0) {
            return text;
        }
        int lastFence = text.lastIndexOf("```");
        if (lastFence <= firstLineEnd) {
            return text;
        }
        return text.substring(firstLineEnd + 1, lastFence).trim();
    }

    /** 把任一 JSON 值压成一行可读文本（展示兜底用）。 */
    public static String flatten(JSONObject json) {
        if (json == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = json.opt(key);
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(key).append(": ").append(stringify(value));
        }
        return builder.toString();
    }

    private static String stringify(Object value) {
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) {
                    builder.append("；");
                }
                builder.append(stringify(array.opt(i)));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }
}