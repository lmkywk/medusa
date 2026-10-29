package io.github.aw1y2z.sesame.model.task.goldenbeans;

import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import io.github.aw1y2z.sesame.data.ModelFields;
import io.github.aw1y2z.sesame.data.ModelGroup;
import io.github.aw1y2z.sesame.data.modelFieldExt.BooleanModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.IntegerModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectAndCountModelField;
import io.github.aw1y2z.sesame.data.modelFieldExt.SelectModelField;
import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.AlipayGoldenBeansTaskList;
import io.github.aw1y2z.sesame.entity.AlipayGoldenBeansMallItem;
import io.github.aw1y2z.sesame.model.base.TaskCommon;
import io.github.aw1y2z.sesame.util.Log;
import io.github.aw1y2z.sesame.util.Status;

/**
 * 金豆夺宝任务模块：只管理配置项与执行编排。
 * <p>
 * 玩法按业务域拆到独立处理器——{@link GoldenBeansTasks}（入口日常与任务列表）、
 * {@link GoldenBeansGameCenter}（乐园抽奖与游戏权益）、{@link GoldenBeansMiner}（金猫矿工）、
 * {@link GoldenBeansExchange}（两种换豆）、{@link GoldenBeansMall}（商城兑换）；
 * 两个入口（芭芭农场 / 芝麻炼金）由 {@link GoldenBeansEntry} 描述，bizType / source / sceneCode 不可混用。
 */
public class goldenbeans extends ModelTask {

    /** 任务黑白名单初始化标记 */
    private static final String FLAG_BLACKLIST_INIT = "BlackList::initGoldenBeans";

    private BooleanModelField goldenBeansMain;
    private BooleanModelField goldenBeansAutoTask;
    private BooleanModelField goldenBeansVisitTask;
    private BooleanModelField goldenBeansSign;
    private BooleanModelField goldenBeansGameDraw;
    private BooleanModelField goldenBeansAutoMine;
    private BooleanModelField goldenBeansCollectReward;
    private BooleanModelField AutoGoldenBeansTaskList;
    private SelectModelField GoldenBeansTaskList;
    private BooleanModelField goldenBeansAutoManureExchange;
    private IntegerModelField goldenBeansManureExchangeLimit;
    private BooleanModelField goldenBeansAutoSesameExchange;
    private IntegerModelField goldenBeansSesameExchangeLimit;
    private BooleanModelField goldenBeansMallExchange;
    private SelectAndCountModelField GoldenBeansMallItemList;
    private IntegerModelField executeInterval;

    @Override
    public String getName() {
        return "金豆夺宝";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.GOLDENBEANS;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(goldenBeansMain = new BooleanModelField("goldenBeansVisitGarden", "待领金豆 | 一键收豆", false)
                .setDescription("领取待领的金豆和乐园奖励；开启后才会执行金豆相关任务"));
        modelFields.addField(goldenBeansAutoTask = new BooleanModelField("goldenBeansAutoTask", "金豆任务 | 自动任务", false)
                .setDescription("自动完成金豆任务并领取奖励"));
        modelFields.addField(goldenBeansVisitTask = new BooleanModelField("goldenBeansVisitTask", "金豆任务 | 浏览任务", false)
                .setDescription("自动完成需要外跳浏览的任务"));
        modelFields.addField(goldenBeansSign = new BooleanModelField("goldenBeansSign", "金豆签到 | 自动签到", false));
        modelFields.addField(goldenBeansGameDraw = new BooleanModelField("goldenBeansGameDraw", "金豆乐园 | 乐园抽奖", false)
                .setDescription("用完可用的乐园抽奖次数"));
        modelFields.addField(goldenBeansAutoMine = new BooleanModelField("goldenBeansAutoMine", "金猫矿工 | 自动挖矿", false));
        modelFields.addField(goldenBeansCollectReward = new BooleanModelField("goldenBeansCollectReward", "金猫矿工 | 领奖后同步", false)
                .setDescription("领完奖励后重新同步一次金豆数据"));
        modelFields.addField(AutoGoldenBeansTaskList = new BooleanModelField("AutoGoldenBeansTaskList", "金豆任务 | 自动跳过", true));
        modelFields.addField(GoldenBeansTaskList = new SelectModelField("GoldenBeansTaskList", "金豆任务 | 跳过列表", new LinkedHashSet<>(), AlipayGoldenBeansTaskList::getList).setDependsOn("AutoGoldenBeansTaskList"));
        modelFields.addField(goldenBeansAutoManureExchange = new BooleanModelField("goldenBeansAutoManureExchange", "肥料换豆 | 自动兑换", false));
        modelFields.addField(goldenBeansManureExchangeLimit = new IntegerModelField("goldenBeansManureExchangeLimit", "肥料换豆 | 每日上限(0不限)", 0, 0, null).setDependsOn("goldenBeansAutoManureExchange"));
        modelFields.addField(goldenBeansAutoSesameExchange = new BooleanModelField("goldenBeansAutoSesameExchange", "芝麻粒换豆 | 自动兑换", false));
        modelFields.addField(goldenBeansSesameExchangeLimit = new IntegerModelField("goldenBeansSesameExchangeLimit", "芝麻粒换豆 | 每日上限(0不限)", 0, 0, null).setDependsOn("goldenBeansAutoSesameExchange"));
        modelFields.addField(goldenBeansMallExchange = new BooleanModelField("goldenBeansMallExchange", "商城兑换 | 自动兑换", false));
        modelFields.addField(GoldenBeansMallItemList = new SelectAndCountModelField("GoldenBeansMallItemList", "商城兑换 | 可兑列表", new LinkedHashMap<>(), AlipayGoldenBeansMallItem::getList, "请填写每日兑换次数(0为不限)", 0, 99).setDependsOn("goldenBeansMallExchange"));
        modelFields.addField(executeInterval = new IntegerModelField("executeInterval", "操作间隔(毫秒)", 500, 500, null)
                .setDescription("每次兑换操作之间的等待时间"));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            Log.i("任务暂停⏸️金豆夺宝:当前为仅收能量时间");
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        try {
            int interval = Math.max(executeInterval.getValue() != null ? executeInterval.getValue() : 500, 500);

            boolean signEnabled = GoldenBeansSupport.enabled(goldenBeansSign);
            boolean popupEnabled = GoldenBeansSupport.enabled(goldenBeansMain);
            boolean taskEnabled = GoldenBeansSupport.enabled(goldenBeansAutoTask);
            boolean gameEnabled = GoldenBeansSupport.enabled(goldenBeansGameDraw);
            boolean mineEnabled = GoldenBeansSupport.enabled(goldenBeansAutoMine);
            boolean resyncEnabled = GoldenBeansSupport.enabled(goldenBeansCollectReward);
            boolean manureEnabled = GoldenBeansSupport.enabled(goldenBeansAutoManureExchange);
            boolean sesameEnabled = GoldenBeansSupport.enabled(goldenBeansAutoSesameExchange);
            boolean mallEnabled = GoldenBeansSupport.enabled(goldenBeansMallExchange);

            if (!signEnabled && !popupEnabled && !taskEnabled && !gameEnabled
                    && !mineEnabled && !resyncEnabled && !manureEnabled && !sesameEnabled && !mallEnabled) {
                Log.record("金豆夺宝功能未开启#本轮跳过");
                return;
            }

            GoldenBeansTasks tasks = new GoldenBeansTasks(GoldenBeansTaskList,
                    GoldenBeansSupport.enabled(AutoGoldenBeansTaskList),
                    GoldenBeansSupport.enabled(goldenBeansVisitTask));

            // 每日初始化任务黑白名单
            if (!Status.hasFlagToday(FLAG_BLACKLIST_INIT)) {
                tasks.initTaskListMap();
                Status.flagToday(FLAG_BLACKLIST_INIT);
            }

            // 两个入口分别执行：主页查询 → 签到 → 营销弹窗 → 任务列表
            boolean taskResolved = true;
            boolean allowClaim = io.github.aw1y2z.sesame.data.plan.PlanContext.allowsSection(
                    "golden", "claim",
                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.Mechanism.CLAIM,
                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.CostType.NONE);
            boolean allowGame = io.github.aw1y2z.sesame.data.plan.PlanContext.allowsSection(
                    "golden", "game",
                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.Mechanism.GAME,
                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.CostType.NONE);
            boolean allowCoin = io.github.aw1y2z.sesame.data.plan.PlanContext.allowsSection(
                    "golden", "exchange",
                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.Mechanism.VIRTUAL_SPEND,
                    io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry.CostType.COIN);
            if (allowClaim && (signEnabled || popupEnabled || taskEnabled)) {
                for (GoldenBeansEntry entry : GoldenBeansEntry.ALL) {
                    if (!tasks.processEntry(entry, interval, signEnabled, popupEnabled, taskEnabled)) {
                        taskResolved = false;
                    }
                }
            }

            // 乐园奖励与金猫矿工只存在于农场入口
            boolean gameResolved = true;
            if (gameEnabled && allowGame) {
                gameResolved = GoldenBeansGameCenter.run(interval);
            }
            if (mineEnabled && allowGame) {
                GoldenBeansMiner.run(interval);
            }

            if (manureEnabled && allowCoin) {
                GoldenBeansExchange.exchangeManure(interval, dailyLimit(goldenBeansManureExchangeLimit));
            }
            if (sesameEnabled && allowCoin) {
                GoldenBeansExchange.exchangeSesame(interval, dailyLimit(goldenBeansSesameExchangeLimit));
            }

            // 商城可兑列表无条件同步（供配置页勾选），兑换才受开关控制
            GoldenBeansMall.run(interval, GoldenBeansMallItemList.getValue(), mallEnabled && allowCoin);

            if (resyncEnabled) {
                resync(interval);
            }

            // 汇总行由 ModelTask 收尾统一打（Log.moduleSummary），这里只声明是否还有未处理任务
            if (!(taskResolved && gameResolved)) {
                Log.markUnresolved();
            }
        } catch (Throwable th) {
            Log.i(GoldenBeansSupport.TAG, "run err:");
            Log.printStackTrace(GoldenBeansSupport.TAG, th);
        }
    }

    /** 读取配置的单日上限，非法值按 0（不限）处理 */
    private int dailyLimit(IntegerModelField field) {
        return field != null && field.getValue() != null ? Math.max(field.getValue(), 0) : 0;
    }

    /** 领奖后数据同步回查 */
    private void resync(int interval) {
        try {
            GoldenBeansSupport.pause(interval);
            JSONObject jo = GoldenBeansSupport.parse(goldenbeansRpcCall.pull("JAR_INFO", "TASK_LIST"));
            if (!GoldenBeansSupport.ok(jo)) {
                Log.i("金豆同步⚠️数据同步失败[" + GoldenBeansSupport.describe(jo) + "]");
            }
        } catch (Throwable th) {
            Log.i(GoldenBeansSupport.TAG, "resync err:");
            Log.printStackTrace(GoldenBeansSupport.TAG, th);
        }
    }
}
