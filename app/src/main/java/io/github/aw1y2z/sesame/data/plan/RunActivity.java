package io.github.aw1y2z.sesame.data.plan;

/**
 * 当前执行任务/阶段的轻量发布器。
 * <p>任务线程只写 PlanRuntime 的 activity 字段，计划卡每 1–2 秒读取一次；
 * 不额外发 RPC，也不改变任务执行逻辑。
 */
public final class RunActivity {

    private static final int MAX_LENGTH = 80;

    private static volatile String userId = "";
    private static volatile String runId = "";

    private RunActivity() {
    }

    /** 叶子任务事件流读取当前轮次；未绑定返回空串。 */
    public static String currentUserId() {
        return userId;
    }

    public static String currentRunId() {
        return runId;
    }

    /** 由 PlanExecutor 在写入 RUNNING 快照后绑定。 */
    public static void bind(String uid, String rid) {
        userId = uid == null ? "" : uid;
        runId = rid == null ? "" : rid;
    }

    /** 运行结束时解绑，之后残留的子任务不能再覆盖最终回执。 */
    public static void unbind() {
        userId = "";
        runId = "";
    }

    /** 发布当前任务/阶段；同一轮内相同文本不重复写盘。 */
    public static void set(String text) {
        String uid = userId;
        String rid = runId;
        if (uid.isEmpty() || rid.isEmpty() || text == null) {
            return;
        }
        String clean = text.trim();
        if (clean.isEmpty()) {
            return;
        }
        if (clean.length() > MAX_LENGTH) {
            clean = clean.substring(0, MAX_LENGTH - 1) + "…";
        }
        synchronized (RunActivity.class) {
            if (!uid.equals(userId) || !rid.equals(runId)) {
                return;
            }
            PlanRuntime runtime = PlanRuntime.load(uid);
            if (!PlanRuntime.RUNNING.equals(runtime.state) || !rid.equals(runtime.runId)) {
                return;
            }
            if (clean.equals(runtime.activity)) {
                return;
            }
            runtime.activity = clean;
            runtime.activityAt = System.currentTimeMillis();
            runtime.save(uid);
        }
    }
}
