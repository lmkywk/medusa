package io.github.aw1y2z.sesame.data.ai;

import org.json.JSONObject;

import java.io.File;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.ai.AiClient;
import io.github.aw1y2z.sesame.util.ai.AiSafety;

/**
 * 4.0.0 手动任务说明：没有深链的手动任务，生成一句「去支付宝哪里做什么」。
 * <p>按 域+标题 缓存，不重复调用；每天最多新生成 {@link #MAX_NEW_PER_DAY} 条。
 */
public final class AiManualHints {

    private static final String TAG = "AiManualHints";
    private static final String FILE = "manual_hints.json";
    private static final int MAX_NEW_PER_DAY = 10;

    private static final String SYSTEM_PROMPT =
            "你是「美杜莎」的只读助手。用户在做支付宝里的手动任务，需要一句指路。"
                    + "只输出一个 JSON 对象：{\"hint\":\"一句话，说明去支付宝的哪个页面、做什么\"}。"
                    + "不要输出 JSON 以外的文字，不要给链接，不要建议任何自动化操作。";

    private static final Object LOCK = new Object();
    private static String countDay = "";
    private static int createdToday = 0;

    private AiManualHints() {
    }

    private static File file() {
        return new File(AiDailySummary.dir(), FILE);
    }

    private static JSONObject loadAll() {
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

    /** 取缓存或生成；失败返回空串（调用方显示原文即可）。 */
    public static String hintFor(String domain, String title) {
        if (title == null || title.trim().isEmpty()) {
            return "";
        }
        if (AppConfig.INSTANCE.getAiManualHints() == Boolean.FALSE) {
            return "";
        }
        String key = key(domain, title);
        JSONObject all = loadAll();
        JSONObject cached = all.optJSONObject(key);
        if (cached != null) {
            return cached.optString("hint", "");
        }
        if (!AiClient.ready() || !allowNew()) {
            return "";
        }
        String reply = AiClient.chat(SYSTEM_PROMPT,
                "域：" + safe(domain) + "\n任务名：" + safe(title) + "\n请给一句指路。");
        if (reply == null) {
            return "";
        }
        AiSafety.Validation validation = AiSafety.validate(reply);
        if (!validation.valid) {
            return "";
        }
        String hint = AiSafety.redact(validation.json.optString("hint", ""));
        if (hint.isEmpty()) {
            return "";
        }
        synchronized (LOCK) {
            try {
                JSONObject item = new JSONObject();
                item.put("hint", hint);
                item.put("at", System.currentTimeMillis());
                all.put(key, item);
                if (FileUtil.write2File(all.toString(), file())) {
                    EventLog.system("ai_hint_created", "domain", safe(domain));
                }
            } catch (Throwable t) {
                Log.err(TAG, "写入手动说明失败", t);
            }
        }
        return hint;
    }

    private static boolean allowNew() {
        synchronized (LOCK) {
            String day = io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore.today();
            if (!day.equals(countDay)) {
                countDay = day;
                createdToday = 0;
            }
            if (createdToday >= MAX_NEW_PER_DAY) {
                return false;
            }
            createdToday++;
            return true;
        }
    }

    private static String key(String domain, String title) {
        String raw = safe(domain) + "|" + title.trim();
        return Integer.toHexString(raw.hashCode()) + "_" + title.trim().length();
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^0-9A-Za-z_.\\-\\u4e00-\\u9fa5]", "_");
    }
}