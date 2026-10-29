package io.github.aw1y2z.sesame.model.task.antOcean;

import io.github.aw1y2z.sesame.data.plan.PlanTargets;

/**
 * 4.0.0 god class 试点：海洋计划目标的分发器。
 * <p>把 {@code AntOcean.run()} 里的 if 链搬到这里，god class 只保留各段的执行体。
 * 分发结果由 {@link Segments} 回调，调用方仍是 AntOcean，行为不变。
 */
public final class OceanFacade {

    private OceanFacade() {
    }

    public interface Segments {
        void reward();

        void clean();

        void fish();

        void welcome();
    }

    /**
     * @return true 表示该目标已由本类分发（调用方应直接返回）
     */
    public static boolean dispatch(String target, Segments segments) {
        if (PlanTargets.OCEAN_REWARD.equals(target)) {
            segments.reward();
            return true;
        }
        if (PlanTargets.OCEAN_CLEAN.equals(target)) {
            segments.clean();
            return true;
        }
        if (PlanTargets.OCEAN_FISH.equals(target)) {
            segments.fish();
            return true;
        }
        if (PlanTargets.OCEAN_WELCOME.equals(target)) {
            segments.welcome();
            return true;
        }
        return false;
    }
}
