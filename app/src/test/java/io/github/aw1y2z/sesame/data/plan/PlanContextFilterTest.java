package io.github.aw1y2z.sesame.data.plan;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import io.github.aw1y2z.sesame.data.task.TaskCapabilityRegistry;
import io.github.aw1y2z.sesame.data.task.TaskFilter;

/** 按类执行：任务与段共用过滤器，跳过的原因要能汇总出来。 */
public class PlanContextFilterTest {

    @After
    public void tearDown() {
        PlanContext.setFilter(null);
        PlanContext.setManual(false);
        PlanContext.takeSkipCounts();
        PlanContext.takeSkipSamples();
        PlanContext.clear();
    }

    @Test
    public void filterBlocksTaskAndCountsReason() {
        PlanContext.setFilter(TaskFilter.of(List.of("CLAIM"), null, null, null));
        PlanContext.setManual(true);
        assertTrue(PlanContext.allowsTask("ocean", "SIGN", "签到", 0, 0));
        assertFalse(PlanContext.allowsTask("farm", "DRAW_MACHINE", "装扮抽奖", 0, 0));
        Map<String, Integer> skipped = PlanContext.takeSkipCounts();
        assertTrue(skipped.containsKey("类型·抽机会"));
    }

    @Test
    public void sectionGateUsesSameFilter() {
        PlanContext.setFilter(TaskFilter.of(List.of("CLAIM"), null, null, null));
        PlanContext.setManual(true);
        assertFalse(PlanContext.allowsSection("forest", "water",
                TaskCapabilityRegistry.Mechanism.VIRTUAL_SPEND, TaskCapabilityRegistry.CostType.WATER));
        assertTrue(PlanContext.allowsSection("ocean", "clean",
                TaskCapabilityRegistry.Mechanism.CLAIM, TaskCapabilityRegistry.CostType.NONE));
        assertTrue(PlanContext.takeSkipCounts().containsKey("段·资源支出/water"));
    }

    @Test
    public void noFilterAllowsEverything() {
        PlanContext.setFilter(null);
        assertTrue(PlanContext.allowsTask("farm", "DRAW_MACHINE", "装扮抽奖", 0, 0));
        assertTrue(PlanContext.allowsSection("forest", "water",
                TaskCapabilityRegistry.Mechanism.VIRTUAL_SPEND, TaskCapabilityRegistry.CostType.WATER));
        assertTrue(PlanContext.takeSkipCounts().isEmpty());
    }
}
