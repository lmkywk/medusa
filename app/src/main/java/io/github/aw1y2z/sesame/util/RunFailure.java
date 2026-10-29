package io.github.aw1y2z.sesame.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本轮接口失败原因。按执行线程绑定的 scope 分桶，避免并发/模块重载时
 * 一个模块的“接口无响应”被另一个计划目标消费。
 */
public final class RunFailure {

    private static final String DEFAULT_KEY = "default";
    private static final Map<String, String> REASONS = new ConcurrentHashMap<>();

    private RunFailure() {
    }

    private static String key() {
        String scope = RunScope.bound();
        return scope == null || scope.trim().isEmpty() ? DEFAULT_KEY : scope.trim();
    }

    public static void clear() {
        REASONS.clear();
    }

    public static void clear(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            REASONS.remove(DEFAULT_KEY);
        } else {
            REASONS.remove(scope.trim());
        }
    }

    public static void set(String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        REASONS.putIfAbsent(key(), value);
    }

    public static String consume() {
        return consume(key());
    }

    public static String consume(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return "";
        }
        String value = REASONS.remove(scope.trim());
        return value == null ? "" : value;
    }

    /** 先按目标、再按所属域消费；plan 目标与线程 scope 不完全同名时也能取到。 */
    public static String consumeForTarget(String target) {
        String value = consume(target);
        if (!value.isEmpty()) {
            return value;
        }
        String scope = RiskGuard.scopeForTarget(target);
        if (scope != null && !scope.equals(target)) {
            value = consume(scope);
            if (!value.isEmpty()) {
                return value;
            }
        }
        if (target != null && target.startsWith("forest.")) {
            return consume(RiskGuard.SCOPE_FOREST);
        }
        return "";
    }

    public static String peek() {
        return peek(key());
    }

    public static String peek(String scope) {
        if (scope == null || scope.trim().isEmpty()) {
            return "";
        }
        String value = REASONS.get(scope.trim());
        return value == null ? "" : value;
    }
}
