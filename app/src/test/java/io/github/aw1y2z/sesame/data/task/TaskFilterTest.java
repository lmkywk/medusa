package io.github.aw1y2z.sesame.data.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/** 按类型执行：空过滤器放行；机制/成本/域是“与”，classKeys 是精确集合。 */
public class TaskFilterTest {

    private static TaskCapabilityRegistry.Spec spec(String domain, String id, String title) {
        return TaskCapabilityRegistry.resolve(domain, id, title, "", 0, 0);
    }

    @Test
    public void emptyFilterIsNull() {
        assertNull(TaskFilter.of(null, null, null, null));
        assertNull(TaskFilter.of(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList()));
    }

    @Test
    public void mechanismFilterOnlyAllowsThatClass() {
        TaskFilter filter = TaskFilter.of(List.of("VIRTUAL_SPEND"), null, null, null);
        assertTrue(filter.allows(spec("forest", "WATER_FRIEND", "浇水")));
        assertFalse(filter.allows(spec("farm", "DRAW_MACHINE", "装扮抽奖")));
    }

    @Test
    public void costAndMechanismAreBothRequired() {
        TaskFilter filter = TaskFilter.of(List.of("VIRTUAL_SPEND"), List.of("FERTILIZER"), null, null);
        assertTrue(filter.allows(spec("orchard", "SPREAD_MANURE", "施肥3次")));
        assertFalse(filter.allows(spec("forest", "WATER_FRIEND", "浇水")));
    }

    @Test
    public void domainNarrowsToThatDomain() {
        TaskFilter filter = TaskFilter.of(List.of("VIRTUAL_SPEND"), null, List.of("forest"), null);
        assertTrue(filter.allows(spec("forest", "WATER_FRIEND", "浇水")));
        assertFalse(filter.allows(spec("orchard", "SPREAD_MANURE", "施肥3次")));
    }

    @Test
    public void classKeysAreExact() {
        String key = spec("farm", "COOK_DAILY", "小鸡厨房 制作").classKey;
        TaskFilter filter = TaskFilter.of(null, null, null, List.of(key));
        assertTrue(filter.allows(spec("farm", "COOK_DAILY", "小鸡厨房 制作")));
        assertFalse(filter.allows(spec("farm", "DRAW_MACHINE", "装扮抽奖")));
    }

    @Test
    public void jsonRoundTripKeepsRules() {
        TaskFilter filter = TaskFilter.of(List.of("CHANCE"), List.of("CHANCE"), null, null);
        TaskFilter parsed = TaskFilter.fromJson(filter.toJson().toString());
        assertTrue(parsed.allows(spec("farm", "DRAW_MACHINE", "装扮抽奖")));
        assertFalse(parsed.allows(spec("forest", "WATER_FRIEND", "浇水")));
    }
}
