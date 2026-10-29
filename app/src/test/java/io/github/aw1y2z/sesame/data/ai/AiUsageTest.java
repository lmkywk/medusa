package io.github.aw1y2z.sesame.data.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** golden test：AI 用量估算口径与「万 / 百万」显示。 */
public class AiUsageTest {

    @Test
    public void estimateCountsHanziAsOneToken() {
        assertEquals(6L, AiUsage.estimateTokens("今天海洋失败"));
        assertEquals(0L, AiUsage.estimateTokens(""));
        assertEquals(0L, AiUsage.estimateTokens(null));
    }

    @Test
    public void estimateCountsOtherCharsAsQuarterToken() {
        // 12 个非汉字字符 → 3 tokens（向上取整）
        assertEquals(3L, AiUsage.estimateTokens("abcdefghijkl"));
        assertEquals(1L, AiUsage.estimateTokens("ab"));
    }

    @Test
    public void mixedTextAddsUp() {
        // 2 汉字 + 8 字符 = 2 + 2 = 4
        assertEquals(4L, AiUsage.estimateTokens("今日abcdefgh"));
    }

    @Test
    public void formatsSmallAndLargeAmounts() {
        assertEquals("999", AiUsage.formatTokens(999));
        assertEquals("9,999", AiUsage.formatTokens(9_999));
        assertEquals("1.2 万", AiUsage.formatTokens(12_000));
        assertEquals("12.3 万", AiUsage.formatTokens(123_456));
        assertEquals("123.5 万", AiUsage.formatTokens(1_234_567));
        assertEquals("1.20 亿", AiUsage.formatTokens(120_000_000));
    }

    @Test
    public void monthlyEstimateUsesHundredThousandsScale() {
        String text = AiUsage.estimateMonthly(1, 10, 1850L, 160L);
        assertTrue(text, text.contains("每天约"));
        assertTrue(text, text.contains("每月约"));
        // 每天 1850 + 1600 = 3450 → 每月约 10.35 万
        assertTrue(text, text.contains("万 tokens"));
    }
}