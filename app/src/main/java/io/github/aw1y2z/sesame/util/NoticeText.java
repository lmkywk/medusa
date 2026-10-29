package io.github.aw1y2z.sesame.util;

/** 通知/气泡展示文本：去掉 emoji 与装饰符号，统一空白；日志原文不受影响。 */
public final class NoticeText {

    private NoticeText() {
    }

    public static String clean(CharSequence source) {
        if (source == null) {
            return "";
        }
        String text = source.toString()
                .replace("\uFE0F", "")
                .replace("\u200D", "")
                .replace('\u00A0', ' ');
        // So=各类符号/emoji，Cs=代理项，Cn=未分配；中文与常规标点不在其中。
        text = text.replaceAll("[\\p{So}\\p{Cs}\\p{Cn}]", "");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }

    public static String oneLine(CharSequence source, int maxChars) {
        String text = clean(source);
        if (maxChars > 0 && text.length() > maxChars) {
            return text.substring(0, maxChars) + "…";
        }
        return text;
    }
}
