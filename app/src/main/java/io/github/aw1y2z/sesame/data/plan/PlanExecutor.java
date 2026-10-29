package io.github.aw1y2z.sesame.data.plan;

import android.content.Context;

import io.github.aw1y2z.sesame.data.AppConfig;
import io.github.aw1y2z.sesame.data.ai.AiDailySummary;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.RunControl;
import io.github.aw1y2z.sesame.util.RunFailure;
import io.github.aw1y2z.sesame.util.RunNotice;
import io.github.aw1y2z.sesame.util.RunScope;
import io.github.aw1y2z.sesame.util.Statistics;
import io.github.aw1y2z.sesame.data.ledger.LedgerScheduler;

/** 串行执行一个计划轮次；只有任务真正跑完且无异常才算完成。 */
public final class PlanExecutor {

    private static final String TAG = "PlanExecutor";

    private PlanExecutor() {
    }

    public static PlanHistory.Record execute(Context context, String userId, Plan plan, String roundTime, int roundIndex) {
        return execute(context, userId, plan, roundTime, roundIndex, PlanHistory.TRIGGER_SCHEDULED, false);
    }

    public static PlanHistory.Record execute(Context context, String userId, Plan plan, String roundTime,
                                             int roundIndex, String trigger) {
        return execute(context, userId, plan, roundTime, roundIndex, trigger, false);
    }

    public static PlanHistory.Record execute(Context context, String userId, Plan plan, String roundTime,
                                             int roundIndex, String trigger, boolean manualOverride) {
        return execute(context, userId, plan, roundTime, roundIndex, trigger, manualOverride, false);
    }

    public static PlanHistory.Record execute(Context context, String userId, Plan plan, String roundTime,
                                             int roundIndex, String trigger, boolean manualOverride,
                                             boolean forceCollect) {
        // 开跑前先把配置对齐磁盘：模块 App 改了开关但广播没送达时，这一句就是「所见即所做」的最后保障
        ConfigV2.reloadIfDiskChanged(userId);
        RunFailure.clear();
        RunNotice.clear();
        // 丢弃上一轮残留的跳过计数与样例
        PlanContext.takeSkipCounts();
        PlanContext.takeSkipSamples();
        long startedAt = System.currentTimeMillis();
        int roundCount = plan.getRoundTimes().size();
        String target = plan.getTarget();
        RunScope.setTarget(target);
        String liveScope = RiskGuard.scopeForTarget(target);
        LiveActions.reset(liveScope);
        PlanContext.setTimedOut(false);
        String label = PlanTargets.labelOf(target);
        String source = trigger == null || trigger.isEmpty() ? PlanHistory.TRIGGER_SCHEDULED : trigger;
        String runId = plan.getId() + "@" + startedAt;
        boolean manual = PlanHistory.TRIGGER_MANUAL.equals(source);
        // 手动直达：任务侧据此按旧版语义执行，自动计划不受影响
        PlanContext.setManual(manual);
        // 按类型执行：空过滤器等于整域全跑，旧计划行为不变
        io.github.aw1y2z.sesame.data.task.TaskFilter planFilter =
                io.github.aw1y2z.sesame.data.task.TaskFilter.fromJson(plan.getFilters());
        PlanContext.setFilter(planFilter);
        if (planFilter != null) {
            Log.record("计划📋按类执行：" + plan.getFilters());
        }

        PlanRuntime runtime = PlanRuntime.load(userId);
        runtime.state = PlanRuntime.RUNNING;
        runtime.runId = runId;
        runtime.trigger = source;
        runtime.manualOverride = manualOverride;
        runtime.planId = plan.getId();
        runtime.target = target;
        runtime.roundTime = roundTime;
        runtime.roundIndex = roundIndex;
        runtime.roundCount = roundCount;
        runtime.completedCount = 0;
        runtime.reason = "";
        runtime.startedAt = startedAt;
        runtime.endedAt = 0L;
        runtime.lastStatus = "";
        runtime.activity = "";
        runtime.activityAt = 0L;
        if (manual) {
            runtime.lastManualStartedAt = startedAt;
            runtime.lastRequestAt = startedAt;
            runtime.lastRequestAccepted = true;
            runtime.lastRejectReason = "";
        }
        runtime.save(userId);
        RunActivity.bind(userId, runId);
        // 先给出目标级回退，具体任务开始后会覆盖成“正在做 XXX”。
        RunActivity.set(label);
        // 分母在开跑时冻结；任务终态由 TaskAttemptPolicy 写入当天进度覆盖层。
        PlanProgress.begin(userId, target, runId);
        PlanFlow.begin();

        PlanHistory.Record record = new PlanHistory.Record();
        record.runId = runId;
        record.planId = plan.getId();
        record.target = target;
        record.roundTime = roundTime;
        record.startedAt = startedAt;
        record.endedAt = 0L;
        record.status = PlanHistory.RUNNING;
        record.reason = manualOverride ? "用户覆盖节律建议" : "";
        record.roundIndex = roundIndex;
        record.roundCount = roundCount;
        record.trigger = source;
        record.manualOverride = manualOverride;
        PlanHistory.upsert(userId, record);
        EventLog.plan("run_start", "runId", runId, "planId", plan.getId(),
                "target", target, "roundTime", roundTime, "roundIndex", roundIndex,
                "trigger", source, "manualOverride", manualOverride,
                // 7 天数据必须能按版本/过滤器切分，否则无法判定问题来自代码还是任务
                "moduleVersion", io.github.aw1y2z.sesame.BuildConfig.VERSION_NAME,
                "filter", plan.getFilters() == null ? "" : plan.getFilters());

        NotificationUtil.updateStatusText("执行中：" + label + " " + roundTime
                + " · 第" + roundIndex + "/" + roundCount + "轮");

        Statistics.resetRoundCollected();
        PlanPacer.configure(plan.getPaceMinutes());
        PlanRunResult result = null;
        String reason = "";
        String status = PlanHistory.INCOMPLETE;
        try {
            if (RunControl.isStopRequested()) {
                reason = "用户停止";
            } else if (RiskGuard.isVerificationRequired()) {
                reason = "需要验证";
            } else if (RiskGuard.isGlobalPaused()) {
                reason = RiskGuard.isManualPause() ? "手动暂停全部自动化" : "账号级风控暂停";
            } else if (RiskGuard.isTargetPaused(target)) {
                reason = "风控域暂停";
            } else {
                PlanContext.setForceCollect(forceCollect);
                result = ModelTask.startPlanTarget(target);
                if (!result.started) {
                    reason = result.reason.isEmpty() ? "未启动（模块未开启或条件不满足）" : result.reason;
                } else if (!result.finished) {
                    reason = result.reason.isEmpty() ? "执行超时" : result.reason;
                } else if (!result.success) {
                    reason = result.reason.isEmpty() ? "执行失败" : result.reason;
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "计划执行异常", t);
            reason = "执行异常：" + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        } finally {
            PlanContext.setForceCollect(false);
            PlanContext.setManual(false);
            PlanContext.setFilter(null);
            java.util.Map<String, Integer> skipped = PlanContext.takeSkipCounts();
            if (!skipped.isEmpty()) {
                StringBuilder summary = new StringBuilder();
                for (java.util.Map.Entry<String, Integer> entry : skipped.entrySet()) {
                    if (summary.length() > 0) {
                        summary.append(" · ");
                    }
                    summary.append(entry.getKey()).append(' ').append(entry.getValue());
                }
                Log.record("计划📋按类跳过：" + summary);
            }
            java.util.Map<String, java.util.List<String>> samples = PlanContext.takeSkipSamples();
            for (java.util.Map.Entry<String, java.util.List<String>> entry : samples.entrySet()) {
                Log.record("计划📋跳过样例：" + entry.getKey() + " → "
                        + String.join("、", entry.getValue()));
                EventLog.plan("capability_skip", "target", target, "class", entry.getKey(),
                        "count", skipped.getOrDefault(entry.getKey(), entry.getValue().size()),
                        "samples", String.join("|", entry.getValue()));
            }
            PlanPacer.clear();
            PlanFlow.end();
        }
        // 先停止活动发布，再写最终回执，避免收尾线程把“正在做…”覆盖进结束状态。
        RunActivity.unbind();
        PlanProgress.end();
        if (result != null && !result.finished) {
            PlanContext.setTimedOut(true);
        }

        int energyDelta = Math.max(0, Statistics.getRoundCollected());
        int completedCount = energyDelta;
        int actions = result == null ? 0 : result.actions;
        // 超时/停止时模块线程可能还没收尾，以实时计数补足，避免回执写“动作 0 项”。
        actions = Math.max(actions, LiveActions.get(liveScope));
        boolean targetRisk = RiskGuard.isGlobalPaused() || RiskGuard.isTargetPaused(target);
        boolean stopped = RunControl.isStopRequested();
        String failure = RunFailure.consumeForTarget(target);
        String notice = RunNotice.consume();
        // 根本没启动（风控/模块关闭/条件不满足）记 SKIPPED：这种轮次不占当天名额，
        // 后面还有机会补跑；只有真的跑了但没跑完才记 INCOMPLETE。
        boolean notStarted = result == null || !result.started;
        if (stopped) {
            status = PlanHistory.STOPPED;
            // 停止请求可能发生在超时文案已经写入之后；以用户停止为准，不留下“执行超时”的假回执。
            reason = "用户停止";
        } else if (notStarted) {
            status = PlanHistory.SKIPPED;
            if (reason.isEmpty()) {
                reason = failure.isEmpty() ? "未启动" : failure;
            }
        } else if (targetRisk) {
            status = PlanHistory.RISK_STOPPED;
            // 停止任务会把 lastRunError 写成“任务被停止”，风控原因要以域暂停记录为准。
            String riskReason = RiskGuard.isGlobalPaused()
                    ? RiskGuard.getReason()
                    : RiskGuard.getScopeReason(RiskGuard.scopeForTarget(target));
            if (riskReason.isEmpty() && target.startsWith("forest.")) {
                riskReason = RiskGuard.getScopeReason(RiskGuard.SCOPE_FOREST);
            }
            if (!riskReason.isEmpty()) {
                reason = riskReason;
            } else if (reason.isEmpty()) {
                reason = failure.isEmpty() ? "执行中触发风控" : failure;
            }
        } else if (!failure.isEmpty()) {
            // 空响应/离线/接口异常不能再被当成完成
            status = PlanHistory.INCOMPLETE;
            reason = failure;
        } else if (result.success && reason.isEmpty()) {
            status = PlanHistory.COMPLETED;
        } else if (reason.isEmpty()) {
            reason = "执行未完成";
        }
        if (PlanHistory.COMPLETED.equals(status)) {
            // 只有收能量计划才能用“能量为 0”判定没事可做；日常/寻宝的产出不一定是能量。
            if (energyDelta == 0 && PlanTargets.FOREST_COLLECT.equals(target)) {
                reason = "今日能量已收集完，没有可执行任务";
                if (notice.isEmpty()) {
                    notice = "没有可执行任务：今天已经完成";
                }
            } else if (actions == 0 && notice.isEmpty()) {
                reason = "没有可执行任务（今日已完成）";
                notice = "没有可执行任务：今天已经完成";
            }
        }
        if (!failure.isEmpty() && notice.isEmpty()) {
            notice = "本轮未完成：" + failure;
        }
        if (manualOverride && reason.isEmpty()) {
            reason = "用户覆盖节律建议";
        }
        long endedAt = System.currentTimeMillis();
        long duration = Math.max(0L, endedAt - startedAt);

        record.endedAt = endedAt;
        record.status = status;
        record.reason = reason;
        record.completedCount = completedCount;
        record.actionCount = actions;
        record.energyDelta = energyDelta;
        PlanHistory.upsert(userId, record);
        EventLog.plan("run_end", "runId", runId, "planId", plan.getId(),
                "target", target, "roundTime", roundTime, "status", status,
                "reasonCode", record.reasonCode, "durationMs", duration,
                "actions", actions, "energyDelta", energyDelta, "trigger", source);
        // 4.0.0：到点后（默认 22 点）当天首次收尾时生成 AI 摘要；异步，绝不阻塞本轮执行
        AiDailySummary.maybeRunAsync(userId);

        // 任务类目标跑完后重抓一次账本：否则任务列表/首页进度还停在跑之前的 TODO 快照，
        // 明明日志里已经领到奖励，页面却显示“未完成/0 项”。
        if (!notStarted && !targetRisk) {
            String ledgerDomain = ledgerDomainForTarget(target);
            if (!ledgerDomain.isEmpty()
                    && (PlanTargetLedgers.hasTaskList(target)
                        || PlanTargets.FOREST_COLLECT.equals(target))) {
                LedgerScheduler.refreshNow(context, ledgerDomain);
            }
        }

        if (stopped) {
            runtime.state = PlanRuntime.STOPPED;
        } else if (targetRisk && !notStarted) {
            runtime.state = PlanRuntime.RISK_STOPPED;
        } else {
            runtime.state = PlanRuntime.IDLE;
        }
        runtime.completedCount = completedCount;
        runtime.reason = reason;
        runtime.endedAt = endedAt;
        runtime.lastStatus = status;
        runtime.lastCompletedCount = completedCount;
        runtime.lastActionCount = actions;
        runtime.lastEnergyDelta = energyDelta;
        runtime.lastDurationMs = duration;
        runtime.lastPlanId = plan.getId();
        runtime.lastTarget = target;
        runtime.lastRoundTime = roundTime;
        runtime.runId = runId;
        runtime.trigger = source;
        runtime.manualOverride = manualOverride;
        runtime.notice = notice;
        runtime.noticeAt = notice.isEmpty() ? 0L : endedAt;
        runtime.activity = "";
        runtime.activityAt = 0L;
        if (manual) {
            runtime.lastManualAt = endedAt;
            runtime.lastManualTarget = target;
            try {
                runtime.manualLastByTarget.put(target, endedAt);
            } catch (Throwable ignored) {
            }
            Integer targetMinutes = AppConfig.INSTANCE.getManualCooldownTarget();
            long targetMs = Math.max(0, targetMinutes == null ? 60 : targetMinutes) * 60_000L;
            runtime.cooldownUntil = endedAt + targetMs;
            runtime.lastRequestAt = startedAt;
            runtime.lastRequestAccepted = true;
            runtime.lastRejectReason = "";
        }
        runtime.save(userId);

        boolean completed = PlanHistory.COMPLETED.equals(status);
        String resultText = completed ? "已完成" : "未完成";
        NotificationUtil.updateStatusText("计划" + label + " " + roundTime + " → " + resultText
                + " · 动作 " + actions + " 项 · 能量 +" + energyDelta + "g"
                + (reason.isEmpty() ? "" : "（" + reason + "）"));
        NotificationUtil.notifyPlanResult(label, roundTime, completed, energyDelta, actions,
                duration, reason, source, status, notice);

        Log.record("计划📋 " + label + " " + roundTime + " → " + resultText
                + " · 动作 " + actions + " 项"
                + " · 能量 +" + energyDelta + "g"
                + " · 用时 " + formatDuration(duration)
                + (reason.isEmpty() ? "" : "（" + reason + "）")
                + " · 触发 " + source);
        if (!notice.isEmpty()) {
            Log.record("计划📋 提示：" + notice);
        }
        RunScope.clearTarget();
        return record;
    }

    /** 任务类目标跑完后要重抓哪个账本域；返回空表示该目标没有任务账本。 */
    private static String ledgerDomainForTarget(String target) {
        if (target == null || target.isEmpty()) {
            return "";
        }
        if (target.startsWith("species.") || PlanTargets.DODO.equals(target)) {
            return "species";
        }
        if (target.startsWith("forest.")) {
            return "forest";
        }
        if (target.startsWith("orchard.")) {
            return "orchard";
        }
        if (target.startsWith("farm")) {
            return "farm";
        }
        if (target.startsWith("ocean")) {
            return "ocean";
        }
        if (PlanTargets.GOLDEN.equals(target)) {
            return "golden";
        }
        return "";
    }

    private static String formatDuration(long durationMs) {
        long seconds = Math.max(0L, durationMs / 1000L);
        if (seconds < 60) {
            return seconds + "秒";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes < 60) {
            return minutes + "分" + rest + "秒";
        }
        return (minutes / 60) + "小时" + (minutes % 60) + "分";
    }
}
