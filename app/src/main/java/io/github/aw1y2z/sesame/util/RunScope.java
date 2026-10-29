package io.github.aw1y2z.sesame.util;

import io.github.aw1y2z.sesame.data.plan.PlanContext;

/** 3.0：当前执行的页面/目标；风控只暂停命中的目标，不再一刀切暂停整个账号。 */
public final class RunScope {

    private static final ThreadLocal<String> SCOPE = new ThreadLocal<>();
    private static volatile String currentTarget = "";

    private RunScope() {
    }

    public static void bind(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            SCOPE.remove();
        } else {
            SCOPE.set(scope);
        }
    }

    /** 只返回本线程显式绑定的 scope，不回退到全局 currentTarget；失败归因必须用它。 */
    public static String bound() {
        String value = SCOPE.get();
        return value == null ? "" : value;
    }

    public static String current() {
        String value = SCOPE.get();
        if (value != null && !value.isEmpty()) {
            return value;
        }
        String planTarget = PlanContext.get();
        if (planTarget != null && !planTarget.isEmpty()) {
            return planTarget;
        }
        return currentTarget;
    }

    public static void setTarget(String target) {
        currentTarget = target == null ? "" : target;
    }

    public static String target() {
        return currentTarget;
    }

    public static void clearTarget() {
        currentTarget = "";
    }

    public static void clear() {
        SCOPE.remove();
    }
}
