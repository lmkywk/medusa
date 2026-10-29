package io.github.aw1y2z.sesame.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** golden test：1009 冷却升级规则（第一次 30 分钟；1 小时内连续命中升 2h / 8h；间隔超 1 小时重置）。 */
public class RiskCooldownTest {

    private static final long MINUTE = 60_000L;
    /** 真实的“上一轮命中时间”一定大于 0；0 是旧数据的空值哨兵 */
    private static final long T0 = 1_700_000_000_000L;

    @Test
    public void firstStrikeIsThirtyMinutes() {
        RiskCooldown.State state = RiskCooldown.next(null, T0);
        assertEquals(1, state.strikes);
        assertEquals(30L * MINUTE, RiskCooldown.cooldownFor(state.strikes));
    }

    @Test
    public void secondStrikeWithinWindowEscalates() {
        RiskCooldown.State first = RiskCooldown.next(null, T0);
        RiskCooldown.State second = RiskCooldown.next(first, T0 + 10L * MINUTE);
        assertEquals(2, second.strikes);
        assertEquals(2L * 60L * MINUTE, RiskCooldown.cooldownFor(second.strikes));
    }

    @Test
    public void thirdStrikeEscalatesToEightHours() {
        RiskCooldown.State first = RiskCooldown.next(null, T0);
        RiskCooldown.State second = RiskCooldown.next(first, T0 + 10L * MINUTE);
        RiskCooldown.State third = RiskCooldown.next(second, T0 + 20L * MINUTE);
        assertEquals(3, third.strikes);
        assertEquals(8L * 60L * MINUTE, RiskCooldown.cooldownFor(third.strikes));
    }

    @Test
    public void fourthStrikeStaysAtEightHours() {
        RiskCooldown.State state = new RiskCooldown.State(3, T0);
        RiskCooldown.State next = RiskCooldown.next(state, T0 + 5L * MINUTE);
        assertEquals(4, next.strikes);
        assertEquals(8L * 60L * MINUTE, RiskCooldown.cooldownFor(next.strikes));
    }

    @Test
    public void strikeAfterWindowResets() {
        RiskCooldown.State first = RiskCooldown.next(null, T0);
        // 距上次超过 1 小时：新一轮从 30 分钟重新算
        RiskCooldown.State later = RiskCooldown.next(first, T0 + 61L * MINUTE);
        assertEquals(1, later.strikes);
        assertEquals(30L * MINUTE, RiskCooldown.cooldownFor(later.strikes));
    }

    @Test
    public void exactlyOneHourStillCountsAsConsecutive() {
        RiskCooldown.State first = RiskCooldown.next(null, T0);
        RiskCooldown.State later = RiskCooldown.next(first, T0 + 60L * MINUTE);
        assertEquals(2, later.strikes);
    }

    @Test
    public void legacyStateWithoutTimestampStartsNewRound() {
        // 旧数据 pausedAt/lastStrikeAt=0：不能拿它算“连续命中”，按第一轮处理
        RiskCooldown.State legacy = new RiskCooldown.State(2, 0L);
        RiskCooldown.State next = RiskCooldown.next(legacy, T0 + MINUTE);
        assertEquals(1, next.strikes);
    }

    @Test
    public void describeIsHumanReadable() {
        assertEquals("30分钟", RiskCooldown.describe(RiskCooldown.cooldownFor(1)));
        assertEquals("2小时", RiskCooldown.describe(RiskCooldown.cooldownFor(2)));
        assertEquals("8小时", RiskCooldown.describe(RiskCooldown.cooldownFor(3)));
    }
}