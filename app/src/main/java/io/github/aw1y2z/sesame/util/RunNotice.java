package io.github.aw1y2z.sesame.util;

import java.util.LinkedHashSet;
import java.util.Set;

/** 3.0：本轮运行的说明（已完成/需手动/网络异常），由状态页和气泡提示读取。 */
public final class RunNotice {

    private static final Set<String> NOTICES = new LinkedHashSet<>();

    private RunNotice() {
    }

    public static synchronized void clear() {
        NOTICES.clear();
    }

    public static synchronized void add(String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        NOTICES.add(value.trim());
    }

    public static synchronized String join() {
        if (NOTICES.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String notice : NOTICES) {
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append(notice);
        }
        return builder.toString();
    }

    public static synchronized String consume() {
        String value = join();
        NOTICES.clear();
        return value;
    }
}
