package io.github.aw1y2z.sesame.replay;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 4.0.0 回放语料脱敏：真实抓包进语料库前必须过这一道。
 * <p>只删/改敏感字段与 URL 里的查询参数，保留结构与状态码，保证回放可比对。
 */
public final class Desensitizer {

    /** 账号/好友/令牌/设备类字段：值一律替换为 &lt;redacted&gt;。 */
    private static final Set<String> SENSITIVE_KEYS = new HashSet<>(Arrays.asList(
            "userId", "uid", "alipayUserId", "friendId", "friendUserId", "loginId", "logonId",
            "token", "sessionId", "sId", "authToken", "mobile", "phone", "mobilePhone",
            "idCard", "realName", "certNo", "deviceId", "utdid", "tid", "imei", "imsi",
            "nickName", "userName", "avatar", "headImageUrl", "email"));

    private static final Pattern QUERY_VALUE = Pattern.compile("([?&][A-Za-z0-9_]+=)[^&\"'\\s]*");
    private static final Pattern URL_LIKE = Pattern.compile("https?://[^\"'\\s]+");

    private Desensitizer() {
    }

    public static String sanitize(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            JSONObject jo = new JSONObject(json);
            scrub(jo);
            return jo.toString();
        } catch (Throwable ignored) {
            return sanitizeText(json);
        }
    }

    private static void scrub(JSONObject jo) {
        try {
            Iterator<String> keys = jo.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jo.opt(key);
                if (SENSITIVE_KEYS.contains(key)) {
                    jo.put(key, "<redacted>");
                } else if (value instanceof String) {
                    jo.put(key, sanitizeText((String) value));
                } else if (value instanceof JSONObject) {
                    scrub((JSONObject) value);
                } else if (value instanceof org.json.JSONArray) {
                    scrubArray((org.json.JSONArray) value);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void scrubArray(org.json.JSONArray array) {
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof JSONObject) {
                scrub((JSONObject) value);
            } else if (value instanceof org.json.JSONArray) {
                scrubArray((org.json.JSONArray) value);
            } else if (value instanceof String) {
                try {
                    array.put(i, sanitizeText((String) value));
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 把 URL 的查询参数值抹掉，只留参数名。 */
    public static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = QUERY_VALUE.matcher(text).replaceAll("$1<redacted>");
        Matcher matcher = URL_LIKE.matcher(result);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group();
            String cleaned = url.replaceAll("^(https?://[^/?#]+)(.*)$", "$1<path>");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(cleaned));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}