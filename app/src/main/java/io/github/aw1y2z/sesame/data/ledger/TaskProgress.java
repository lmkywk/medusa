package io.github.aw1y2z.sesame.data.ledger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.aw1y2z.sesame.data.plan.PlanTargetLedgers;

/**
 * 某个计划目标的当日任务进度：分母为该目标账本里的可执行任务（去掉需手动/已跳过），
 * 分子为已领 + 执行器当天处理过的任务。只读快照，不触发 RPC。
 */
public final class TaskProgress {

    public int total;
    public int done;
    public String nextTitle = "";
    public String nextStatus = "";
    public final Set<String> executableTitles = new HashSet<>();
    public final Set<String> doneTitles = new HashSet<>();

    public boolean hasTasks() {
        return total > 0;
    }

    public int percent() {
        return total <= 0 ? 0 : Math.min(100, Math.round(done * 100f / total));
    }

    public static TaskProgress forTarget(String userId, String target) {
        String day = TaskLedgerStore.today();
        return from(
                TaskLedgerStore.loadSnapshot(userId, day),
                TaskLedgerStore.loadResolved(userId, day),
                target
        );
    }

    /** 用同一份当日快照计算多个目标，首页/计划页避免重复读盘。 */
    public static TaskProgress from(JSONObject snapshot, JSONObject resolved, String target) {
        TaskProgress progress = new TaskProgress();
        List<PlanTargetLedgers.Entry> domains = PlanTargetLedgers.entriesFor(target);
        if (snapshot == null || domains.isEmpty()) {
            return progress;
        }
        JSONObject allDomains = snapshot.optJSONObject("domains");
        if (allDomains == null) {
            return progress;
        }
        JSONObject handled = resolved == null ? null : resolved.optJSONObject("handled");
        Set<String> seenIds = new HashSet<>();
        List<LedgerTask> tasks = new ArrayList<>();
        for (PlanTargetLedgers.Entry entry : domains) {
            JSONObject domain = allDomains.optJSONObject(entry.domain);
            JSONArray array = domain == null ? null : domain.optJSONArray("tasks");
            for (int i = 0; array != null && i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String id = item.optString("id", "");
                if (!seenIds.add(entry.domain + ":" + id)) {
                    continue;
                }
                LedgerTask task = LedgerTask.fromJson(item);
                if (task.status == LedgerStatus.MANUAL || task.status == LedgerStatus.SKIPPED) {
                    continue;
                }
                tasks.add(task);
                String title = titleOf(task);
                progress.executableTitles.add(title);
                if (task.status == LedgerStatus.CLAIMED) {
                    progress.doneTitles.add(title);
                }
            }
        }
        progress.total = tasks.size();
        if (handled != null) {
            java.util.Iterator<String> keys = handled.keys();
            while (keys.hasNext()) {
                String title = keys.next();
                if (progress.executableTitles.contains(title)) {
                    progress.doneTitles.add(title);
                }
            }
        }
        progress.done = Math.min(progress.total, progress.doneTitles.size());
        pickNext(progress, tasks);
        return progress;
    }

    private static void pickNext(TaskProgress progress, List<LedgerTask> tasks) {
        String claimable = "";
        String untouched = "";
        String inProgress = "";
        for (LedgerTask task : tasks) {
            String title = titleOf(task);
            if (progress.doneTitles.contains(title)) {
                continue;
            }
            if (task.status == LedgerStatus.CLAIMABLE && claimable.isEmpty()) {
                claimable = title;
            } else if (task.status == LedgerStatus.UNTOUCHED && untouched.isEmpty()) {
                untouched = title;
            } else if (task.status == LedgerStatus.IN_PROGRESS && inProgress.isEmpty()) {
                inProgress = title;
            }
        }
        if (!claimable.isEmpty()) {
            progress.nextTitle = claimable;
            progress.nextStatus = "待领取";
        } else if (!untouched.isEmpty()) {
            progress.nextTitle = untouched;
            progress.nextStatus = "未执行";
        } else if (!inProgress.isEmpty()) {
            progress.nextTitle = inProgress;
            progress.nextStatus = "进行中";
        }
    }

    private static String titleOf(LedgerTask task) {
        String title = task.title == null ? "" : task.title.trim();
        return title.isEmpty() ? task.id : title;
    }
}
