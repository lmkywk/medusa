package io.github.aw1y2z.sesame.data.plan;

/** 一个计划目标的真实执行结果：启动、结束、成功/失败都要可解释。 */
public final class PlanRunResult {

    public String target = "";
    public boolean started;
    public boolean finished;
    public boolean success;
    public String reason = "";
    public int actions;
    public long startedAt;
    public long endedAt;

    public long durationMs() {
        return endedAt > startedAt ? endedAt - startedAt : 0L;
    }

    public PlanRunResult finish() {
        endedAt = System.currentTimeMillis();
        return this;
    }
}
