package io.github.aw1y2z.sesame.data.ledger;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.aw1y2z.sesame.data.plan.PlanScheduler;
import io.github.aw1y2z.sesame.util.EventLog;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

/**
 * 每日 00:01 系统建账：只读抓取任务列表，失败按 00:05 / 00:15 / 00:30 后每小时补。
 * <p>不占用用户计划；与计划执行共用“尽量避免并发”的检查，但读取很快，不强行阻塞计划。
 */
public final class LedgerScheduler {

    public static final String ACTION = "com.eg.android.AlipayGphone.sesame.ledger";
    public static final String EXTRA_STATUS_ONLY = "statusOnly";
    private static final int REQUEST_CODE = 410;
    private static final int STATUS_REQUEST_CODE = 411;
    private static final String PREFS = "medusa_ledger";
    private static final String KEY_RETRY = "retryCount";
    private static final String KEY_STATUS_RETRY = "statusRetryCount";
    private static final String KEY_STATUS_RETRY_DAY = "statusRetryDay";
    private static final String KEY_FULL_SYNC_DAY = "lastFullSyncDay";
    private static final String SYNC_TIME = "00:05";
    private static final String TAG = "LedgerScheduler";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);

    private LedgerScheduler() {
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static void scheduleNext(Context context) {
        scheduleAt(context, nextMidnight(), false);
    }

    /**
     * 宿主重新加载时：今天还没有完成一次“全量同步”就 1 分钟后补建，
     * 否则只排下一天。首次安装会走这里，即使已有部分账本也会再完整跑一次。
     */
    public static void ensureToday(Context context) {
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) {
            scheduleNext(context);
            return;
        }
        String last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FULL_SYNC_DAY, "");
        if (!TaskLedgerStore.today().equals(last)) {
            scheduleAt(context, System.currentTimeMillis() + 60_000L, true);
        } else {
            scheduleNext(context);
        }
    }

    public static void onAlarm(Context context) {
        onAlarm(context, false);
    }

    public static void onAlarm(Context context, boolean statusOnly) {
        final Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) {
            if (statusOnly) {
                scheduleAt(app, System.currentTimeMillis() + 5L * 60_000L, false, true);
            }
            return;
        }
        PowerManager.WakeLock lock = null;
        try {
            PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Medusa:Ledger");
                lock.acquire(15L * 60L * 1000L);
            }
        } catch (Throwable ignored) {
        }
        try {
            String trigger = statusOnly ? "status" : "alarm";
            EventLog.ledger(statusOnly ? "status_sync_start" : "sync_start", "trigger", trigger);
            if (statusOnly) {
                runStatusOnly(app);
            } else {
                runCapture(app);
            }
        } finally {
            RUNNING.set(false);
            EventLog.ledger(statusOnly ? "status_sync_end" : "sync_end", "trigger", "alarm");
            if (lock != null) {
                try {
                    if (lock.isHeld()) {
                        lock.release();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 手动刷新：模块 UI 发广播触发，只读；同一时间只允许一个刷新线程，避免重复请求叠加风控。 */
    public static void refreshNow(Context context, String domain) {
        refreshNow(context, domain, false);
    }

    /**
     * @param includeSports 首页“同步”按钮传 true：连运动域状态+动能账本一起抓。
     *                      自动全量同步仍保持 false，避免每天首次建账就撞高风险接口。
     */
    public static void refreshNow(Context context, String domain, boolean includeSports) {
        final Context app = context.getApplicationContext();
        if (!REFRESHING.compareAndSet(false, true)) {
            Log.record("任务账本⏸已有刷新进行中，忽略重复请求");
            EventLog.ledger("refresh_skip", "reason", "busy");
            return;
        }
        EventLog.ledger("refresh_start", "domain", domain == null ? "" : domain);
        new Thread(() -> {
            try {
                if (RiskGuard.isVerificationRequired()) {
                    Log.record("任务账本⏸等待支付宝验证，暂停刷新");
                    return;
                }
                String uid = UserIdMap.getCurrentUid();
                if (uid == null || uid.isEmpty()) {
                    return;
                }
                boolean all = domain == null || domain.isEmpty();
                boolean ocean = all || OceanTaskLedger.DOMAIN.equals(domain);
                if (ocean || "ocean.reward".equals(domain)) {
                    boolean ok = OceanTaskLedger.capture(uid);
                    Log.record(ok ? "任务账本🔄海洋奖励已刷新" : "任务账本⚠️海洋奖励刷新失败");
                }
                if (ocean || OceanTaskLedger.DOMAIN_FISH.equals(domain)) {
                    boolean ok = OceanTaskLedger.captureFish(uid);
                    Log.record(ok ? "任务账本🔄摸鱼任务已刷新" : "任务账本⚠️摸鱼任务刷新失败");
                }
                if (ocean || "ocean.welcome".equals(domain) || "ocean.clean".equals(domain)
                        || OceanTaskLedger.DOMAIN_FISH.equals(domain)) {
                    boolean ok = OceanStatus.capture(uid);
                    Log.record(ok ? "任务账本🔄海洋状态已刷新" : "任务账本⚠️海洋状态刷新失败");
                }
                if (all || FarmTaskLedger.DOMAIN.equals(domain)) {
                    boolean ok = FarmTaskLedger.captureMain(uid);
                    Log.record(ok ? "任务账本🔄庄园饲料任务已刷新" : "任务账本⚠️庄园饲料任务刷新失败");
                    FarmCompetitionStatus.capture(uid);
                    FarmSunflowerStatus.capture(uid);
                    boolean statusOk = FarmStatus.capture(uid);
                    Log.record(statusOk ? "任务账本🔄庄园状态已刷新" : "任务账本⚠️庄园状态刷新失败");
                }
                if (all || FarmTaskLedger.DOMAIN_DRAW.equals(domain)) {
                    boolean ok = FarmTaskLedger.captureDraw(uid);
                    Log.record(ok ? "任务账本🔄庄园抽抽乐任务已刷新" : "任务账本⚠️庄园抽抽乐刷新失败");
                }
                if (all || "forest".equals(domain) || ForestTaskLedger.DOMAIN.equals(domain)) {
                    ForestStatus.capture(uid);
                    boolean ok = ForestTaskLedger.capture(uid);
                    Log.record(ok ? "任务账本🔄森林奖励任务已刷新" : "任务账本⚠️森林奖励任务刷新失败");
                }
                if (all || "forest".equals(domain) || ForestParkTaskLedger.DOMAIN.equals(domain)) {
                    ForestParkTaskLedger.capture(uid);
                }
                if (all || "forest".equals(domain) || ForestPatrolTaskLedger.DOMAIN.equals(domain)) {
                    ForestPatrolTaskLedger.capture(uid);
                }
                if (all || "forest".equals(domain) || "forest.backpack".equals(domain)) {
                    ForestBackpackStatus.capture(uid);
                }
                if (all || "forest".equals(domain) || "forest.ip".equals(domain)) {
                    ForestIpStatus.capture(uid);
                }
                if (all || "species".equals(domain) || SpeciesTaskLedger.DOMAIN.equals(domain)) {
                    boolean ok = SpeciesTaskLedger.capture(uid);
                    SpeciesStatus.capture(uid);
                    Log.record(ok ? "任务账本🔄神奇物种任务已刷新" : "任务账本⚠️神奇物种任务刷新失败");
                }
                if (all || "protect".equals(domain)) {
                    boolean ok = ProtectStatus.capture(uid);
                    Log.record(ok ? "任务账本🔄生态保护状态已刷新" : "任务账本⚠️生态保护状态刷新失败");
                }
                if (all || "greenAction".equals(domain)) {
                    boolean ok = GreenActionStatus.capture(uid);
                    Log.record(ok ? "任务账本🔄绿色行动状态已刷新" : "任务账本⚠️绿色行动状态刷新失败");
                }
                if (all || "orchard".equals(domain)) {
                    OrchardStatus.capture(uid);
                }
                if (all || "orchard".equals(domain) || OrchardTaskLedger.DOMAIN.equals(domain)) {
                    boolean ok = OrchardTaskLedger.capture(uid);
                    Log.record(ok ? "任务账本🔄果园肥料任务已刷新" : "任务账本⚠️果园肥料任务刷新失败");
                }
                if (all || "orchard".equals(domain) || OrchardParkTaskLedger.DOMAIN.equals(domain)) {
                    boolean ok = OrchardParkTaskLedger.capture(uid);
                    Log.record(ok ? "任务账本🔄果园乐园任务已刷新" : "任务账本⚠️果园乐园任务刷新失败");
                }
                if (all || "orchard".equals(domain) || OrchardDrawTaskLedger.DOMAIN.equals(domain)) {
                    boolean ok = OrchardDrawTaskLedger.capture(uid);
                    Log.record(ok ? "任务账本🔄果园抽抽乐任务已刷新" : "任务账本⚠️果园抽抽乐任务刷新失败");
                }
                if (all || "orchard".equals(domain) || OrchardChallengeTaskLedger.DOMAIN.equals(domain)) {
                    boolean ok = OrchardChallengeTaskLedger.capture(uid);
                    Log.record(ok ? "任务账本🔄果园限时挑战已刷新" : "任务账本⚠️果园限时挑战刷新失败");
                }
                if (all || "member".equals(domain)) {
                    boolean ok = MemberStatus.capture(uid);
                    Log.record(ok ? "任务账本🔄会员状态已刷新" : "任务账本⚠️会员状态刷新失败");
                }
                if (all || "stall".equals(domain)) {
                    boolean ok = StallStatus.capture(uid);
                    Log.record(ok ? "任务账本🔄新村状态已刷新" : "任务账本⚠️新村状态刷新失败");
                }
                if (all || "golden".equals(domain) || GoldenBeansTaskLedger.DOMAIN.equals(domain)) {
                    if (riskAborted()) {
                        return;
                    }
                    if (RiskGuard.isPaused("golden")) {
                        Log.record("任务账本⏸金豆域保护性暂停，跳过任务刷新");
                    } else {
                        boolean ok = GoldenBeansTaskLedger.capture(uid);
                        Log.record(ok ? "任务账本🔄金豆任务已刷新" : "任务账本⚠️金豆任务刷新失败");
                    }
                }
                boolean sportsExplicit = "sports".equals(domain)
                        || KineticTaskLedger.DOMAIN.equals(domain)
                        || "sports.status".equals(domain);
                if (sportsExplicit || (all && includeSports)) {
                    if (riskAborted()) {
                        return;
                    }
                    if (RiskGuard.isPaused("sports")) {
                        Log.record("任务账本⏸运动域保护性暂停，跳过动能任务刷新");
                    } else {
                        if (!"sports.status".equals(domain)) {
                            boolean ok = KineticTaskLedger.capture(uid);
                            Log.record(ok ? "任务账本🔄动能任务已刷新" : "任务账本⚠️动能任务刷新失败");
                        }
                        boolean statusOk = KineticStatus.capture(uid);
                        Log.record(statusOk ? "任务账本🔄运动状态已刷新" : "任务账本⚠️运动状态刷新失败");
                    }
                } else if (all) {
                    Log.record("任务账本⏸运动账本不参与自动同步；首页点“同步”可一并刷新运动域");
                }
                if (all) {
                    scheduleStatusRetryIfNeeded(app, uid);
                }
                TaskLedgerStore.writeDailyReport(uid, TaskLedgerStore.today());
            } catch (Throwable t) {
                Log.err(TAG, "refreshNow err:", t);
            } finally {
                REFRESHING.set(false);
                EventLog.ledger("refresh_end", "domain", domain == null ? "" : domain);
            }
        }, "Medusa-LedgerRefresh").start();
    }

    /** 刷新过程中出现滑块/1009 时立刻收手，避免继续打高风险查询。 */
    private static boolean riskAborted() {
        if (RiskGuard.isVerificationRequired() || RiskGuard.isGlobalPaused()) {
            Log.record("任务账本⏸检测到风控暂停，终止本轮刷新");
            EventLog.ledger("refresh_abort", "reason", "risk");
            return true;
        }
        return false;
    }

    private static void runCapture(Context context) {
        if (RiskGuard.isVerificationRequired()) {
            Log.record("任务账本⏸等待支付宝验证，暂停建账");
            return;
        }
        String uid = UserIdMap.getCurrentUid();
        if (uid == null || uid.isEmpty()) {
            scheduleRetry(context);
            return;
        }
        if (PlanScheduler.isBusy()) {
            // 账本是后续执行的依据：只读同步不再给计划让路。
            // 手动刷新已证明可与能量收集并行，RPC 量很小；风控仍由 RiskGuard 统一拦截。
            Log.record("任务账本📖计划运行中，仍执行只读同步");
        }
        try {
            boolean ok = OceanTaskLedger.capture(uid);
            boolean fishOk = OceanTaskLedger.captureFish(uid);
            boolean farmOk = FarmTaskLedger.captureMain(uid);
            boolean farmDrawOk = FarmTaskLedger.captureDraw(uid);
            boolean forestOk = ForestTaskLedger.capture(uid);
            boolean speciesOk = SpeciesTaskLedger.capture(uid);
            // 运动账本不参与自动全量同步：queryCoinTaskPanel 是已知高风险接口，
            // 只在用户手动刷新运动域时抓取，避免每天首次同步就撞 1009。
            Log.record("任务账本⏸运动账本不参与自动同步，请手动刷新运动域");
            boolean goldenOk;
            if (RiskGuard.isPaused("golden")) {
                goldenOk = true;
                Log.record("任务账本⏸金豆域保护性暂停，今日跳过金豆任务抓取");
            } else {
                goldenOk = GoldenBeansTaskLedger.capture(uid);
            }
            boolean protectStatusOk = ProtectStatus.capture(uid);
            boolean greenStatusOk = GreenActionStatus.capture(uid);
            boolean memberStatusOk = MemberStatus.capture(uid);
            boolean stallStatusOk = StallStatus.capture(uid);
            OrchardStatus.capture(uid);
            boolean orchardOk = OrchardTaskLedger.capture(uid);
            boolean orchardParkOk = OrchardParkTaskLedger.capture(uid);
            boolean orchardDrawOk = OrchardDrawTaskLedger.capture(uid);
            boolean orchardChallengeOk = OrchardChallengeTaskLedger.capture(uid);
            ForestParkTaskLedger.capture(uid);
            ForestPatrolTaskLedger.capture(uid);
            ForestBackpackStatus.capture(uid);
            ForestIpStatus.capture(uid);
            ForestStatus.capture(uid);
            SpeciesStatus.capture(uid);
            boolean oceanStatusOk = OceanStatus.capture(uid);
            boolean farmStatusOk = FarmStatus.capture(uid);
            FarmCompetitionStatus.capture(uid);
            FarmSunflowerStatus.capture(uid);
            Log.record(ok ? "任务账本📒今日海洋奖励基线已建立" : "任务账本⚠️海洋奖励基线建立失败，稍后重试");
            Log.record(fishOk ? "任务账本📒今日摸鱼任务基线已建立" : "任务账本⚠️摸鱼任务基线建立失败，稍后重试");
            Log.record(farmOk ? "任务账本📒今日庄园饲料任务基线已建立" : "任务账本⚠️庄园饲料任务基线建立失败，稍后重试");
            Log.record(farmDrawOk ? "任务账本📒今日庄园抽抽乐任务基线已建立" : "任务账本⚠️庄园抽抽乐基线建立失败，稍后重试");
            Log.record(forestOk ? "任务账本📒今日森林奖励任务基线已建立" : "任务账本⚠️森林奖励任务基线建立失败，稍后重试");
            Log.record(speciesOk ? "任务账本📒今日神奇物种任务基线已建立" : "任务账本⚠️神奇物种任务基线建立失败，稍后重试");
            Log.record(oceanStatusOk ? "任务账本📒今日海洋状态已建立" : "任务账本⚠️海洋状态建立失败，不影响任务账本");
            Log.record(farmStatusOk ? "任务账本📒今日庄园状态已建立" : "任务账本⚠️庄园状态建立失败，不影响任务账本");
            Log.record(protectStatusOk ? "任务账本📒今日生态保护状态已建立" : "任务账本⚠️生态保护状态建立失败，不影响任务账本");
            Log.record(greenStatusOk ? "任务账本📒今日绿色行动状态已建立" : "任务账本⚠️绿色行动状态建立失败，不影响任务账本");
            Log.record(memberStatusOk ? "任务账本📒今日会员状态已建立" : "任务账本⚠️会员状态建立失败，不影响任务账本");
            Log.record(stallStatusOk ? "任务账本📒今日新村状态已建立" : "任务账本⚠️新村状态建立失败，不影响任务账本");
            Log.record(goldenOk ? "任务账本📒今日金豆任务基线已建立" : "任务账本⚠️金豆任务基线建立失败，稍后重试");
            Log.record(orchardOk ? "任务账本📒今日果园肥料任务基线已建立" : "任务账本⚠️果园肥料任务基线建立失败，稍后重试");
            Log.record(orchardParkOk ? "任务账本📒今日果园乐园任务基线已建立" : "任务账本⚠️果园乐园任务基线建立失败，稍后重试");
            Log.record(orchardDrawOk ? "任务账本📒今日果园抽抽乐任务基线已建立" : "任务账本⚠️果园抽抽乐任务基线建立失败，稍后重试");
            Log.record(orchardChallengeOk ? "任务账本📒今日果园限时挑战基线已建立" : "任务账本⚠️果园限时挑战基线建立失败，稍后重试");
            TaskLedgerStore.writeDailyReport(uid, TaskLedgerStore.today());
            scheduleStatusRetryIfNeeded(context, uid);
            boolean allOk = ok && fishOk && farmOk && farmDrawOk && forestOk && speciesOk && goldenOk
                    && orchardOk && orchardParkOk && orchardDrawOk && orchardChallengeOk;
            if (allOk) {
                clearRetry(context);
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(KEY_FULL_SYNC_DAY, TaskLedgerStore.today()).apply();
                Log.record("任务账本✅今日全量同步完成");
                scheduleNext(context);
            } else {
                scheduleRetry(context);
            }
        } catch (Throwable t) {
            Log.err(TAG, "runCapture err:", t);
            scheduleRetry(context);
        }
    }

    private static final String[] STATUS_KEYS = {
            "ocean", "farm", "forest", "species", "orchard", "protect", "green", "member", "stall"
    };

    private static boolean captureStatusByName(String uid, String name) {
        switch (name) {
            case "ocean":
                return OceanStatus.capture(uid);
            case "farm":
                return FarmStatus.capture(uid);
            case "forest":
                return ForestStatus.capture(uid);
            case "species":
                return SpeciesStatus.capture(uid);
            case "orchard":
                return OrchardStatus.capture(uid);
            case "protect":
                return ProtectStatus.capture(uid);
            case "green":
                return GreenActionStatus.capture(uid);
            case "member":
                return MemberStatus.capture(uid);
            case "stall":
                return StallStatus.capture(uid);
            default:
                return true;
        }
    }

    private static List<String> missingStatuses(String uid) {
        List<String> missing = new ArrayList<>();
        for (String name : STATUS_KEYS) {
            boolean present;
            switch (name) {
                case "ocean":
                    present = OceanStatus.loadToday(uid) != null;
                    break;
                case "farm":
                    present = FarmStatus.loadToday(uid) != null;
                    break;
                case "forest":
                    present = ForestStatus.loadToday(uid) != null;
                    break;
                case "species":
                    present = SpeciesStatus.loadToday(uid) != null;
                    break;
                case "orchard":
                    present = OrchardStatus.loadToday(uid) != null;
                    break;
                case "protect":
                    present = ProtectStatus.loadToday(uid) != null;
                    break;
                case "green":
                    present = GreenActionStatus.loadToday(uid) != null;
                    break;
                case "member":
                    present = MemberStatus.loadToday(uid) != null;
                    break;
                case "stall":
                    present = StallStatus.loadToday(uid) != null;
                    break;
                default:
                    present = true;
                    break;
            }
            if (!present) {
                missing.add(name);
            }
        }
        return missing;
    }

    private static String joinNames(List<String> names) {
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(name);
        }
        return builder.toString();
    }

    public static String getStatusRetryDay(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_STATUS_RETRY_DAY, "");
    }

    public static int getStatusRetryCount(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_STATUS_RETRY, 0);
    }

    /** 任务账本成功但首页状态缺失时，只补状态，绝不重跑任务账本。 */
    private static void scheduleStatusRetryIfNeeded(Context context, String uid) {
        try {
            List<String> missing = missingStatuses(uid);
            if (missing.isEmpty()) {
                clearStatusRetry(context);
                return;
            }
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String day = TaskLedgerStore.today();
            int count = day.equals(prefs.getString(KEY_STATUS_RETRY_DAY, ""))
                    ? prefs.getInt(KEY_STATUS_RETRY, 0) : 0;
            if (count >= 2) {
                Log.record("任务账本⚠️状态补抓两次仍缺：" + joinNames(missing) + "，等下次手动刷新");
                EventLog.ledger("status_retry_giveup", "missing", joinNames(missing));
                return;
            }
            long delayMinutes = count == 0 ? 10L : 45L;
            prefs.edit()
                    .putString(KEY_STATUS_RETRY_DAY, day)
                    .putInt(KEY_STATUS_RETRY, count + 1)
                    .apply();
            Log.record("任务账本🕓状态缺失(" + joinNames(missing) + ")，约 "
                    + delayMinutes + " 分钟后只补状态");
            EventLog.ledger("status_retry_schedule", "missing", joinNames(missing),
                    "attempt", String.valueOf(count + 1));
            scheduleAt(context, System.currentTimeMillis() + delayMinutes * 60_000L, false, true);
        } catch (Throwable t) {
            Log.err(TAG, "scheduleStatusRetry err:", t);
        }
    }

    private static void clearStatusRetry(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_STATUS_RETRY)
                .remove(KEY_STATUS_RETRY_DAY)
                .apply();
    }

    private static void runStatusOnly(Context context) {
        try {
            if (RiskGuard.isVerificationRequired()) {
                Log.record("任务账本⏸等待支付宝验证，跳过状态补抓");
                return;
            }
            String uid = UserIdMap.getCurrentUid();
            if (uid == null || uid.isEmpty()) {
                scheduleAt(context, System.currentTimeMillis() + 5L * 60_000L, false, true);
                return;
            }
            List<String> missing = missingStatuses(uid);
            if (missing.isEmpty()) {
                clearStatusRetry(context);
                Log.record("任务账本✅状态已齐全，无需补抓");
                return;
            }
            List<String> failed = new ArrayList<>();
            for (String name : missing) {
                if (!captureStatusByName(uid, name)) {
                    failed.add(name);
                }
            }
            List<String> still = missingStatuses(uid);
            if (still.isEmpty()) {
                clearStatusRetry(context);
                Log.record("任务账本✅状态补抓完成：" + joinNames(missing));
                EventLog.ledger("status_retry_ok", "captured", joinNames(missing));
            } else {
                Log.record("任务账本⚠️状态补抓仍缺：" + joinNames(still)
                        + (failed.isEmpty() ? "" : "（失败 " + joinNames(failed) + "）"));
                EventLog.ledger("status_retry_partial", "captured", joinNames(missing),
                        "missing", joinNames(still));
                scheduleStatusRetryIfNeeded(context, uid);
            }
        } catch (Throwable t) {
            Log.err(TAG, "runStatusOnly err:", t);
        }
    }

    private static void scheduleRetry(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt(KEY_RETRY, 0) + 1;
        prefs.edit().putInt(KEY_RETRY, count).apply();
        long delayMinutes;
        if (count <= 1) {
            delayMinutes = 5;      // 00:10
        } else if (count == 2) {
            delayMinutes = 10;     // 00:20
        } else if (count == 3) {
            delayMinutes = 15;     // 00:35
        } else {
            delayMinutes = 60;     // 之后每小时
        }
        Log.record("任务账本🕓第" + count + "次补建，约 " + delayMinutes + " 分钟后");
        scheduleAt(context, System.currentTimeMillis() + delayMinutes * 60_000L, true);
    }

    private static void clearRetry(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_RETRY).apply();
    }

    private static long nextMidnight() {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 5);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        return next.getTimeInMillis();
    }

    private static void scheduleAt(Context context, long atMillis, boolean retry) {
        scheduleAt(context, atMillis, retry, false);
    }

    private static void scheduleAt(Context context, long atMillis, boolean retry, boolean statusOnly) {
        try {
            AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (manager == null) {
                return;
            }
            Intent intent = new Intent(ACTION);
            intent.setPackage(context.getPackageName());
            intent.putExtra("retry", retry);
            intent.putExtra(EXTRA_STATUS_ONLY, statusOnly);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context, statusOnly ? STATUS_REQUEST_CODE : REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            boolean exact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                exact = false;
            }
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending);
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending);
            }
        } catch (Throwable t) {
            Log.err(TAG, "scheduleAt err:", t);
        }
    }
}
