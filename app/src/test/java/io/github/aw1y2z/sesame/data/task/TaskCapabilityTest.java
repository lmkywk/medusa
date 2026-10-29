package io.github.aw1y2z.sesame.data.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** golden test：TaskCapability 只按服务端字段分类，不靠标题猜。 */
public class TaskCapabilityTest {

    @Test
    public void autoCompleteIsClaim() {
        assertEquals(TaskCapability.Kind.CLAIM,
                TaskCapability.classify("随便什么标题", 0, 0, 0, true));
    }

    @Test
    public void timedTaskWithoutRealActionIsWait() {
        assertEquals(TaskCapability.Kind.WAIT,
                TaskCapability.classify("看视频得奖励", 15, 0, 0, false));
        assertEquals(TaskCapability.Kind.WAIT,
                TaskCapability.classify("逛一逛", 5, 0, 0, false));
    }

    @Test
    public void progressTaskIsRealAction() {
        assertEquals(TaskCapability.Kind.REAL_ACTION,
                TaskCapability.classify("拉新", 0, 3, 0, false));
        assertEquals(TaskCapability.Kind.REAL_ACTION,
                TaskCapability.classify("某进度任务", 0, 0, 2, false));
    }

    @Test
    public void transactionNounIsAlwaysRealActionEvenWithTimeCount() {
        // 4.0.0 修正：交易/履约类名词带 timeCount 也必须是 REAL_ACTION，不能当 WAIT 伪申报
        assertEquals(TaskCapability.Kind.REAL_ACTION,
                TaskCapability.classify("完成订单得积分", 20, 0, 0, false));
        assertEquals(TaskCapability.Kind.REAL_ACTION,
                TaskCapability.classify("去评价一下", 10, 0, 0, false));
        assertEquals(TaskCapability.Kind.REAL_ACTION,
                TaskCapability.classify("点赞助力", 10, 0, 0, false));
        assertEquals(TaskCapability.Kind.REAL_ACTION,
                TaskCapability.classify("充值会员", 0, 0, 0, false));
        assertTrue(TaskCapability.hardRealAction("支付有礼"));
        assertFalse(TaskCapability.hardRealAction("完成浏览任务"));
    }

    @Test
    public void noSignalIsUnknown() {
        assertEquals(TaskCapability.Kind.UNKNOWN,
                TaskCapability.classify("", 0, 0, 0, false));
        assertEquals(TaskCapability.Kind.UNKNOWN,
                TaskCapability.classify(null, 0, 0, 0, false));
    }

    @Test
    public void waitIsLowRiskAndRealActionIsHighRisk() {
        assertEquals(TaskCapability.RiskLevel.LOW,
                TaskCapability.of(TaskCapability.Kind.WAIT, 5).riskLevel);
        assertEquals(TaskCapability.RiskLevel.LOW,
                TaskCapability.of(TaskCapability.Kind.CLAIM, 0).riskLevel);
        assertEquals(TaskCapability.RiskLevel.HIGH,
                TaskCapability.of(TaskCapability.Kind.REAL_ACTION, 0).riskLevel);
        assertEquals(TaskCapability.RiskLevel.MEDIUM,
                TaskCapability.of(TaskCapability.Kind.UNKNOWN, 0).riskLevel);
    }
}