package io.github.aw1y2z.sesame.data.plan;

import java.util.HashSet;
import java.util.Set;

import io.github.aw1y2z.sesame.data.ledger.TaskLedgerStore;
import io.github.aw1y2z.sesame.data.ledger.TaskProgress;

/**
 * 计划运行期间的任务进度发布：任务进入终态时把标题写入当天 progress.json，
 * 计划卡/首页读同一份覆盖层立即更新，不依赖下一次账本同步。
 * <p>只记录进度，不改账本状态；未绑定计划或目标没有任务列表时为空操作。
 */
public final class PlanProgress {

    private static final Object LOCK = new Object();

    private static String userId = "";
    private static String runId = "";
    private static int total;
    private static Set<String> executable = new HashSet<>();
    private static Set<String> alreadyDone = new HashSet<>();
    private static final Set<String> handled = new HashSet<>();

    private PlanProgress() {
    }

    /** 由 PlanExecutor 在计划开始后绑定；分母在此刻冻结。 */
    public static void begin(String uid, String target, String rid) {
        synchronized (LOCK) {
            userId = uid == null ? "" : uid;
            runId = rid == null ? "" : rid;
            handled.clear();
            TaskProgress progress = TaskProgress.forTarget(userId, target);
            total = progress.total;
            executable = new HashSet<>(progress.executableTitles);
            alreadyDone = new HashSet<>(progress.doneTitles);
        }
    }

    /** 运行结束解绑，收尾线程残留的任务不能继续推进进度。 */
    public static void end() {
        synchronized (LOCK) {
            userId = "";
            runId = "";
            total = 0;
            executable.clear();
            alreadyDone.clear();
            handled.clear();
        }
    }

    /** 任务进入终态（成功/已提交/不可执行/当日已试）时调用；同一轮内重复标题只记一次。 */
    public static void resolve(String title) {
        String uid;
        String matched;
        synchronized (LOCK) {
            if (userId.isEmpty() || runId.isEmpty() || total <= 0) {
                return;
            }
            matched = match(title);
            if (matched == null || alreadyDone.contains(matched) || !handled.add(matched)) {
                return;
            }
            uid = userId;
        }
        TaskLedgerStore.addResolved(uid, TaskLedgerStore.today(), matched, "HANDLED");
    }

    /** 精确匹配优先；没有精确命中时只在唯一包含项上落地，避免把同名任务算错。 */
    private static String match(String raw) {
        String title = titleOf(raw);
        if (title.isEmpty()) {
            return null;
        }
        if (executable.contains(title)) {
            return title;
        }
        String found = null;
        for (String candidate : executable) {
            if (candidate.contains(title) || title.contains(candidate)) {
                if (found != null) {
                    return null;
                }
                found = candidate;
            }
        }
        return found;
    }

    private static String titleOf(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
