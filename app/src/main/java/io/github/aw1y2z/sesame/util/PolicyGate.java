package io.github.aw1y2z.sesame.util;

/** 3.0：所有 RPC 的写闸门。只在用户停止或账号级风控暂停时拦截，避免误伤正常模块。 */
public final class PolicyGate {

    private static final long LOG_INTERVAL_MS = 5_000L;
    private static volatile long lastLogAt = 0L;

    private PolicyGate() {
    }

    public static boolean isBlocked() {
        if (RunControl.isStopRequested()) {
            RunFailure.set("用户停止当前运行");
            logBlocked("用户已停止当前运行");
            return true;
        }
        String scope = RunScope.current();
        if (scope != null && !scope.isEmpty() && RiskGuard.isPaused(scope)) {
            RunFailure.set("风控暂停：" + RiskGuard.labelOf(scope));
            logBlocked("目标风控暂停：" + RiskGuard.labelOf(scope));
            return true;
        }
        if (RiskGuard.isGlobalPaused()) {
            RunFailure.set("账号级风控暂停");
            logBlocked("账号级风控暂停");
            return true;
        }
        return false;
    }

    private static void logBlocked(String reason) {
        long now = System.currentTimeMillis();
        if (now - lastLogAt < LOG_INTERVAL_MS) {
            return;
        }
        lastLogAt = now;
        Log.record("写闸门⛔已拦截后续 RPC：" + reason);
    }
}
