package io.github.aw1y2z.sesame.data.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 能力注册表：按机制×端点分类，交易与抽机会必须先于通用关键词命中。 */
public class TaskCapabilityRegistryTest {

    private static TaskCapabilityRegistry.Spec resolve(String domain, String id, String title) {
        return TaskCapabilityRegistry.resolve(domain, id, title, "", 0, 0);
    }

    @Test
    public void transactionBeatsEverythingElse() {
        TaskCapabilityRegistry.Spec spec = resolve("orchard", "ORCHARD_NORMAL_XIANXIAZHIFU100", "逛一逛得肥料");
        assertEquals(TaskCapabilityRegistry.Mechanism.TRANSACTION, spec.mechanism);
        assertEquals(TaskCapabilityRegistry.CostType.CASH, spec.costType);
    }

    @Test
    public void cookHasItsOwnEndpoint() {
        TaskCapabilityRegistry.Spec spec = resolve("farm", "COOK_DAILY", "小鸡厨房 制作");
        assertEquals("antfarm.cook", spec.endpoint);
        assertTrue(spec.classKey.endsWith("|farm|antfarm.cook"));
    }

    @Test
    public void virtualSpendKeepsCostSubtype() {
        assertEquals(TaskCapabilityRegistry.CostType.WATER, resolve("forest", "WATER_FRIEND", "浇水").costType);
        assertEquals(TaskCapabilityRegistry.CostType.ENERGY, resolve("forest", "IP_EXCHANGE", "交能量兑保护罩").costType);
        assertEquals(TaskCapabilityRegistry.CostType.FEED, resolve("farm", "FEED", "喂小鸡饲料").costType);
        assertEquals(TaskCapabilityRegistry.CostType.STEPS, resolve("sports", "STEP_DONATE", "捐步数").costType);
        assertEquals(TaskCapabilityRegistry.CostType.FERTILIZER, resolve("orchard", "SPREAD_MANURE", "施肥3次").costType);
    }

    @Test
    public void chanceAndGameAreSeparated() {
        assertEquals(TaskCapabilityRegistry.Mechanism.CHANCE, resolve("farm", "DRAW_MACHINE", "装扮抽奖").mechanism);
        assertEquals(TaskCapabilityRegistry.Mechanism.GAME, resolve("forest", "XDDQ_QDRW_LEYUAN", "乐园任务").mechanism);
    }

    @Test
    public void externalJumpIsNotBrowse() {
        assertEquals(TaskCapabilityRegistry.Mechanism.EXTERNAL_JUMP, resolve("orchard", "KUAISHOU_VISIT", "逛一逛快手").mechanism);
        assertEquals(TaskCapabilityRegistry.Mechanism.BROWSE, resolve("forest", "READ", "浏览森林集市").mechanism);
    }

    @Test
    public void earnedManualReasonIsHonoured() {
        TaskCapabilityRegistry.Spec real = TaskCapabilityRegistry.resolve("forest", "T1", "某任务", "REAL_ACTION", 0, 0);
        assertEquals(TaskCapabilityRegistry.Mechanism.REAL_ACTION, real.mechanism);
        TaskCapabilityRegistry.Spec bounty = TaskCapabilityRegistry.resolve("forest", "ENERGY_XUANJIAO_1", "悬赏卡", "BOUNTY_CARD", 0, 0);
        assertEquals(TaskCapabilityRegistry.Mechanism.REAL_ACTION, bounty.mechanism);
        assertEquals("doFarmTask:bounty", bounty.endpoint);
    }

    @Test
    public void sceneCodeSeparatesClasses() {
        TaskCapabilityRegistry.Spec leyi = TaskCapabilityRegistry.resolve(
                "farm", "XDDQ_QDRW_LEYUAN", "乐园任务", "", 0, 0, "ANTFOREST_LEYUAN_DAILY_TASK");
        TaskCapabilityRegistry.Spec other = TaskCapabilityRegistry.resolve(
                "farm", "XDDQ_QDRW_LEYUAN", "乐园任务", "", 0, 0, "ANTFARM_GAME_OTHER");
        assertEquals(TaskCapabilityRegistry.Mechanism.GAME, leyi.mechanism);
        assertTrue(leyi.classKey.endsWith("#ANTFOREST_LEYUAN_DAILY_TASK"));
        assertTrue(!leyi.classKey.equals(other.classKey));
    }

    @Test
    public void classKeyRoundTripsMechanism() {
        TaskCapabilityRegistry.Spec spec = resolve("ocean", "FRIENDRUBBISCLEAN_EVERYDAY_NEW", "帮好友清理垃圾");
        assertEquals(TaskCapabilityRegistry.Mechanism.SOCIAL, spec.mechanism);
        assertEquals("cleanFriendOcean", spec.endpoint);
        assertEquals(spec.mechanism.name(), TaskCapabilityRegistry.mechanismOfClassKey(spec.classKey));
    }
}
