package io.github.aw1y2z.sesame.data.task;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.Status;

/**
 * 类级冷却闸门：自动计划遇到今天已失败的端点时，只跳过该类任务，不再重复撞同一接口。
 *
 * <p>默认关闭（AppConfig.capabilityClassCooldown=false），关闭时完全回到整域原行为。
 * 手动执行永远放行——手动直达以用户选择为准。账号级/域级 1009 闸门不受本类影响。
 */
public final class CapabilityGate {

    private CapabilityGate() {
    }

    public static boolean enabled() {
        try {
            return Boolean.TRUE.equals(AppConfig.INSTANCE.getCapabilityClassCooldown());
        } catch (Throwable t) {
            return false;
        }
    }

    /** 自动执行时，该端点今天是否应跳过。 */
    public static boolean blocked(String endpoint) {
        if (!enabled() || endpoint == null || endpoint.isEmpty()) {
            return false;
        }
        if (!EndpointStateStore.isFailingTaskEndpoint(endpoint)) {
            return false;
        }
        String key = "capgate::" + endpoint;
        if (!Status.hasFlagToday(key)) {
            Status.flagToday(key);
            Log.record("按类跳过⏸端点今天已失败：" + endpoint + "（手动执行不受限）");
        }
        return true;
    }
}
