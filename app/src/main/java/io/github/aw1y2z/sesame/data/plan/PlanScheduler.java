package io.github.aw1y2z.sesame.data.plan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.model.task.antForest.AntForestV2;
import io.github.aw1y2z.sesame.data.system.SystemStatusWriter;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.RunControl;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/** 计划闹钟：一次只排最近的一跳，触发后串行执行并重排下一跳。 */
public final class PlanScheduler {

    public static final String ACTION = "com.eg.android.AlipayGphone.sesame.plan";
    private static final int REQUEST_CODE = 300;
    private static final String TAG = "PlanScheduler";
    private static final long CATCH_UP_DELAY_MS = 5_000L;
    /** 上一轮还没退出时不每 5 秒空转，最多一分钟后再试一次 */
    private static final long BUSY_RETRY_DELAY_MS = 60_000L;
    private static final long MAX_WAKE_LOCK_MS = 90L * 60L * 1000L;
    /** 兜底一次最多串这么多模块时间，超了就留给明晚，避免夜间无界活动 */
    private static final long FALLBACK_BUDGET_MS = 60L * 60L * 1000L;
    private static volatile long lastHistoryCleanupAt;

    /** 最近一次成功设置计划闹钟的时间；仅用于状态报告。 */
    private static volatile long lastAlarmSetAt = 0L;

    public static long getLastAlarmSetAt() {
        return lastAlarmSetAt;
    }

    /** 计划执行放到独立单线程：广播接收器可以立即归还，长任务不占系统广播窗口。 */
    /** 手动执行单独排队：A 域跑完后自动接 B 域，不再直接丢弃“已有轮次在跑”。 */
    private static final ExecutorService MANUAL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Medusa-ManualQueue");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Medusa-PlanRunner");
        thread.setDaemon(false);
        return thread;
    });
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private PlanScheduler() {
    }

    private static PendingIntent pendingIntent(Context context) {
        Intent intent = new Intent(ACTION);
        intent.setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static AlarmManager alarmManager(Context context) {
        return (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public static boolean hasEnabledPlans(String userId) {
        return !PlanStore.loadEnabled(userId).isEmpty();
    }

    /** 是否有一轮计划正在执行；每日建账用它避让，不强行抢 RPC。 */
    public static boolean isBusy() {
        return RUNNING.get();
    }

    /**
     * 广播触发入口：立即返回，实际执行交给单线程和唤醒锁。
     * <p>系统给 goAsync 的时间很短，20 分钟的任务绝不能压在广播线程上。
     */
    public static void requestTrigger(Context context) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            if (!RUNNING.compareAndSet(false, true)) {
                Log.record("计划📋已有轮次在跑，忽略重复触发");
                return;
            }
            RunControl.clearStop();
            PowerManager.WakeLock lock = null;
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Medusa:PlanRun");
                    lock.acquire(MAX_WAKE_LOCK_MS);
                }
            } catch (Throwable t) {
                Log.debug("计划唤醒锁获取失败：" + t);
            }
            try {
                onTrigger(app);
            } finally {
                RUNNING.set(false);
                if (lock != null) {
                    try {
                        if (lock.isHeld()) {
                            lock.release();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    /** 手动立即执行一个目标：使用独立 planId，不消耗真实计划当天的轮次记录。 */
    public static void requestManualRun(Context context, String target, String roundTime) {
        requestManualRun(context, target, roundTime, PlanHistory.TRIGGER_MANUAL, false);
    }

    /** 能量窗口巡检：5 分钟一轮，只收能量，共用同一把执行锁 */
    public static void requestManualRun(Context context, String target, String roundTime, String trigger) {
        requestManualRun(context, target, roundTime, trigger, false);
    }

    /**
     * 手动请求入口。ignoreCooldown 只表示用户已确认节律建议，宿主不会因此拒绝执行；
     * 冷却本身由 UI 计算并提示，软件不阻止用户。
     */
    public static void requestManualRun(Context context, String target, String roundTime, String trigger,
                                        boolean ignoreCooldown) {
        requestManualRun(context, target, roundTime, trigger, ignoreCooldown, false);
    }

    public static void requestManualRun(Context context, String target, String roundTime, String trigger,
                                        boolean ignoreCooldown, boolean forceCollect) {
        requestManualRun(context, target, roundTime, trigger, ignoreCooldown, forceCollect, "");
    }

    /** 带按类过滤的手动执行：filters 为 TaskFilter JSON，空串表示整域全跑。 */
    public static void requestManualRun(Context context, String target, String roundTime, String trigger,
                                        boolean ignoreCooldown, boolean forceCollect, String filters) {
        final Context app = context.getApplicationContext();
        final String source = trigger;
        final String planTarget = target;
        final String time = PlanTargets.isValidTime(roundTime) ? roundTime : formatTime(System.currentTimeMillis());
        MANUAL_EXECUTOR.execute(() -> {
            long waitUntil = System.currentTimeMillis() + 10L * 60L * 1000L;
            while (RUNNING.get() && System.currentTimeMillis() < waitUntil) {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    Log.record("计划📋手动队列已中断：" + PlanTargets.labelOf(planTarget));
                    return;
                }
            }
            if (!RUNNING.compareAndSet(false, true)) {
                Log.record("计划📋手动队列等待超时：" + PlanTargets.labelOf(planTarget));
                return;
            }
            Log.record("计划📋手动队列开始：" + PlanTargets.labelOf(planTarget));
            RunControl.clearStop();
            PowerManager.WakeLock lock = null;
            try {
                PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Medusa:ManualPlan");
                    lock.acquire(MAX_WAKE_LOCK_MS);
                }
            } catch (Throwable t) {
                Log.debug("手动计划唤醒锁获取失败：" + t);
            }
            try {
                if (RiskGuard.isVerificationRequired()) {
                    Log.record("计划📋等待支付宝验证，本次手动执行已忽略");
                    return;
                }
                String userId = UserIdMap.getCurrentUid();
                if (userId == null || userId.isEmpty()) {
                    Log.record("计划📋手动执行失败：当前账号为空");
                    return;
                }
                Plan manual = new Plan();
                manual.setId("manual-" + System.currentTimeMillis());
                manual.setTarget(planTarget);
                manual.setWindowStart("00:00");
                manual.setWindowEnd("23:59");
                manual.setRoundTimes(java.util.Collections.singletonList(time));
                manual.setFilters(filters);
                PlanExecutor.execute(app, userId, manual, time, 1, source, ignoreCooldown, forceCollect);
            } catch (Throwable t) {
                Log.err(TAG, "手动计划执行失败", t);
            } finally {
                RUNNING.set(false);
                if (lock != null) {
                    try {
                        if (lock.isHeld()) {
                            lock.release();
                        }
                    } catch (Throwable ignored) {
                    }
                }
                scheduleNext(app);
                try {
                    SystemStatusWriter.writeAsync(app, "postRun");
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 临时全量测试：按当前已开启的开关，把所有域的主目标排进同一串行队列，不写 plans.json。 */
    public static void requestFullTest(Context context) {
        final Context app = context.getApplicationContext();
        String userId = UserIdMap.getCurrentUid();
        if (userId == null || userId.isEmpty()) {
            Log.record("全量测试⚠️当前账号为空");
            return;
        }
        ConfigV2.load(userId);
        String time = formatTime(System.currentTimeMillis());
        List<String> queued = new ArrayList<>();
        for (String target : PlanTargets.fullTestTargets()) {
            if (!ModelTask.isPlanTargetEnabled(target)) {
                continue;
            }
            queued.add(target);
            requestManualRun(app, target, time, PlanHistory.TRIGGER_MANUAL, true,
                    PlanTargets.FOREST_COLLECT.equals(target));
        }
        Log.record("全量测试📋已加入队列：" + String.join("、", queued));
        EventLog.ledger("full_test_queue", "targets", String.join(",", queued));
    }

    /** 用户主动停止当前运行：关闭写闸门，取消已启动的模块；保护窗口结束后自动解除。 */
    public static void requestStop(Context context) {
        RunControl.requestStop();
        ModelTask.stopAllTask();
        String userId = UserIdMap.getCurrentUid();
        if (userId != null && !userId.isEmpty()) {
            PlanRuntime runtime = PlanRuntime.load(userId);
            runtime.state = PlanRuntime.STOPPED;
            runtime.reason = "用户停止";
            runtime.endedAt = System.currentTimeMillis();
            runtime.lastStatus = PlanHistory.STOPPED;
            runtime.save(userId);
        }
        Log.record("计划📋用户停止当前运行；写闸门已关闭，保护窗口结束后恢复");
        // 停止只针对当前运行：10 秒保护窗口结束后解除写闸门，计划/手动/周期任务都能重新开始。
        new Thread(() -> {
            try {
                Thread.sleep(10_000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            RunControl.clearStop();
            Log.record("计划📋停止保护窗口结束，后续新任务可正常启动");
        }, "Medusa-StopRelease").start();
    }

    /** 计算并设置下一跳；没有计划/没有可执行项时按需保留兜底与试探。 */
    public static void scheduleNext(Context context) {
        try {
            String userId = UserIdMap.getCurrentUid();
            if (userId == null || userId.isEmpty()) {
                return;
            }
            long nowMsForCleanup = System.currentTimeMillis();
            if (nowMsForCleanup - lastHistoryCleanupAt > 60_000L) {
                lastHistoryCleanupAt = nowMsForCleanup;
                PlanHistory.cleanupStaleRunning(userId);
            }
            Calendar now = Calendar.getInstance();
            // 进程被杀时运行快照可能停在 RUNNING；超过一个执行超时窗口就视为上次异常中断
            PlanRuntime stale = PlanRuntime.load(userId);
            if (PlanRuntime.RUNNING.equals(stale.state)
                    && !RUNNING.get()
                    && now.getTimeInMillis() - stale.updatedAt > 2L * 60L * 1000L) {
                stale.state = PlanRuntime.IDLE;
                stale.reason = "上次执行未正常结束";
                stale.lastStatus = PlanHistory.INCOMPLETE;
                stale.endedAt = now.getTimeInMillis();
                stale.save(userId);
                for (PlanHistory.Record record : PlanHistory.load(userId)) {
                    if (PlanHistory.RUNNING.equals(record.status)
                            && stale.runId != null && stale.runId.equals(record.runId)) {
                        record.status = PlanHistory.INCOMPLETE;
                        record.reason = "上次执行被中断";
                        record.endedAt = now.getTimeInMillis();
                        PlanHistory.upsert(userId, record);
                        break;
                    }
                }
                Log.record("计划📋发现上次运行快照超时，已恢复为空闲");
            }
            List<Plan> plans = PlanStore.loadEnabled(userId);
            List<PlanHistory.Record> history = PlanHistory.load(userId);
            long next = Long.MAX_VALUE;
            String nextLabel = "";
            String nextTime = "";

            CatchUp catchUp = nextCatchUp(plans, history, now);
            if (catchUp != null) {
                next = catchUp.at;
                nextLabel = "补跑 " + catchUp.label + "（" + catchUp.time + "）";
                nextTime = formatTime(catchUp.at);
            } else {
                for (Plan plan : plans) {
                    for (String time : plan.getRoundTimes()) {
                        long candidate = todayAt(time, now);
                        if (candidate <= now.getTimeInMillis()) {
                            candidate += 24L * 60 * 60 * 1000;
                        }
                        if (candidate < next) {
                            next = candidate;
                            nextLabel = PlanTargets.labelOf(plan.getTarget());
                            nextTime = time;
                        }
                    }
                }
            }

            List<String> pausedScopes = RiskGuard.getPausedScopes();
            // 22:30 兜底（每天）：22:30-23:59 之间尚未兜底时立即补一次
            if (!Status.hasFlagToday("plan::fallback") && !plans.isEmpty()) {
                long fallbackStart = todayAt("22:30", now);
                long fallbackEnd = todayAt("23:59", now);
                long fallback;
                long nowMs = now.getTimeInMillis();
                if (nowMs < fallbackStart) {
                    fallback = fallbackStart;
                } else if (nowMs <= fallbackEnd) {
                    fallback = nowMs + CATCH_UP_DELAY_MS;
                } else {
                    fallback = fallbackStart + 24L * 60 * 60 * 1000;
                }
                if (fallback < next) {
                    next = fallback;
                    nextLabel = "每日兜底检查";
                    nextTime = "22:30";
                }
            }

            // 00:10 试探：00:10-05:00 之间尚未试探时立即补一次
            if (!Status.hasFlagToday("plan::probe") && !pausedScopes.isEmpty()) {
                long probeStart = todayAt("00:10", now);
                long probeEnd = todayAt("05:00", now);
                long probe;
                long nowMs = now.getTimeInMillis();
                if (nowMs < probeStart) {
                    probe = probeStart;
                } else if (nowMs <= probeEnd) {
                    probe = nowMs + CATCH_UP_DELAY_MS;
                } else {
                    probe = probeStart + 24L * 60 * 60 * 1000;
                }
                if (probe < next) {
                    next = probe;
                    nextLabel = "零点风控试探";
                    nextTime = "00:10";
                }
            }

            // 3.9.0 活力值秒杀：11:45 探针 + 12:00 自动抢（受森林域开关控制）
            long nowMsForSeckill = now.getTimeInMillis();
            if (AntForestV2.isSeckillProbeEnabled() && !Status.hasFlagToday("seckill::probe")) {
                long start = todayAt("11:45", now);
                long end = todayAt("11:59", now);
                long probeAt;
                if (nowMsForSeckill < start) {
                    probeAt = start;
                } else if (nowMsForSeckill <= end) {
                    probeAt = nowMsForSeckill + CATCH_UP_DELAY_MS;
                } else {
                    probeAt = start + 24L * 60L * 60L * 1000L;
                }
                if (probeAt < next) {
                    next = probeAt;
                    nextLabel = "活力值秒杀探针";
                    nextTime = "11:45";
                }
            }
            if (AntForestV2.isSeckillAutoBuyEnabled() && !Status.hasFlagToday("seckill::buy")) {
                long start = todayAt("12:00", now);
                long end = todayAt("12:05", now);
                long buyAt;
                if (nowMsForSeckill < start) {
                    buyAt = start;
                } else if (nowMsForSeckill <= end) {
                    buyAt = nowMsForSeckill + CATCH_UP_DELAY_MS;
                } else {
                    buyAt = start + 24L * 60L * 60L * 1000L;
                }
                if (buyAt < next) {
                    next = buyAt;
                    nextLabel = "活力值自动秒杀";
                    nextTime = "12:00";
                }
            }

            if (next == Long.MAX_VALUE) {
                // 没有下一跳时清掉旧提示，避免“全部停用”后卡片还显示上一次的下一项
                PlanRuntime idle = PlanRuntime.load(userId);
                idle.nextTarget = "";
                idle.nextTime = "";
                idle.save(userId);
                NotificationUtil.setNextExecTime(0);
                NotificationUtil.updateStatusText("计划：当前没有可执行的计划项");
                Log.record("计划📋当前没有可执行的计划项");
                return;
            }
            AlarmManager manager = alarmManager(context);
            if (manager == null) {
                return;
            }
            boolean exact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                exact = false;
            }
            try {
                if (exact) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context));
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context));
                }
            } catch (SecurityException se) {
                // 部分系统即使 canScheduleExactAlarms 为 true 仍会拒绝，降级而不是让整条链断掉
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context));
                exact = false;
            }
            lastAlarmSetAt = System.currentTimeMillis();
            PlanRuntime runtime = PlanRuntime.load(userId);
            runtime.nextTarget = nextLabel;
            runtime.nextTime = nextTime;
            runtime.save(userId);
            NotificationUtil.setNextExecTime(next);
            NotificationUtil.updateStatusText("下一项：" + nextLabel + " " + nextTime
                    + (exact ? "" : "（系统未授权精确闹钟，可能延迟）"));
            Log.record("计划📋下一跳：" + nextLabel + " " + nextTime + " " + formatTime(next)
                    + (exact ? "" : "（非精确闹钟）"));
            EventLog.scheduler("next", "label", nextLabel, "time", nextTime,
                    "nextAt", next, "exact", exact);
        } catch (Throwable t) {
            Log.err(TAG, "排计划闹钟失败", t);
        }
    }

    /** 广播触发：在后台线程串行执行所有到期项，然后排下一跳。 */
    public static void onTrigger(Context context) {
        String userId = UserIdMap.getCurrentUid();
        if (userId == null || userId.isEmpty()) {
            return;
        }
        try {
            List<Plan> plans = PlanStore.loadEnabled(userId);
            Calendar now = Calendar.getInstance();
            List<Plan> due = new ArrayList<>();
            for (Plan plan : plans) {
                if (firstDueTime(plan, now.getTimeInMillis(), PlanHistory.load(userId)) != null) {
                    due.add(plan);
                }
            }
            due.sort(Comparator.comparingInt(plans::indexOf));
            for (Plan plan : due) {
                List<PlanHistory.Record> history = PlanHistory.load(userId);
                String dueTime = firstDueTime(plan, now.getTimeInMillis(), history);
                if (dueTime == null) {
                    continue;
                }
                long dueAt = todayAt(dueTime, now);
                boolean catchUp = now.getTimeInMillis() - dueAt > 2L * 60L * 1000L;
                // 模型还在跑时不要进入 PlanExecutor：否则每触发一次就多一条 SKIPPED。
                // 只记一次「上一轮尚未结束」，下一次补跑按 BUSY_RETRY_DELAY_MS 退避。
                if (ModelTask.isPlanTargetRunning(plan.getTarget())) {
                    recordBusySkipOnce(userId, plan, dueTime,
                            plan.getRoundTimes().indexOf(dueTime) + 1,
                            catchUp ? PlanHistory.TRIGGER_CATCHUP : PlanHistory.TRIGGER_SCHEDULED);
                    continue;
                }
                PlanExecutor.execute(context, userId, plan, dueTime,
                        plan.getRoundTimes().indexOf(dueTime) + 1,
                        catchUp ? PlanHistory.TRIGGER_CATCHUP : PlanHistory.TRIGGER_SCHEDULED);
            }

            if (!Status.hasFlagToday("plan::probe")) {
                long probe = todayAt("00:10", now);
                long end = todayAt("05:00", now);
                if (now.getTimeInMillis() >= probe && now.getTimeInMillis() <= end) {
                    probeLimitScopes(context, userId, now);
                    Status.flagToday("plan::probe");
                }
            }
            if (!Status.hasFlagToday("plan::fallback")) {
                long fallback = todayAt("22:30", now);
                long end = todayAt("23:59", now);
                if (now.getTimeInMillis() >= fallback && now.getTimeInMillis() <= end) {
                    runFallback(context, userId, plans);
                    Status.flagToday("plan::fallback");
                }
            }
            long nowMs = now.getTimeInMillis();
            if (AntForestV2.isSeckillProbeEnabled() && !Status.hasFlagToday("seckill::probe")) {
                long start = todayAt("11:45", now);
                long end = todayAt("11:59", now);
                if (nowMs >= start && nowMs <= end) {
                    AntForestV2.runSeckillProbe(context);
                    Status.flagToday("seckill::probe");
                }
            }
            if (AntForestV2.isSeckillAutoBuyEnabled() && !Status.hasFlagToday("seckill::buy")) {
                long start = todayAt("12:00", now);
                long end = todayAt("12:05", now);
                if (nowMs >= start && nowMs <= end) {
                    AntForestV2.runSeckillBuy(context);
                    Status.flagToday("seckill::buy");
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "计划触发执行失败", t);
        } finally {
            try {
                recordMissedWindows(userId);
            } catch (Throwable t) {
                Log.err(TAG, "补记错过窗口失败", t);
            }
            scheduleNext(context);
            try {
                SystemStatusWriter.writeAsync(context, "postRun");
            } catch (Throwable ignored) {
            }
        }
    }

    /** 00:10 后对 1004 限流域与 1009 服务端拒绝域各试探一轮；成功则恢复，再触发会重新暂停。 */
    private static void probeLimitScopes(Context context, String userId, Calendar now) {
        String time = formatTime(now.getTimeInMillis());
        for (String scope : new ArrayList<>(RiskGuard.getPausedScopes())) {
            if (!PlanTargets.isValidTarget(scope) || scope.endsWith(".external")) {
                continue;
            }
            String reason = RiskGuard.getScopeReason(scope);
            if (reason == null || (!reason.contains("1004") && !reason.contains("1009"))) {
                continue;
            }
            Log.record("风控保护🌅零点试探：" + PlanTargets.labelOf(scope));
            RiskGuard.resumeScope(scope);
            Plan probe = new Plan();
            probe.setTarget(scope);
            probe.setWindowStart("00:00");
            probe.setWindowEnd("05:00");
            probe.setRoundTimes(java.util.Collections.singletonList(time));
            PlanExecutor.execute(context, userId, probe, time, 1, PlanHistory.TRIGGER_PROBE);
        }
    }

    private static void runFallback(Context context, String userId, List<Plan> plans) {
        java.util.Set<String> ranModels = new java.util.HashSet<>();
        for (Plan plan : plans) {
            List<PlanHistory.Record> history = PlanHistory.load(userId);
            ranModels.add(modelKeyOf(plan.getTarget()));
            for (String time : plan.getRoundTimes()) {
                if (!isCompleted(history, plan, time, System.currentTimeMillis())) {
                    Log.record("计划📋晚间兜底补跑：" + PlanTargets.labelOf(plan.getTarget()) + " " + time);
                    PlanExecutor.execute(context, userId, plan, time,
                            plan.getRoundTimes().indexOf(time) + 1, PlanHistory.TRIGGER_FALLBACK);
                    break;
                }
            }
        }
        // 计划只覆盖它自己点到的域。其余「模块已开启、但没有任何计划排到」的，兜底各补一轮——
        // 否则用户开了庄园却永远等不到庄园执行（2.3.2 真机实测就是这个现象）。
        if (!io.github.aw1y2z.sesame.data.AppConfig.INSTANCE.getWholeDomainFallback()) {
            Log.record("计划📋整域兜底巡检已关闭，仅补跑计划内目标");
            return;
        }
        String now = formatTime(System.currentTimeMillis());
        long budgetEnd = System.currentTimeMillis() + FALLBACK_BUDGET_MS;
        for (String target : PlanTargets.all()) {
            if (target.endsWith(".external") || ranModels.contains(modelKeyOf(target))) {
                continue;
            }
            if (!ModelTask.isPlanTargetEnabled(target)) {
                continue;
            }
            if (System.currentTimeMillis() > budgetEnd) {
                Log.record("计划📋兜底已用满 1 小时，剩余模块留给明晚：" + PlanTargets.labelOf(target));
                break;
            }
            ranModels.add(modelKeyOf(target));
            Log.record("计划📋晚间兜底（未排计划的已开启模块）：" + PlanTargets.labelOf(target));
            Plan extra = new Plan();
            extra.setId("fallback-" + target.replace('.', '_'));
            extra.setTarget(target);
            extra.setWindowStart("00:00");
            extra.setWindowEnd("23:59");
            extra.setRoundTimes(java.util.Collections.singletonList(now));
            PlanExecutor.execute(context, userId, extra, now, 1, PlanHistory.TRIGGER_FALLBACK);
        }
    }

    /** 森林的收能量/寻宝共用同一个模块，兜底时只补一次 */
    private static String modelKeyOf(String target) {
        return target != null && target.startsWith("forest.") ? "forest" : target;
    }

    /**
     * 今日已到点但还没跑过的轮次：只在计划时间窗内补跑一次，避免把整天的任务在深夜全部翻出来。
     * 上一轮模型还没退出时退避到 BUSY_RETRY_DELAY_MS，避免每 5 秒空转刷 SKIPPED。
     */
    private static CatchUp nextCatchUp(List<Plan> plans, List<PlanHistory.Record> history, Calendar now) {
        long nowMs = now.getTimeInMillis();
        for (Plan plan : plans) {
            long start = todayAt(plan.getWindowStart(), now);
            long end = todayAt(plan.getWindowEnd(), now);
            if (nowMs < start || nowMs > end) {
                continue;
            }
            for (String time : plan.getRoundTimes()) {
                long at = todayAt(time, now);
                if (at > nowMs || isAttempted(history, plan, time, nowMs)) {
                    continue;
                }
                long retryAt = nowMs + CATCH_UP_DELAY_MS;
                if (ModelTask.isPlanTargetRunning(plan.getTarget())) {
                    retryAt = nowMs + BUSY_RETRY_DELAY_MS;
                } else {
                    PlanHistory.Record lastSkip = lastSkippedToday(history, plan, time, nowMs);
                    if (lastSkip != null && nowMs - lastSkip.startedAt < BUSY_RETRY_DELAY_MS) {
                        retryAt = lastSkip.startedAt + BUSY_RETRY_DELAY_MS;
                    }
                }
                if (retryAt > end) {
                    // 窗口内已经等不到下一次；本次不排补跑，错过由 recordMissedWindows 记一次
                    continue;
                }
                return new CatchUp(retryAt, time, PlanTargets.labelOf(plan.getTarget()));
            }
        }
        return null;
    }

    private static PlanHistory.Record lastSkippedToday(List<PlanHistory.Record> history, Plan plan,
                                                       String time, long nowMs) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(nowMs);
        PlanHistory.Record best = null;
        for (PlanHistory.Record record : history) {
            if (!plan.getId().equals(record.planId) || !time.equals(record.roundTime)) {
                continue;
            }
            if (!PlanHistory.SKIPPED.equals(record.status) || !isSameDay(record.startedAt, day)) {
                continue;
            }
            if (best == null || record.startedAt > best.startedAt) {
                best = record;
            }
        }
        return best;
    }

    private static boolean isSameDay(long millis, Calendar day) {
        Calendar at = Calendar.getInstance();
        at.setTimeInMillis(millis);
        return at.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && at.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    /** 模型仍在跑时只补一条可见记录，不进入 PlanExecutor，避免每次触发都刷 SKIPPED。 */
    private static void recordBusySkipOnce(String userId, Plan plan, String time, int roundIndex, String trigger) {
        List<PlanHistory.Record> history = PlanHistory.load(userId);
        Calendar now = Calendar.getInstance();
        long nowMs = now.getTimeInMillis();
        for (PlanHistory.Record record : history) {
            if (!plan.getId().equals(record.planId) || !time.equals(record.roundTime)) {
                continue;
            }
            if (!PlanHistory.SKIPPED.equals(record.status) || !isSameDay(record.startedAt, now)) {
                continue;
            }
            if (record.reason != null && record.reason.contains("上一轮尚未结束")) {
                return;
            }
        }
        PlanHistory.Record record = new PlanHistory.Record();
        record.runId = "busy-" + plan.getId() + "@" + nowMs;
        record.planId = plan.getId();
        record.target = plan.getTarget();
        record.roundTime = time;
        record.startedAt = nowMs;
        record.endedAt = nowMs;
        record.status = PlanHistory.SKIPPED;
        record.reason = "上一轮尚未结束，补跑已延后";
        record.roundIndex = roundIndex;
        record.roundCount = plan.getRoundTimes().size();
        record.trigger = trigger;
        PlanHistory.upsert(userId, record);
        Log.record("计划📋 " + PlanTargets.labelOf(plan.getTarget()) + " " + time
                + " 上一轮尚未结束，已记一次跳过并延后");
    }

    /** 窗口已经结束、但今天完全没有执行记录的轮次补记一次，避免被别的长轮次吞掉后无声无息。 */
    private static void recordMissedWindows(String userId) {
        if (userId == null || userId.isEmpty()) {
            return;
        }
        List<Plan> plans = PlanStore.loadEnabled(userId);
        List<PlanHistory.Record> history = PlanHistory.load(userId);
        Calendar now = Calendar.getInstance();
        long nowMs = now.getTimeInMillis();
        for (Plan plan : plans) {
            long end = todayAt(plan.getWindowEnd(), now);
            if (nowMs <= end || ModelTask.isPlanTargetRunning(plan.getTarget())) {
                continue;
            }
            for (int i = 0; i < plan.getRoundTimes().size(); i++) {
                String time = plan.getRoundTimes().get(i);
                long at = todayAt(time, now);
                if (at > end || hasRecordToday(history, plan, time, now)) {
                    continue;
                }
                PlanHistory.Record record = new PlanHistory.Record();
                record.runId = "missed-" + plan.getId() + "@" + at;
                record.planId = plan.getId();
                record.target = plan.getTarget();
                record.roundTime = time;
                record.startedAt = at;
                record.endedAt = at;
                record.status = PlanHistory.SKIPPED;
                record.reason = "窗口已结束，未执行";
                record.roundIndex = i + 1;
                record.roundCount = plan.getRoundTimes().size();
                record.trigger = PlanHistory.TRIGGER_CATCHUP;
                PlanHistory.upsert(userId, record);
                EventLog.scheduler("missed", "planId", plan.getId(),
                        "target", plan.getTarget(), "roundTime", time);
                Log.record("计划📋 " + PlanTargets.labelOf(plan.getTarget()) + " " + time
                        + " 窗口已结束，补记未执行");
            }
        }
    }

    private static boolean hasRecordToday(List<PlanHistory.Record> history, Plan plan, String time, Calendar now) {
        for (PlanHistory.Record record : history) {
            if (plan.getId().equals(record.planId) && time.equals(record.roundTime)
                    && isSameDay(record.startedAt, now)) {
                return true;
            }
        }
        return false;
    }

    private static final class CatchUp {
        final long at;
        final String time;
        final String label;

        CatchUp(long at, String time, String label) {
            this.at = at;
            this.time = time;
            this.label = label;
        }
    }

    private static String firstDueTime(Plan plan, long now, List<PlanHistory.Record> history) {
        return PlanTimes.firstDueTime(plan, now, history);
    }

    /** 正常轮次只看“今天是否已经跑过”，失败/未完成不在当天无限重试；22:30 兜底再补一次。 */
    private static boolean isAttempted(List<PlanHistory.Record> history, Plan plan, String time, long now) {
        return PlanTimes.isAttempted(history, plan, time, now);
    }

    private static boolean isCompleted(List<PlanHistory.Record> history, Plan plan, String time, long now) {
        return PlanTimes.isCompleted(history, plan, time, now);
    }

    private static long todayAt(String time, Calendar now) {
        return PlanTimes.todayAt(time, now);
    }

    private static String formatTime(long time) {
        return PlanTimes.formatTime(time);
    }
}
