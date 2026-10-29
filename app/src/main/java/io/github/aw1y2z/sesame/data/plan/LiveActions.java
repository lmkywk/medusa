package io.github.aw1y2z.sesame.data.plan;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** 当前计划轮次的实时动作数；用于计划超时/停止时仍能给出真实动作数，而不是等模块线程收尾才写回。 */
public final class LiveActions {

    private static final ConcurrentHashMap<String, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    private LiveActions() {
    }

    public static void reset(String scope) {
        if (scope == null || scope.isEmpty()) {
            return;
        }
        COUNTS.computeIfAbsent(scope, k -> new AtomicInteger()).set(0);
    }

    public static void increment(String scope) {
        if (scope == null || scope.isEmpty()) {
            return;
        }
        COUNTS.computeIfAbsent(scope, k -> new AtomicInteger()).incrementAndGet();
    }

    public static int get(String scope) {
        if (scope == null || scope.isEmpty()) {
            return 0;
        }
        AtomicInteger counter = COUNTS.get(scope);
        return counter == null ? 0 : counter.get();
    }
}
