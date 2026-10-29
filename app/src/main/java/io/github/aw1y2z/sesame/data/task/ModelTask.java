package io.github.aw1y2z.sesame.data.task;

import static io.github.aw1y2z.sesame.model.normal.base.BaseModel.taskRpcRequest;

import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.Status;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import lombok.Getter;
import io.github.aw1y2z.sesame.data.ConfigV2;
import io.github.aw1y2z.sesame.data.Model;
import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.ModelType;
import io.github.aw1y2z.sesame.model.normal.base.BaseModel;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.NotificationUtil;
import io.github.aw1y2z.sesame.util.RiskGuard;
import io.github.aw1y2z.sesame.util.RunControl;
import io.github.aw1y2z.sesame.data.plan.PlanContext;
import io.github.aw1y2z.sesame.data.plan.PlanTargets;
import io.github.aw1y2z.sesame.data.plan.PlanRunResult;
import io.github.aw1y2z.sesame.util.RunGeneration;
import io.github.aw1y2z.sesame.util.RunScope;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.TaskCancelledException;
import io.github.aw1y2z.sesame.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class ModelTask extends Model {

    private static final long PLAN_TASK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(20);
    /** 3.9.0 短跑：域级任务默认 20 分钟，超出部分转手动/待操作，不再硬追到 45 分钟。 */
    private static final long DOMAIN_TASK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(20);
    /** 森林日常短跑 25 分钟；寻宝单列 30 分钟（实测约 29 分钟）。 */
    private static final long FOREST_DAILY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(25);
    private static final long FOREST_HUNT_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(30);
    /** 金豆只保留站内签到/任务，15 分钟足够；游戏类转手动。 */
    private static final long GOLDEN_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(15);
    /** 拆分后的短目标：给 6/10 分钟“活跃时间”预算，冻结时间会被 waitForPlanTask 自动顺延。 */
    private static final long SHORT_TASK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(6);
    private static final long MEDIUM_TASK_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(10);
    /** 超时停止后给网络请求收尾留时间；游戏上报最坏 10s 连接 + 15s 读 + 3s 间隔，5 秒会让下一目标与它重叠。 */
    private static final long PLAN_STOP_GRACE_MS = 60_000L;

    private static final Map<ModelTask, Thread> MAIN_TASK_MAP = new ConcurrentHashMap<>();

    /** 本模型的代际编号：stopTask 递增，旧代在下一个检查点作废退出，无需 join 阻塞调用方 */
    private volatile long generation = 0;

    /** 计划目标：PlanContext 是线程私有的，必须显式带进模型线程，子目标才会走对应分支 */
    private volatile String runPlanTarget;
    /** 本轮是否手动发起：与 target 一样需要带到模型线程，供任务侧区分手动直达与自动计划 */
    private volatile boolean runPlanManual;
    /** 按类型执行：同样要带到模型线程，任务循环才能按类放行。 */
    private volatile io.github.aw1y2z.sesame.data.task.TaskFilter runPlanFilter;

    /** 执行槽（只限本实例；重载后是新对象，跨实例靠代际令牌在检查点退出）：抢不到即放弃，不用实例锁 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 最近一次启动被拒绝的原因，供计划页/日志解释「为什么没跑」 */
    private volatile String lastStartReject = "";
    private volatile long lastRunStartedAt;
    private volatile long lastRunEndedAt;
    private volatile boolean lastRunFinished;
    private volatile boolean lastRunSuccess;
    private volatile String lastRunError = "";
    private volatile int lastRunActions;

    private static final int MODEL_COUNT = Math.max(getModelArray().length, 1);

    /**
     * 主任务线程池：每个模型占一条线程（core = 模型数），上限 2 倍模型数，饱和后回退到调用线程执行。
     * <p>SynchronousQueue 不排队，故不存在「撤销已提交但尚未开始的那一轮」这种操作。
     */
    private static final ThreadPoolExecutor MAIN_THREAD_POOL = new ThreadPoolExecutor(MODEL_COUNT, MODEL_COUNT * 2, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), new ThreadPoolExecutor.CallerRunsPolicy());

    private final Map<String, ChildModelTask> childTaskMap = new ConcurrentHashMap<>();

    private ChildTaskExecutor childTaskExecutor;

    @Getter
    private final Runnable mainRunnable = new Runnable() {

        private final ModelTask task = ModelTask.this;

        @Override
        public void run() {
            // 执行槽已由 startTask 抢住；此处只取快照，stopTask 递增后本代作废
            long myGen = task.generation;
            task.lastRunStartedAt = System.currentTimeMillis();
            task.lastRunEndedAt = 0L;
            task.lastRunFinished = false;
            task.lastRunSuccess = false;
            task.lastRunError = "";
            task.lastRunActions = 0;
            MAIN_TASK_MAP.put(task, Thread.currentThread());
            // 绑定本代令牌：本线程的 sleep / RPC 入口据此感知作废
            RunGeneration prevGen = RunGeneration.bind(myGen, () -> task.generation);
            // 3.0：把当前模块/页面作用域带到线程里，风控和写闸门按目标判断，不再全局一刀切。
            String runScope = RiskGuard.scopeForModel(task.getClass().getSimpleName());
            if (runScope == null) {
                // 森林没有单一模型 scope：计划目标优先，否则用域级 forest，
                // 不能回退到全局 currentTarget——并发轮询时会被别的计划目标带偏。
                String planTarget = task.runPlanTarget;
                if (planTarget == null || planTarget.isEmpty()) {
                    planTarget = PlanContext.get();
                }
                runScope = planTarget == null || planTarget.isEmpty()
                        ? RiskGuard.SCOPE_FOREST : planTarget;
            }
            RunScope.bind(runScope);
            if (!task.isSync()) {
                PlanContext.set(task.runPlanTarget);
                PlanContext.setManual(task.runPlanManual);
                PlanContext.setFilter(task.runPlanFilter);
            }
            // 静音计数分层：记下上层残留，本轮只统计自己这段
            int droppedPrev = Log.takeDroppedStaleLogCount();
            // 与执行槽配对：只有真正拿到槽的线程才计入 runningCount；同时驱动状态栏显示当前在跑的模块
            NotificationUtil.trackTaskStart(task.getName());
            ModelGroup group = task.getGroup();
            String prevModule = Log.beginModule(group == null ? null : group.getCode());
            Log.record("执行开始-" + task.getName());
            Log.startModuleLogCount();
            Log.resetUnresolved();
            try {
                task.run();
                task.lastRunSuccess = true;
            } catch (TaskCancelledException e) {
                task.lastRunError = "任务被停止";
                Log.record(task.getName() + "⏹代际已作废，本轮提前结束");
            } catch (Exception e) {
                task.lastRunError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Log.printStackTrace(e);
            } finally {
                // 任何收尾日志/通知异常都不能跳过执行槽释放，否则计划会一直等到超时
                try {
                // 作废期间被静音的错误日志条数一并报告，避免真故障无声丢失
                int dropped = Log.takeDroppedStaleLogCount();
                if (dropped > 0) {
                    Log.record(task.getName() + "⏹代际已作废，其间静音错误日志 " + dropped + " 条");
                }
                Log.restoreDroppedStaleLogCount(droppedPrev);
                // 还原而非清空：isSync 会在同一线程嵌套起跑，清掉会抹掉外层令牌
                RunGeneration.restore(prevGen);
                String summaryScope = RunScope.current();
                boolean riskStopped = RiskGuard.isVerificationRequired()
                        || RiskGuard.isGlobalPaused()
                        || (summaryScope != null && !summaryScope.isEmpty() && RiskGuard.isPaused(summaryScope));
                RunScope.clear();
                int actions = Log.stopModuleLogCount();
                task.lastRunActions = actions;
                // 模块收尾汇总：风控暂停优先于“完成/无操作”，避免把中止误写成完成
                boolean planTimedOut = PlanContext.isTimedOut()
                        && task.runPlanTarget != null && !task.runPlanTarget.isEmpty();
                Log.moduleSummary(task.getName()
                        + (riskStopped ? "⏸风控暂停，本轮中止"
                        : (planTimedOut ? "⏹计划超时，本轮中止"
                        : (Log.isUnresolved() ? "⏳仍有待完成或待领取任务"
                        : (actions == 0 ? "✅本轮无操作" : "✅本轮完成")))));
                Log.record("执行结束-" + task.getName());
                Log.endModule(prevModule);
                } catch (Throwable t) {
                    Log.printStackTrace(t);
                } finally {
                    if (!task.isSync()) {
                        PlanContext.clear();
                    }
                    // 身份化移除：只删本线程登记的那条，避免旧代收尾误删新一代条目
                    MAIN_TASK_MAP.remove(task, Thread.currentThread());
                    task.running.set(false);
                    task.lastRunFinished = true;
                    task.lastRunEndedAt = System.currentTimeMillis();
                    try {
                        NotificationUtil.trackTaskEnd(task.getName());
                    } catch (Throwable ignored) {
                    }
                }
            }
        }

    };

    public ModelTask() {
    }

    @Override
    public final void prepare() {
        childTaskExecutor = newTimedTaskExecutor();
    }

    public String getId() {
        return toString();
    }

    public ModelType getType() {
        return ModelType.TASK;
    }

    public abstract String getName();

    public abstract ModelFields getFields();

    public abstract Boolean check();

    public Boolean isSync() {
        return false;
    }

    public abstract void run();

    public Boolean hasChildTask(String childId) {
        return childTaskMap.containsKey(childId);
    }

    public ChildModelTask getChildTask(String childId) {
        return childTaskMap.get(childId);
    }

    public Boolean addChildTask(ChildModelTask childTask) {
        String childId = childTask.getId();
        childTask.modelTask = this;
        // 提交放锁外：池满时 CallerRunsPolicy 会让提交线程就地跑完整段子任务，放进 compute 会卡住整桶
        if (!childTaskExecutor.addChildTask(childTask)) {
            return false;
        }
        ChildModelTask oldTask = childTaskMap.put(childId, childTask);
        if (oldTask != null) {
            oldTask.cancel();
        }
        return true;
    }

    public void removeChildTask(String childId) {
        ChildModelTask childTask = childTaskMap.remove(childId);
        if (childTask != null) {
            // 通知执行器放锁外，别在 CHM 桶锁内调用可能阻塞的代码
            childTaskExecutor.removeChildTask(childTask);
        }
    }

    public Integer countChildTask() {
        return childTaskMap.size();
    }

    public Boolean startTask() {
        return startTask(false);
    }

    /**
     * 启动一轮。不加 {@code synchronized}：互斥由 {@code running} 执行槽（CAS）保证，被挡住的只是执行
     * 线程自己，不像实例锁那样把整轮时间压给 {@link #stopTask()} 的调用方（宿主 onDestroy 在主线程）。
     * <p>抢槽在本方法内完成，故返回值如实反映「是否真的开始跑」：抢不到返回 false。
     */
    public Boolean startTask(Boolean force) {
        if (force) {
            stopTask();
        }
        lastStartReject = "";
        if (RunControl.isStopRequested()) {
            lastStartReject = "用户已停止";
            Log.record(getName() + "⏭用户已停止，本轮跳过");
            return false;
        }
        if (RiskGuard.isVerificationRequired()) {
            lastStartReject = "需要验证";
            Log.record(getName() + "⏭等待支付宝验证，本轮跳过");
            return false;
        }
        if (RiskGuard.isGlobalPaused()) {
            lastStartReject = "账号风控暂停";
            Log.record(getName() + "⏭账号风控保护暂停中，本轮跳过");
            return false;
        }
        String riskScope = RiskGuard.scopeForModel(getClass().getSimpleName());
        if (riskScope == null) {
            String planTarget = PlanContext.get();
            riskScope = planTarget == null || planTarget.isEmpty()
                    ? RiskGuard.SCOPE_FOREST : planTarget;
        }
        if (riskScope != null && RiskGuard.isPaused(riskScope)) {
            lastStartReject = "风控域暂停";
            Log.record(getName() + "⏭风控域[" + RiskGuard.labelOf(riskScope) + "]暂停中，本轮跳过");
            return false;
        }
        if (!isEnable()) {
            lastStartReject = "模块未开启";
            return false;
        }
        try {
            if (!check()) {
                // 禁用/条件不满足时原先是完全静默的，用户只能看到「没有日志」。
                // 至少留一条可定位的跳过原因，日志开关关闭时也不额外落盘。
                lastStartReject = "条件不满足";
                Log.record(getName() + "⏭本轮条件不满足，已跳过");
                return false;
            }
        } catch (Exception e) {
            lastStartReject = "检查异常";
            Log.printStackTrace(e);
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            lastStartReject = "上一轮尚未结束";
            Log.record(getName() + "⏭本实例上一代未退出，本轮跳过");
            return false;
        }
        try {
            runPlanTarget = PlanContext.get();
            runPlanManual = PlanContext.isManual();
            runPlanFilter = PlanContext.getFilter();
            if (isSync()) {
                mainRunnable.run();
            } else {
                try {
                    MAIN_THREAD_POOL.execute(mainRunnable);
                } catch (Throwable t) {
                    // 没能提交就要归还槽，否则该模型再也起不来
                    running.set(false);
                    lastStartReject = "启动异常";
                    throw t;
                }
            }
            return true;
        } catch (Exception e) {
            lastStartReject = "启动异常";
            Log.printStackTrace(e);
            return false;
        }
    }

    public String getLastStartReject() {
        return lastStartReject;
    }

    public boolean isLastRunFinished() {
        return lastRunFinished;
    }

    public boolean isLastRunSuccess() {
        return lastRunSuccess;
    }

    public String getLastRunError() {
        return lastRunError;
    }

    public int getLastRunActions() {
        return lastRunActions;
    }

    public long getLastRunStartedAt() {
        return lastRunStartedAt;
    }

    public long getLastRunEndedAt() {
        return lastRunEndedAt;
    }

    public synchronized void stopTask() {
        // 递增代际：旧代在下一个检查点自行退出，无需 join 阻塞调用方。
        // 令牌只覆盖模型主线程；子任务线程未绑定，其取消仍依赖 cancel() 与 clearAllChildTask()
        generation++;
        for (ChildModelTask childModelTask : childTaskMap.values()) {
            try {
                childModelTask.cancel();
            } catch (Exception e) {
                Log.printStackTrace(e);
            }
        }
        if (childTaskExecutor != null) {
            childTaskExecutor.clearAllChildTask();
        }
        childTaskMap.clear();
        // 身份化移除：只删自己登记的那条线程，避免误删新一代条目
        Thread running = MAIN_TASK_MAP.get(this);
        if (running != null) {
            MAIN_TASK_MAP.remove(this, running);
        }
    }

    public static void startAllTask() {
        startAllTask(false);
    }

    public static void startAllTask(Boolean force) {
        // 开跑前对齐磁盘配置，广播只是加速器
        ConfigV2.reloadIfDiskChanged(UserIdMap.getCurrentUid());
        if (RiskGuard.isGlobalPaused()) {
            Log.record("风控保护🛡️账号级风控暂停中，跳过本轮；请到首页查看并手动恢复");
            return;
        }
        // 整轮开始：清零本轮收取能量。单分组执行（startGroupTask）不算整轮，故不在此清零
        NotificationUtil.startRound();
        //自动触发备份配置文件
        if (!Status.hasFlagToday("Config::backup")) {
            FileUtil.backupConfigV2WithRolling(UserIdMap.getCurrentUid());
            Status.flagToday("Config::backup");
        }
        //执行BaseModel中自定义执行请求
        taskRpcRequest();
        List<String> disabledModels = new ArrayList<>();
        for (Model model : getModelArray()) {
            // 已停止就不再启动后续模型，否则 MAIN_TASK 会把整轮跑完
            if (RunGeneration.isStale()) {
                Log.record("⏹已停止，本轮不再启动后续任务");
                return;
            }
            if (model != null) {
                if (ModelType.TASK == model.getType()) {
                    if (!model.isEnable()) {
                        disabledModels.add(model.getName());
                        continue;
                    }
                    if (((ModelTask) model).startTask(force)) {
                        // 带代际检查的睡眠：停止时在下一个边界结束整轮（未绑定时同 Thread.sleep）
                        try {
                            TimeUtil.sleep(750);
                        } catch (TaskCancelledException e) {
                            return;
                        }
                    }
                }
            }
        }
        if (!disabledModels.isEmpty()) {
            // 每轮只汇总一行，避免把逐个模块的跳过日志刷满运行日志
            Log.record("本轮未开启模块: " + String.join("、", disabledModels));
        }
    }

    /**
     * 只执行指定分组下的任务（{@code groupCode} 取 {@link ModelGroup#getCode()}）。
     * <p>配置页的"执行"按钮必须通过广播交给<strong>注入进程</strong>跑：模块 App 自己的进程没有
     * libxposed 类（{@code ApplicationHook}/{@code hook.Toast}/{@code NotificationUtil} 一碰就是
     * NoClassDefFoundError），而且那是个 UI 进程，把任务循环压在主线程上会直接卡死界面。
     *
     * @return 实际触发的任务数
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 只跑一个计划目标，并等待本轮真正结束（异步模型也要等），供计划串行队列使用。
     * 森林的 collect/hunt 通过 PlanContext 只跑对应阶段；返回值区分未启动、超时和真实失败。
     */
    /**
     * 这个计划目标对应的模块，用户是否真的开启了。
     * <p>兜底要「所有开了的模块都检查一遍」，判断依据只能是模块 enable，不能是计划里有没有写。
     */
    public static boolean isPlanTargetEnabled(String target) {
        if (target == null || target.isEmpty() || target.endsWith(".external")) {
            return false;
        }
        for (Model model : getModelArray()) {
            if (model == null || ModelType.TASK != model.getType() || !(model instanceof ModelTask)) {
                continue;
            }
            String simpleName = model.getClass().getSimpleName();
            boolean matched = target.startsWith("forest.")
                    ? "AntForestV2".equals(simpleName)
                    : target.startsWith("ocean.")
                    ? "AntOcean".equals(simpleName)
                    : target.startsWith("farm.")
                    ? "AntFarm".equals(simpleName)
                    : target.startsWith("species.")
                    ? "AntDodo".equals(simpleName)
                    : target.startsWith("orchard.")
                    ? "AntOrchard".equals(simpleName)
                    : target.equals(RiskGuard.scopeForModel(simpleName));
            if (matched) {
                return model.isEnable();
            }
        }
        return false;
    }

    /** 某个计划目标对应的模型是否还在跑；超时/补跑退避用，不触发新的执行。 */
    public static boolean isPlanTargetRunning(String target) {
        if (target == null || target.isEmpty() || target.endsWith(".external")) {
            return false;
        }
        for (Model model : getModelArray()) {
            if (model == null || ModelType.TASK != model.getType() || !(model instanceof ModelTask)) {
                continue;
            }
            String simpleName = model.getClass().getSimpleName();
            boolean matched = target.startsWith("forest.")
                    ? "AntForestV2".equals(simpleName)
                    : target.startsWith("ocean.")
                    ? "AntOcean".equals(simpleName)
                    : target.startsWith("farm.")
                    ? "AntFarm".equals(simpleName)
                    : target.startsWith("species.")
                    ? "AntDodo".equals(simpleName)
                    : target.startsWith("orchard.")
                    ? "AntOrchard".equals(simpleName)
                    : target.equals(RiskGuard.scopeForModel(simpleName));
            if (matched) {
                return ((ModelTask) model).isRunning();
            }
        }
        return false;
    }

    public static PlanRunResult startPlanTarget(String target) {
        PlanRunResult result = new PlanRunResult();
        result.target = target;
        result.startedAt = System.currentTimeMillis();
        if (target == null || target.isEmpty()) {
            result.reason = "目标为空";
            return result.finish();
        }
        if (target.startsWith("forest.")) {
            ModelTask forest = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "AntForestV2".equals(model.getClass().getSimpleName())) {
                    forest = (ModelTask) model;
                    break;
                }
            }
            if (forest == null) {
                result.reason = "未找到森林任务";
                return result.finish();
            }
            final ModelTask forestTask = forest;
            PlanContext.set(target);
            // 森林日常 25 分钟、寻宝 30 分钟、收能量/IP 20 分钟；短跑优先，剩余转手动。
            boolean forestDaily = PlanTargets.FOREST_DAILY.equals(target);
            boolean forestHunt = PlanTargets.FOREST_HUNT.equals(target);
            long timeoutMs = forestDaily ? FOREST_DAILY_TIMEOUT_MS
                    : forestHunt ? FOREST_HUNT_TIMEOUT_MS : timeoutForTarget(target);
            // 森林是同步模型：startTask 在调用线程里一直跑到整轮结束，waitForPlanTask 的超时永远等不到。
            // 用独立看门狗守墙钟，超时后递增代际让森林在下一个检查点退出。
            AtomicBoolean timedOut = new AtomicBoolean(false);
            Thread watchdog = new Thread(() -> {
                long deadline = System.currentTimeMillis() + timeoutMs;
                long lastLoopAt = System.currentTimeMillis();
                try {
                    while (forestTask.isRunning() && System.currentTimeMillis() < deadline) {
                        TimeUnit.MILLISECONDS.sleep(1000L);
                        long now = System.currentTimeMillis();
                        long gap = now - lastLoopAt;
                        lastLoopAt = now;
                        // 进程被冻结的时间不计入计划预算；否则夜间一次挂起就会误判超时。
                        if (gap > 60_000L) {
                            deadline += gap;
                        }
                    }
                } catch (InterruptedException ignored) {
                    return;
                }
                if (forestTask.isRunning()) {
                    timedOut.set(true);
                    Log.record(forestTask.getName() + "⏹计划超时，已请求停止");
                    forestTask.stopTask();
                }
            }, "Medusa-ForestPlanTimeout");
            watchdog.setDaemon(true);
            try {
                watchdog.start();
                result.started = forestTask.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + forestTask.getLastStartReject();
                    return result.finish();
                }
                result.finished = !timedOut.get() && !forestTask.isRunning();
                applyTaskOutcome(result, forestTask);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = forestDaily ? "执行超时（25分钟）"
                            : forestHunt ? "执行超时（30分钟）" : "执行超时（20分钟）";
                }
                return result.finish();
            } finally {
                watchdog.interrupt();
                PlanContext.clear();
            }
        }
        if (target.startsWith("ocean.")) {
            ModelTask ocean = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "AntOcean".equals(model.getClass().getSimpleName())) {
                    ocean = (ModelTask) model;
                    break;
                }
            }
            if (ocean == null) {
                result.reason = "未找到海洋任务";
                return result.finish();
            }
            PlanContext.set(target);
            try {
                result.started = ocean.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + ocean.getLastStartReject();
                    return result.finish();
                }
                result.finished = waitForPlanTask(ocean, timeoutForTarget(target));
                applyTaskOutcome(result, ocean);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（20分钟）";
                }
                return result.finish();
            } finally {
                PlanContext.clear();
            }
        }
        if (target.startsWith("farm.")) {
            ModelTask farm = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "AntFarm".equals(model.getClass().getSimpleName())) {
                    farm = (ModelTask) model;
                    break;
                }
            }
            if (farm == null) {
                result.reason = "未找到庄园任务";
                return result.finish();
            }
            PlanContext.set(target);
            try {
                result.started = farm.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + farm.getLastStartReject();
                    return result.finish();
                }
                result.finished = waitForPlanTask(farm, timeoutForTarget(target));
                applyTaskOutcome(result, farm);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（20分钟）";
                }
                return result.finish();
            } finally {
                PlanContext.clear();
            }
        }
        if (target.startsWith("species.")) {
            ModelTask species = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "AntDodo".equals(model.getClass().getSimpleName())) {
                    species = (ModelTask) model;
                    break;
                }
            }
            if (species == null) {
                result.reason = "未找到物种任务";
                return result.finish();
            }
            PlanContext.set(target);
            try {
                result.started = species.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + species.getLastStartReject();
                    return result.finish();
                }
                result.finished = waitForPlanTask(species, timeoutForTarget(target));
                applyTaskOutcome(result, species);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（20分钟）";
                }
                return result.finish();
            } finally {
                PlanContext.clear();
            }
        }
        if (target.equals(PlanTargets.ORCHARD) || target.startsWith("orchard.")) {
            ModelTask orchard = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "AntOrchard".equals(model.getClass().getSimpleName())) {
                    orchard = (ModelTask) model;
                    break;
                }
            }
            if (orchard == null) {
                result.reason = "未找到果园任务";
                return result.finish();
            }
            PlanContext.set(target);
            try {
                result.started = orchard.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + orchard.getLastStartReject();
                    return result.finish();
                }
                result.finished = waitForPlanTask(orchard, timeoutForTarget(target));
                applyTaskOutcome(result, orchard);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（20分钟）";
                }
                return result.finish();
            } finally {
                PlanContext.clear();
            }
        }
        if (PlanTargets.KINETIC.equals(target)) {
            ModelTask sports = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "AntSports".equals(model.getClass().getSimpleName())) {
                    sports = (ModelTask) model;
                    break;
                }
            }
            if (sports == null) {
                result.reason = "未找到运动任务";
                return result.finish();
            }
            PlanContext.set(target);
            try {
                result.started = sports.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + sports.getLastStartReject();
                    return result.finish();
                }
                result.finished = waitForPlanTask(sports, PLAN_TASK_TIMEOUT_MS);
                applyTaskOutcome(result, sports);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（20分钟）";
                }
                return result.finish();
            } finally {
                PlanContext.clear();
            }
        }
        if (PlanTargets.GOLDEN.equals(target)) {
            ModelTask golden = null;
            for (Model model : getModelArray()) {
                if (model instanceof ModelTask && "goldenbeans".equals(model.getClass().getSimpleName())) {
                    golden = (ModelTask) model;
                    break;
                }
            }
            if (golden == null) {
                result.reason = "未找到金豆任务";
                return result.finish();
            }
            PlanContext.set(target);
            try {
                result.started = golden.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + golden.getLastStartReject();
                    return result.finish();
                }
                result.finished = waitForPlanTask(golden, GOLDEN_TIMEOUT_MS);
                applyTaskOutcome(result, golden);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（15分钟）";
                }
                return result.finish();
            } finally {
                PlanContext.clear();
            }
        }
        for (Model model : getModelArray()) {
            if (model == null || ModelType.TASK != model.getType() || !(model instanceof ModelTask)) {
                continue;
            }
            String scope = RiskGuard.scopeForModel(model.getClass().getSimpleName());
            if (target.equals(scope)) {
                ModelTask task = (ModelTask) model;
                result.started = task.startTask(false);
                if (!result.started) {
                    result.reason = "未启动：" + task.getLastStartReject();
                    return result.finish();
                }
                // 拆分后的短目标给 6/10 分钟活跃预算；重目标仍按域级 20 分钟。
                long timeoutMs = timeoutForTarget(target);
                result.finished = waitForPlanTask(task, timeoutMs);
                applyTaskOutcome(result, task);
                if (!result.finished && result.reason.isEmpty()) {
                    result.reason = "执行超时（" + TimeUnit.MILLISECONDS.toMinutes(timeoutMs) + "分钟）";
                }
                return result.finish();
            }
        }
        result.reason = "未找到执行模块";
        return result.finish();
    }

    /** 拆分后的目标按“活跃时间”预算；进程被冻结的墙钟时间由 waitForPlanTask 自动顺延。 */
    private static long timeoutForTarget(String target) {
        if (target == null) {
            return DOMAIN_TASK_TIMEOUT_MS;
        }
        switch (target) {
            case PlanTargets.FARM_DAILY:
            case PlanTargets.FARM_KITCHEN:
            case PlanTargets.OCEAN_WELCOME:
                return SHORT_TASK_TIMEOUT_MS;
            case PlanTargets.FARM_TASK:
            case PlanTargets.FARM_DRAW:
            case PlanTargets.OCEAN_REWARD:
            case PlanTargets.OCEAN_FISH:
            case PlanTargets.ORCHARD_FERTILIZER:
            case PlanTargets.ORCHARD_SPREAD:
                return MEDIUM_TASK_TIMEOUT_MS;
            case PlanTargets.GOLDEN:
                return GOLDEN_TIMEOUT_MS;
            case PlanTargets.FOREST_DAILY:
                return FOREST_DAILY_TIMEOUT_MS;
            case PlanTargets.FOREST_REWARD:
            case PlanTargets.FOREST_GAME:
            case PlanTargets.FOREST_PATROL:
                return MEDIUM_TASK_TIMEOUT_MS;
            case PlanTargets.FOREST_PARK:
                return SHORT_TASK_TIMEOUT_MS;
            case PlanTargets.FOREST_READ:
                return TimeUnit.MINUTES.toMillis(12);
            default:
                return DOMAIN_TASK_TIMEOUT_MS;
        }
    }

    /** 把任务线程的真实结束状态写入计划结果；森林阶段结果优先。 */
    private static void applyTaskOutcome(PlanRunResult result, ModelTask task) {
        result.actions = task.getLastRunActions();
        String phaseResult = PlanContext.getResult();
        String phaseDetail = PlanContext.getDetail();
        if (PlanContext.RESULT_FAILED.equals(phaseResult)) {
            result.reason = phaseDetail == null || phaseDetail.isEmpty() ? "阶段执行失败" : phaseDetail;
            return;
        }
        if (PlanContext.RESULT_SKIPPED.equals(phaseResult)) {
            result.reason = phaseDetail == null || phaseDetail.isEmpty() ? "阶段被跳过" : phaseDetail;
            return;
        }
        if (result.finished && !task.isLastRunSuccess()) {
            String error = task.getLastRunError();
            result.reason = error == null || error.isEmpty() ? "执行异常" : error;
            return;
        }
        result.success = result.finished;
    }

    private static boolean waitForPlanTask(ModelTask task, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastLoopAt = System.currentTimeMillis();
        boolean riskStopped = false;
        String target = PlanContext.get();
        while (task.isRunning() && System.currentTimeMillis() < deadline) {
            // 风控/用户停止要立刻断开执行槽，不能等 20–45 分钟计划超时。
            if (RunControl.isStopRequested()
                    || RiskGuard.isVerificationRequired()
                    || RiskGuard.isGlobalPaused()
                    || (target != null && !target.isEmpty() && RiskGuard.isTargetPaused(target))) {
                riskStopped = true;
                break;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            long now = System.currentTimeMillis();
            long gap = now - lastLoopAt;
            lastLoopAt = now;
            // 本循环只做 500ms 睡眠；单次跨过 60 秒说明进程被系统冻结，不应算进计划预算。
            if (gap > 60_000L) {
                deadline += gap;
            }
        }
        if (!task.isRunning()) {
            return true;
        }
        // 超时只返回 false 不够：模型槽还被占着，补跑会每 5 秒重试并记一条 SKIPPED。
        // 主动递增代际并取消子任务，再给一个很短的有界等待；退不退出都不阻塞计划回执。
        Log.record(task.getName() + (riskStopped ? "⏹风控暂停，已请求停止" : "⏹计划超时，已请求停止"));
        task.stopTask();
        long graceDeadline = System.currentTimeMillis() + PLAN_STOP_GRACE_MS;
        while (task.isRunning() && System.currentTimeMillis() < graceDeadline) {
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (task.isRunning()) {
            Log.record(task.getName() + "⏹停止请求后仍未退出，补跑将延后");
        }
        // 即使停止成功，这一轮也已经超过计划时限，必须按超时（未完成）回执，
        // 不能因为槽位已释放就误报 COMPLETED。
        return false;
    }

    public static int startGroupTask(String groupCode) {
        // 分组手动执行同样先对齐磁盘配置
        ConfigV2.reloadIfDiskChanged(UserIdMap.getCurrentUid());
        if (RiskGuard.isGlobalPaused()) {
            Log.record("风控保护🛡️账号级风控暂停中，跳过分组执行；请到首页查看并手动恢复");
            return 0;
        }
        List<Model> targets = new ArrayList<>();
        for (Model model : getModelArray()) {
            if (model == null || ModelType.TASK != model.getType()) {
                continue;
            }
            ModelGroup group = model.getGroup();
            if (group == null || !groupCode.equals(group.getCode())) {
                continue;
            }
            targets.add(model);
        }
        if (targets.isEmpty()) {
            return 0;
        }
        // 手动执行分组也算新一轮：先清零本轮收取能量，再起跑
        NotificationUtil.startRound();
        int count = 0;
        for (Model model : targets) {
            if (((ModelTask) model).startTask(false)) {
                count++;
            }
        }
        return count;
    }

    public static void stopAllTask() {
        for (Model model : getModelArray()) {
            if (model != null) {
                try {
                    if (ModelType.TASK == model.getType()) {
                        ((ModelTask) model).stopTask();
                    }
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
        }
    }

    private ChildTaskExecutor newTimedTaskExecutor() {
        ChildTaskExecutor childTaskExecutor;
        Integer timedTaskModel = BaseModel.getTimedTaskModel().getValue();
        if (timedTaskModel == BaseModel.TimedTaskModel.SYSTEM) {
            childTaskExecutor = new SystemChildTaskExecutor();
        } else if (timedTaskModel == BaseModel.TimedTaskModel.PROGRAM) {
            childTaskExecutor = new ProgramChildTaskExecutor();
        } else {
            throw new RuntimeException("not found childTaskExecutor");
        }
        return childTaskExecutor;
    }

    public static class ChildModelTask implements Runnable {

        @Getter
        private ModelTask modelTask;

        @Getter
        private final String id;

        @Getter
        private final String group;

        private final Runnable runnable;

        @Getter
        private final Long execTime;

        private CancelTask cancelTask;

        @Getter
        private Boolean isCancel = false;

        public ChildModelTask() {
            this(null, null, () -> {
            }, 0L);
        }

        public ChildModelTask(String id) {
            this(id, null, () -> {
            }, 0L);
        }

        public ChildModelTask(String id, String group) {
            this(id, group, () -> {
            }, 0L);
        }

        protected ChildModelTask(String id, long execTime) {
            this(id, null, null, execTime);
        }

        /*protected ChildModelTask(String id, String group, Long time) {
            this(id, group, null, time);
        }*/

        public ChildModelTask(String id, Runnable runnable) {
            this(id, null, runnable, 0L);
        }

        public ChildModelTask(String id, String group, Runnable runnable) {
            this(id, group, runnable, 0L);
        }

        public ChildModelTask(String id, String group, Runnable runnable, Long execTime) {
            if (StringUtil.isEmpty(id)) {
                id = toString();
            }
            if (StringUtil.isEmpty(group)) {
                group = "DEFAULT";
            }
            if (runnable == null) {
                runnable = setRunnable();
            }
            this.id = id;
            this.group = group;
            this.runnable = runnable;
            this.execTime = execTime;
        }

        public Runnable setRunnable() {
            return null;
        }

        public final void run() {
            // 子任务线程继承所属模块：这些流程日志也带模块 tag（嵌套时恢复上层）
            ModelTask owner = modelTask;
            ModelGroup group = owner == null ? null : owner.getGroup();
            String prevModule = Log.beginModule(group == null ? null : group.getCode());
            try {
                runnable.run();
            } finally {
                Log.endModule(prevModule);
            }
        }

        protected void setCancelTask(CancelTask cancelTask) {
            this.cancelTask = cancelTask;
        }

        public final void cancel() {
            if (cancelTask != null) {
                try {
                    cancelTask.cancel();
                    isCancel = true;
                } catch (Exception e) {
                    Log.printStackTrace(e);
                }
            }
        }

    }

    public interface CancelTask {

        void cancel();

    }

}
