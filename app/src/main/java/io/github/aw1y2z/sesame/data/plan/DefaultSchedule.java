package io.github.aw1y2z.sesame.data.plan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 默认编排：零点两轮收能量 + 07:00–08:25 黄金收能量 + 08:25–10:30 主体批次。
 * 新用户/空计划时自动生成；老用户可在管理计划里恢复默认。
 */
public final class DefaultSchedule {

    public static final String MODE_SYSTEM = "SYSTEM";

    public static final class Entry {
        public final String id;
        public final String time;
        public final String windowEnd;
        public final List<String> roundTimes;
        public final String target;
        public final String label;
        public final String mode;
        public final String note;

        Entry(String id, String time, String windowEnd, List<String> roundTimes,
              String target, String label, String mode, String note) {
            this.id = id;
            this.time = time;
            this.windowEnd = windowEnd;
            this.roundTimes = roundTimes;
            this.target = target;
            this.label = label;
            this.mode = mode;
            this.note = note;
        }
    }

    private DefaultSchedule() {
    }

    public static List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        // 零点：0 点刷新后立刻全量收一轮；00:02 第二轮只做"防漏跑"兜底，
        // 这个时间点人手收一轮就够，之后不会再产出新能量，不是给晚熟能量留的
        list.add(new Entry("default-0000-forest-collect", "00:00", "00:45",
                Arrays.asList("00:00", "00:02"), PlanTargets.FOREST_COLLECT, "森林·收能量",
                Plan.MODE_COLLECT, "0 点收一轮，00:02 兜底再核一遍"));
        list.add(new Entry("default-0005-ledger-sync", "00:05", "00:35",
                new ArrayList<>(), "", "全量账本同步",
                MODE_SYSTEM, "系统任务：刷新今日任务与差异，不占计划"));
        // 夜间短批：全部压到 02:00 同一跳，调度器一次唤醒、一个 90 分钟唤醒锁里串行跑完；
        // 前 7 项实测合计约 10 分钟，先跑短任务，物种/森林/果园/金豆放后面。
        // 运动排在最前：步数是行走捐/能量兑换的前置，必须先把步数同步进去
        list.add(new Entry("default-0820-sports-kinetic", "08:20", "09:30",
                Arrays.asList("08:20"), PlanTargets.KINETIC, "运动·步数与能量",
                Plan.MODE_ONCE, "同步步数→行走→捐步→签到/能量任务，先于其它批次"));
        list.add(new Entry("default-0200-farm-daily", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FARM_DAILY, "庄园·日常",
                Plan.MODE_ONCE, "投喂/鸡蛋/日记；特殊饲料按上限使用"));
        list.add(new Entry("default-0200-farm-task", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FARM_TASK, "庄园·饲料任务",
                Plan.MODE_ONCE, "先领已完成，再做 TODO；短批优先"));
        list.add(new Entry("default-0200-farm-draw", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FARM_DRAW, "庄园·装扮抽抽乐",
                Plan.MODE_ONCE, "只抽装扮，不消耗特殊饲料"));
        list.add(new Entry("default-0200-farm-kitchen", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FARM_KITCHEN, "庄园·小鸡厨房",
                Plan.MODE_ONCE, "只做菜，不消耗特殊饲料"));
        // 果园/金豆排在夜间批的第 5、6 位：此前排在末尾，夜间批次一跑就超时到 07:34，
        // 正好撞进只收能量时段被整条跳过。它们窗口只到 06:00，跳过就是当天没跑。
        list.add(new Entry("default-0200-orchard-daily", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.ORCHARD_DAILY, "果园·日常",
                Plan.MODE_ONCE, "任务/签到/乐园/寻宝/挑战"));
        list.add(new Entry("default-0200-golden", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.GOLDEN, "金豆·签到与任务",
                Plan.MODE_ONCE, "只做站内签到/任务"));
        list.add(new Entry("default-0200-ocean-reward", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.OCEAN_REWARD, "海洋·奖励",
                Plan.MODE_ONCE, "先领已完成，再做任务；短批优先"));
        list.add(new Entry("default-0200-ocean-fish", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.OCEAN_FISH, "海洋·摸鱼",
                Plan.MODE_ONCE, "摸鱼次数刷新后同一批跑，不再被任务挤掉"));
        list.add(new Entry("default-0200-ocean-welcome", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.OCEAN_WELCOME, "海洋·迎回",
                Plan.MODE_ONCE, "拼图/潘多拉；收尾快"));
        list.add(new Entry("default-0200-species", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.DODO, "物种·日常",
                Plan.MODE_ONCE, "好友抽卡仍走 12:01"));
        // 森林日常继续拆短：领奖/乐园/巡护各自独立，无纸阅读单独 12 分钟预算；
        // 任何一段卡住都不会再拖住后面整段森林。
        list.add(new Entry("default-0200-forest-reward", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FOREST_REWARD, "森林·领奖励",
                Plan.MODE_ONCE, "签到/任务/已完成领奖/兑换/医疗/装扮"));
        list.add(new Entry("default-0200-forest-park", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FOREST_PARK, "森林·乐园宝箱",
                Plan.MODE_ONCE, "乐园宝箱/游戏中心奖励，收尾快"));
        list.add(new Entry("default-0200-forest-game", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FOREST_GAME, "森林·乐园游戏",
                Plan.MODE_ONCE, "限定游戏 6/6 多轮，单独 10 分钟预算"));
        list.add(new Entry("default-0200-forest-patrol", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FOREST_PATROL, "森林·巡护",
                Plan.MODE_ONCE, "旧版巡护/新版保护地/动物伙伴"));
        list.add(new Entry("default-0200-forest-read", "08:25", "10:30",
                Arrays.asList("08:25"), PlanTargets.FOREST_READ, "森林·无纸阅读",
                Plan.MODE_ONCE, "单独 12 分钟，读完即止"));
        // 黄金收能量：7:00 前后能量集中产出；只排两轮，08:25 结束，给 08:35 寻宝留出干净窗口。
        list.add(new Entry("default-0700-forest-window", "07:00", "08:25",
                Arrays.asList("07:00", "07:40"),
                PlanTargets.FOREST_COLLECT, "森林·黄金收能量",
                Plan.MODE_COLLECT, "7:00–8:25 只收能量，首轮全量，07:40 补晚熟"));
        // 8:35 之后才排其它任务；能量窗口 8:25 结束，避免收能量把寻宝挤成“窗口已结束”。
        list.add(new Entry("default-0835-forest-hunt", "10:35", "11:05",
                Arrays.asList("08:35"), PlanTargets.FOREST_HUNT, "森林·寻宝",
                Plan.MODE_ONCE, "单窗口实测约 30 分钟，黄金窗口结束后再跑"));
        // 上午短批：肥料/施肥/海洋清理压到 09:10 同一跳，一次唤醒串行跑完，错开 08:35 寻宝。
        list.add(new Entry("default-0910-orchard-fertilizer", "10:45", "11:25",
                Arrays.asList("09:10"), PlanTargets.ORCHARD_FERTILIZER, "果园·每日肥料",
                Plan.MODE_ONCE, "07:00 后可领；和施肥/海洋清理同批"));
        list.add(new Entry("default-0910-orchard-spread", "10:45", "11:25",
                Arrays.asList("09:10"), PlanTargets.ORCHARD_SPREAD, "果园·自动施肥",
                Plan.MODE_ONCE, "领完每日肥料后按自定义次数施肥"));
        list.add(new Entry("default-0910-ocean-clean", "10:45", "11:25",
                Arrays.asList("09:10"), PlanTargets.OCEAN_CLEAN, "海洋·上午清理",
                Plan.MODE_ONCE, "错开 08:35 寻宝；07:00–17:00 窗口"));
        list.add(new Entry("default-1201-species-friend", "12:01", "12:20",
                Arrays.asList("12:01"), PlanTargets.SPECIES_FRIEND, "物种·好友抽卡",
                Plan.MODE_ONCE, "12:00 开放，定点执行"));
        // 夜间全量被风控/验证跳过后，任务奖励不能跟着整天作废；12:30 单独补一次任务与领奖
        list.add(new Entry("default-1230-species-task", "12:30", "12:50",
                Arrays.asList("12:30"), PlanTargets.SPECIES_TASK, "物种·任务",
                Plan.MODE_ONCE, "兜底：补领已完成未领奖的物种任务"));
        list.add(new Entry("default-1701-ocean-clean", "17:01", "17:20",
                Arrays.asList("17:01"), PlanTargets.OCEAN_CLEAN, "海洋·下午清理",
                Plan.MODE_ONCE, "17:00–24:00 窗口"));
        // 22:30 由调度器统一兜底未完成的计划，不再单排晚间收能量
        return list;
    }

    /** 把默认编排转成可见、可编辑的计划；系统同步行不生成计划。 */
    public static List<Plan> createPlans() {
        List<Plan> plans = new ArrayList<>();
        for (Entry entry : entries()) {
            if (MODE_SYSTEM.equals(entry.mode) || entry.target == null || entry.target.isEmpty()) {
                continue;
            }
            Plan plan = new Plan();
            plan.setId(entry.id);
            plan.setTarget(entry.target);
            plan.setWindowStart(entry.time);
            plan.setWindowEnd(entry.windowEnd);
            plan.setRoundTimes(entry.roundTimes);
            plan.setMode(entry.mode);
            plan.setPaceMinutes(0);
            plan.setDefaultPlan(true);
            plan.setEnabled(true);
            if (entry.id.startsWith("default-0200-")) {
                plan.setGroupId("night-batch");
            } else if (entry.id.startsWith("default-0910-")) {
                plan.setGroupId("morning-batch");
            }
            plans.add(plan);
        }
        return plans;
    }
}
