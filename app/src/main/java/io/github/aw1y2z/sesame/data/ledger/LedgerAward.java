package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONObject;

/**
 * 任务奖励文案的统一兜底。
 * <p>服务端对悬赏卡/真实行为任务常回 {@code awardType=NONE}，真实的奖励写在
 * {@code bizInfo.taskDesc}（例如“每笔262g”）。之前各账本直接拼成 {@code NONEx1}，
 * 容易被误读成 1g 奖励。这里统一改为：优先显示任务说明，没有说明则显示“奖励按说明”。
 */
public final class LedgerAward {

    private LedgerAward() {
    }

    /** NONE / 空类型 / 未知类型的兜底文案。 */
    public static String fallback(String desc) {
        String clean = clean(desc);
        return clean.isEmpty() ? "奖励按说明" : clean;
    }

    public static boolean isNone(String type) {
        return type == null || type.trim().isEmpty() || "NONE".equalsIgnoreCase(type.trim());
    }

    /** 从 taskBaseInfo.bizInfo 或 taskDisplayConfig 里取一段人类可读的任务说明。 */
    public static String descOf(JSONObject base, JSONObject displayConfig) {
        String desc = "";
        if (base != null) {
            try {
                JSONObject biz = new JSONObject(base.optString("bizInfo", "{}"));
                desc = firstNonEmpty(
                        biz.optString("taskDesc", ""),
                        biz.optString("desc", ""),
                        biz.optString("awardDesc", ""),
                        biz.optString("subTitle", "")
                );
            } catch (Throwable ignored) {
            }
            if (desc.isEmpty()) {
                desc = firstNonEmpty(base.optString("taskDesc", ""), base.optString("desc", ""));
            }
        }
        if (desc.isEmpty() && displayConfig != null) {
            desc = firstNonEmpty(
                    displayConfig.optString("desc", ""),
                    displayConfig.optString("awardDesc", ""),
                    displayConfig.optString("subTitle", "")
            );
        }
        return clean(desc);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() > 30 ? text.substring(0, 30) + "…" : text;
    }
}
