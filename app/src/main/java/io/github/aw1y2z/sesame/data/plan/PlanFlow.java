package io.github.aw1y2z.sesame.data.plan;

import java.util.Locale;
import java.util.Random;

import io.github.aw1y2z.sesame.data.AppConfig;

/**
 * 计划流阈值：计划运行期间给相邻写请求加最小间隔，并在阈值上叠加随机抖动。
 * 只作用于计划线程发起的写请求；读请求、探针和模块外的手动操作不受影响。
 */
public final class PlanFlow {

    private static final Object LOCK = new Object();
    private static final Random RANDOM = new Random();

    private static final String[] READ_HINTS = {
            "query", "list", "get", "homepage", "index", "status", "ranking",
            "detail", "probe", "enter", "preview"
    };
    private static final String[] WRITE_HINTS = {
            "finish", "receive", "dofarmtask", "cook", "collect", "clean",
            "draw", "feed", "water", "sign", "exchange", "donate", "plant",
            "spread", "harvest", "recall", "send", "help", "rob", "fill",
            "touch", "scratch", "apply", "claim", "visit", "buy", "sell",
            "upgrade", "open", "unlock", "pick"
    };

    private static volatile boolean active;
    private static long lastWriteAt;

    private PlanFlow() {
    }

    public static void begin() {
        synchronized (LOCK) {
            active = true;
            lastWriteAt = 0L;
        }
    }

    public static void end() {
        synchronized (LOCK) {
            active = false;
            lastWriteAt = 0L;
        }
    }

    /** 在真正发出请求前调用；非计划期间或读请求直接放行。 */
    public static void beforeRequest(String method) {
        if (!active || !isWrite(method)) {
            return;
        }
        Integer configured = AppConfig.INSTANCE.getPlanFlowMs();
        int base = configured == null ? AppConfig.DEFAULT_PLAN_FLOW_MS : configured;
        if (base <= 0) {
            return;
        }
        base = Math.max(500, Math.min(15000, base));
        synchronized (LOCK) {
            if (!active) {
                return;
            }
            Integer configuredJitter = AppConfig.INSTANCE.getPlanFlowJitterPercent();
            int jitterPercent = configuredJitter == null
                    ? AppConfig.DEFAULT_PLAN_FLOW_JITTER : configuredJitter;
            jitterPercent = Math.max(0, Math.min(50, jitterPercent));
            long interval = base + (long) (base * (jitterPercent / 100.0) * RANDOM.nextDouble());
            long now = System.currentTimeMillis();
            long wait = lastWriteAt + interval - now;
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            lastWriteAt = System.currentTimeMillis();
        }
    }

    private static boolean isWrite(String method) {
        if (method == null || method.trim().isEmpty()) {
            return false;
        }
        String name = method.toLowerCase(Locale.US);
        for (String hint : READ_HINTS) {
            if (name.contains(hint)) {
                return false;
            }
        }
        for (String hint : WRITE_HINTS) {
            if (name.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
