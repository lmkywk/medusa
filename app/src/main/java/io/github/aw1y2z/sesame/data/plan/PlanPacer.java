package io.github.aw1y2z.sesame.data.plan;

/**
 * 把任务列表平均铺到计划设定的分钟数内。
 * 只影响主动调用 step() 的任务循环；paceMinutes=0 时完全无副作用。
 */
public final class PlanPacer {

    private static final ThreadLocal<State> STATE = new ThreadLocal<>();

    private static final class State {
        final long startAt;
        final long paceMs;
        int total;
        int done;

        State(long startAt, long paceMs, int total) {
            this.startAt = startAt;
            this.paceMs = paceMs;
            this.total = total;
        }
    }

    private PlanPacer() {
    }

    public static void configure(int paceMinutes) {
        if (paceMinutes <= 0) {
            STATE.remove();
        } else {
            STATE.set(new State(System.currentTimeMillis(), paceMinutes * 60_000L, 0));
        }
    }

    public static void begin(int total) {
        State state = STATE.get();
        if (state == null || total <= 0) {
            return;
        }
        state.total = Math.max(state.total, total);
    }

    public static void step() {
        State state = STATE.get();
        if (state == null || state.total <= 0) {
            return;
        }
        state.done++;
        long target = state.startAt + state.paceMs * state.done / state.total;
        long delay = target - System.currentTimeMillis();
        if (delay > 0L) {
            try {
                Thread.sleep(Math.min(delay, 60_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void clear() {
        STATE.remove();
    }
}
