package io.github.aw1y2z.sesame.util;

/**
 * 4.0.0：1009 冷却升级的纯函数实现，供 RiskGuard 与 golden test 共用。
 * <p>第一次 30 分钟；1 小时内再次 1009 升到 2 小时；第三次 8 小时；之后保持 8 小时。
 * 距上次命中超过 1 小时视为新的一轮，从 30 分钟重新计。
 */
public final class RiskCooldown {

    public static final long FIRST_MS = 30L * 60L * 1000L;
    public static final long SECOND_MS = 2L * 60L * 60L * 1000L;
    public static final long THIRD_MS = 8L * 60L * 60L * 1000L;
    /** 同域相邻两次 1009 在这个窗口内才算“连续命中” */
    public static final long STRIKE_WINDOW_MS = 60L * 60L * 1000L;

    private RiskCooldown() {
    }

    public static final class State {
        public final int strikes;
        public final long lastStrikeAt;

        public State(int strikes, long lastStrikeAt) {
            this.strikes = strikes;
            this.lastStrikeAt = lastStrikeAt;
        }
    }

    /**
     * @param previous 上一次的冷却状态；null 表示当前没有生效中的冷却
     * @param now      本次 1009 的时间
     */
    public static State next(State previous, long now) {
        int strikes = 1;
        if (previous != null && previous.strikes > 0) {
            long last = previous.lastStrikeAt;
            if (last > 0L && now - last <= STRIKE_WINDOW_MS) {
                strikes = previous.strikes + 1;
            }
        }
        return new State(strikes, now);
    }

    public static long cooldownFor(int strikes) {
        if (strikes <= 1) {
            return FIRST_MS;
        }
        if (strikes == 2) {
            return SECOND_MS;
        }
        return THIRD_MS;
    }

    /** 冷却到期的可读时长（日志用）。 */
    public static String describe(long cooldownMs) {
        if (cooldownMs >= 60L * 60L * 1000L) {
            return (cooldownMs / (60L * 60L * 1000L)) + "小时";
        }
        return (cooldownMs / 60_000L) + "分钟";
    }
}
