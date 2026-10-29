package io.github.aw1y2z.sesame.replay;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** 4.0.0 回放用可注入时钟与随机源：让“今天/连续命中/轮次”这类判断在测试里完全确定。 */
public final class ReplayEnv implements LongSupplier {

    private final AtomicLong now;
    private long[] randomValues;
    private int randomIndex;

    public ReplayEnv(long startMillis) {
        this.now = new AtomicLong(startMillis);
    }

    @Override
    public long getAsLong() {
        return now.get();
    }

    public long now() {
        return now.get();
    }

    public void setNow(long millis) {
        now.set(millis);
    }

    public void advance(long millis) {
        now.addAndGet(millis);
    }

    /** 固定随机序列，循环取用。 */
    public ReplayEnv withRandom(long... values) {
        this.randomValues = values == null || values.length == 0 ? null : values;
        this.randomIndex = 0;
        return this;
    }

    /** 0..bound-1 的确定性“随机”。 */
    public int nextInt(int bound) {
        if (randomValues == null || randomValues.length == 0) {
            return 0;
        }
        long value = randomValues[randomIndex % randomValues.length];
        randomIndex++;
        if (bound <= 0) {
            return 0;
        }
        return (int) Math.floorMod(value, bound);
    }
}