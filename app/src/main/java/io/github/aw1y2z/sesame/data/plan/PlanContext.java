package io.github.aw1y2z.sesame.data.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.aw1y2z.sesame.data.task.CapabilityGate;
import io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry;
import io.github.aw1y2z.sesame.data.task.TaskFilter;

/** 当前线程正在执行的计划目标；森林用它区分收能量和寻宝，并回报阶段结果。 */
public final class PlanContext {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_SKIPPED = "SKIPPED";
    public static final String RESULT_FAILED = "FAILED";

    private static final ThreadLocal<String> TARGET = new ThreadLocal<>();
    private static final ThreadLocal<String> RESULT = new ThreadLocal<>();
    private static final ThreadLocal<String> DETAIL = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORCE_COLLECT = new ThreadLocal<>();
    /** 本轮是否由用户手动发起：手动直达按旧版语义执行，自动计划仍走保守策略。 */
    private static final ThreadLocal<Boolean> MANUAL = new ThreadLocal<>();
    /** 按类型执行：非空时只放行指定机制/成本/域/类键的任务。 */
    private static final ThreadLocal<TaskFilter> FILTER = new ThreadLocal<>();
    /** 本轮是否因计划墙钟超时而停止；模块线程收尾时据此避免把中止写成“本轮完成”。 */
    private static volatile boolean timedOut = false;
    /**
     * 本轮按类跳过的计数（类型·游戏 5 / 段·资源支出/water 1），用于运行结束汇总一行。
     * 不能用 ThreadLocal：异步模型在模型线程判断，汇总在计划线程读取。
     */
    private static final Map<String, Integer> SKIPPED = new java.util.concurrent.ConcurrentHashMap<>();
    /** 每类最多留 5 个被跳过的任务名，供次日核对“是不是误挡”。 */
    private static final Map<String, java.util.Set<String>> SKIP_SAMPLES =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_SKIP_SAMPLES = 5;

    private PlanContext() {
    }

    public static void set(String target) {
        if (target == null) {
            TARGET.remove();
        } else {
            TARGET.set(target);
        }
        RESULT.remove();
        DETAIL.remove();
    }

    public static String get() {
        return TARGET.get();
    }

    public static void success() {
        RESULT.set(RESULT_SUCCESS);
        DETAIL.remove();
    }

    public static void skipped(String detail) {
        RESULT.set(RESULT_SKIPPED);
        DETAIL.set(detail == null ? "" : detail);
    }

    public static void failure(String detail) {
        RESULT.set(RESULT_FAILED);
        DETAIL.set(detail == null ? "" : detail);
    }

    public static String getResult() {
        return RESULT.get();
    }

    /** 首页“手动收能量”：即使自动收能量开关关闭，也收一轮。 */
    public static void setForceCollect(boolean force) {
        if (force) {
            FORCE_COLLECT.set(Boolean.TRUE);
        } else {
            FORCE_COLLECT.remove();
        }
    }

    public static boolean isForceCollect() {
        return Boolean.TRUE.equals(FORCE_COLLECT.get());
    }

    public static void setManual(boolean manual) {
        if (manual) {
            MANUAL.set(Boolean.TRUE);
        } else {
            MANUAL.remove();
        }
    }

    public static boolean isManual() {
        return Boolean.TRUE.equals(MANUAL.get());
    }

    public static void setTimedOut(boolean value) {
        timedOut = value;
    }

    public static boolean isTimedOut() {
        return timedOut;
    }

    public static void setFilter(TaskFilter filter) {
        if (filter == null || filter.isEmpty()) {
            FILTER.remove();
        } else {
            FILTER.set(filter);
        }
    }

    public static TaskFilter getFilter() {
        return FILTER.get();
    }

    /**
     * 各域任务循环的统一放行判断：先过按类过滤器，再过类级冷却（仅自动）。
     * 两者都为空/关闭时一律放行，旧行为不变。
     */
    public static boolean allowsTask(String domain, String id, String title, int require, int progress) {
        return allowsTask(domain, id, title, require, progress, "");
    }

    /** 带场景码的版本：sceneCode 会进入 classKey，避免不同玩法共用一个结论。 */
    public static boolean allowsTask(String domain, String id, String title, int require, int progress,
                                     String sceneCode) {
        TaskFilter filter = FILTER.get();
        TaskCapabilityRegistry.Spec spec = null;
        if (filter != null) {
            spec = TaskCapabilityRegistry.resolve(domain, id, title, "", require, progress, sceneCode);
            if (!filter.allows(spec)) {
                String reason = "按类跳过:" + TaskCapabilityRegistry.mechanismLabel(spec.mechanism);
                countSkip("类型·" + TaskCapabilityRegistry.mechanismLabel(spec.mechanism), title);
                io.github.aw1y2z.sesame.data.task.TaskRunEvent.skip(domain, id, title, reason);
                return false;
            }
        }
        if (!isManual() && CapabilityGate.enabled()) {
            if (spec == null) {
                spec = TaskCapabilityRegistry.resolve(domain, id, title, "", require, progress, sceneCode);
            }
            if (CapabilityGate.blocked(spec.endpoint)) {
                countSkip("端点·" + spec.endpoint, title);
                io.github.aw1y2z.sesame.data.task.TaskRunEvent.skip(domain, id, title,
                        "端点冷却:" + spec.endpoint);
                return false;
            }
        }
        return true;
    }

    /**
     * 段级放行：收能量/浇水/巡护/清理这类“不是任务”的动作也要受类型过滤约束。
     * 没有过滤器时一律放行。
     */
    public static boolean allowsSection(String domain, String section,
                                        TaskCapabilityRegistry.Mechanism mechanism,
                                        TaskCapabilityRegistry.CostType costType) {
        TaskFilter filter = FILTER.get();
        if (filter == null) {
            return true;
        }
        TaskCapabilityRegistry.Spec spec = TaskCapabilityRegistry.specOf(mechanism, costType, section, domain);
        if (!filter.allows(spec)) {
            countSkip("段·" + TaskCapabilityRegistry.mechanismLabel(mechanism) + "/" + section, section);
            return false;
        }
        return true;
    }

    private static void countSkip(String key, String sample) {
        if (key == null || key.isEmpty()) {
            return;
        }
        SKIPPED.merge(key, 1, Integer::sum);
        if (sample == null || sample.trim().isEmpty()) {
            return;
        }
        java.util.Set<String> set = SKIP_SAMPLES.computeIfAbsent(key,
                k -> java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>()));
        if (set.size() < MAX_SKIP_SAMPLES) {
            set.add(sample.trim());
        }
    }

    /** 取走本轮的跳过样例；调用后清空。 */
    public static Map<String, java.util.List<String>> takeSkipSamples() {
        Map<String, java.util.List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.Set<String>> entry : SKIP_SAMPLES.entrySet()) {
            copy.put(entry.getKey(), new java.util.ArrayList<>(entry.getValue()));
        }
        SKIP_SAMPLES.clear();
        return copy;
    }

    /** 取走本轮跳过计数；调用后清空，避免跨轮累计。 */
    public static Map<String, Integer> takeSkipCounts() {
        Map<String, Integer> copy = new LinkedHashMap<>(SKIPPED);
        SKIPPED.clear();
        return copy.isEmpty() ? Collections.emptyMap() : copy;
    }

    public static String getDetail() {
        return DETAIL.get();
    }

    public static void clear() {
        TARGET.remove();
        RESULT.remove();
        DETAIL.remove();
        FORCE_COLLECT.remove();
        MANUAL.remove();
        FILTER.remove();
        // SKIPPED 不在这里清：模型线程结束时清掉会让“按类跳过”汇总读不到，
        // 由 PlanExecutor 在轮次结束 take 走。
    }
}
