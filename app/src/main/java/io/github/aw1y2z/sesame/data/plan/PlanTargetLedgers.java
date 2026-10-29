package io.github.aw1y2z.sesame.data.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.aw1y2z.sesame.data.ledger.GoldenBeansTaskLedger;
import io.github.aw1y2z.sesame.data.ledger.KineticTaskLedger;

/**
 * 计划目标 → 账本域的映射，计划页、首页和进度计算共用一份，避免同一个目标在不同页面
 * 取到不同的任务集合。只列有任务列表的账本；纯时间/能量目标返回空。
 */
public final class PlanTargetLedgers {

    public static final class Entry {
        public final String label;
        public final String domain;

        Entry(String label, String domain) {
            this.label = label;
            this.domain = domain;
        }
    }

    private static final List<Entry> NONE = Collections.emptyList();

    private PlanTargetLedgers() {
    }

    public static List<Entry> entriesFor(String target) {
        if (target == null) {
            return NONE;
        }
        switch (target) {
            case PlanTargets.FARM:
            case PlanTargets.FARM_DAILY:
                return list(entry("饲料", "farm"), entry("抽奖", "farm.draw"));
            case PlanTargets.FARM_TASK:
                return list(entry("饲料", "farm"));
            case PlanTargets.FARM_DRAW:
                return list(entry("抽奖", "farm.draw"));
            case PlanTargets.OCEAN:
                return list(entry("奖励", "ocean"), entry("摸鱼", "ocean.fish"));
            case PlanTargets.OCEAN_REWARD:
                return list(entry("奖励", "ocean"));
            case PlanTargets.OCEAN_FISH:
                return list(entry("摸鱼", "ocean.fish"));
            case PlanTargets.DODO:
            case PlanTargets.SPECIES_TASK:
                return list(entry("任务", "species.task"));
            case PlanTargets.ORCHARD:
            case PlanTargets.ORCHARD_DAILY:
                return list(
                        entry("任务", "orchard.task"),
                        entry("乐园", "orchard.park"),
                        entry("寻宝", "orchard.draw"),
                        entry("挑战", "orchard.challenge")
                );
            case PlanTargets.ORCHARD_TASK:
                return list(entry("任务", "orchard.task"));
            case PlanTargets.ORCHARD_PARK:
                return list(entry("乐园", "orchard.park"));
            case PlanTargets.ORCHARD_DRAW:
                return list(entry("寻宝", "orchard.draw"));
            case PlanTargets.ORCHARD_CHALLENGE:
                return list(entry("挑战", "orchard.challenge"));
            case PlanTargets.FOREST_DAILY:
                return list(
                        entry("奖励", "forest.reward"),
                        entry("乐园", "forest.park"),
                        entry("巡护", "forest.patrol")
                );
            case PlanTargets.FOREST_REWARD:
                return list(entry("奖励", "forest.reward"));
            case PlanTargets.FOREST_PARK:
            case PlanTargets.FOREST_GAME:
                return list(entry("乐园", "forest.park"));
            case PlanTargets.FOREST_PATROL:
                return list(entry("巡护", "forest.patrol"));
            case PlanTargets.FOREST_READ:
                return NONE;
            case PlanTargets.KINETIC:
                return list(entry("动能", KineticTaskLedger.DOMAIN));
            case PlanTargets.GOLDEN:
                return list(entry("金豆", GoldenBeansTaskLedger.DOMAIN));
            default:
                return NONE;
        }
    }

    /** 目标是否带任务列表：这类计划只执行一轮，进度按任务数表达。 */
    public static boolean hasTaskList(String target) {
        return !entriesFor(target).isEmpty();
    }

    private static Entry entry(String label, String domain) {
        return new Entry(label, domain);
    }

    private static List<Entry> list(Entry... entries) {
        List<Entry> result = new ArrayList<>(entries.length);
        Collections.addAll(result, entries);
        return result;
    }
}
