package io.github.aw1y2z.sesame.data.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 手动直达目标：可被 planRun 使用，但不进晚间整域兜底。 */
public class PlanTargetsTest {

    @Test
    public void patrolIsManualTarget() {
        assertTrue(PlanTargets.isValidTarget(PlanTargets.FOREST_PATROL));
        assertEquals("森林·保护地巡护", PlanTargets.labelOf(PlanTargets.FOREST_PATROL));
        assertFalse(PlanTargets.all().contains(PlanTargets.FOREST_PATROL));
        assertTrue(PlanTargets.fullTestTargets().contains(PlanTargets.FOREST_PATROL));
    }
}
