package io.github.aw1y2z.sesame.util.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** golden test：AI 出站脱敏与入站校验（两条边界必须真的挡住东西）。 */
public class AiSafetyTest {

    @Test
    public void redactsLongDigitRuns() {
        String clean = AiSafety.redact("账号 2088000000000000 今天失败 3 次");
        assertFalse(clean.contains("2088000000000000"));
        assertTrue(clean.contains("<num>"));
        assertTrue(clean.contains("失败 3 次"));
    }

    @Test
    public void redactsUrlsToHost() {
        String clean = AiSafety.redact("跳转 https://example.com/path/a?token=xyz 再看");
        assertFalse(clean.contains("token=xyz"));
        assertFalse(clean.contains("/path/a"));
        assertTrue(clean.contains("example.com"));
    }

    @Test
    public void acceptsPlainJsonObject() {
        AiSafety.Validation validation = AiSafety.validate(
                "{\"headline\":\"今天海洋域失败偏多\",\"causes\":[\"1009\"],\"suggestions\":[\"明日观察\"]}");
        assertTrue(validation.reason, validation.valid);
        assertEquals("今天海洋域失败偏多", validation.json.optString("headline"));
    }

    @Test
    public void acceptsFencedJson() {
        AiSafety.Validation validation = AiSafety.validate(
                "```json\n{\"headline\":\"正常\"}\n```");
        assertTrue(validation.reason, validation.valid);
    }

    @Test
    public void rejectsNonJson() {
        assertFalse(AiSafety.validate("今天一切正常。").valid);
        assertFalse(AiSafety.validate("").valid);
        assertFalse(AiSafety.validate(null).valid);
    }

    @Test
    public void rejectsExecutableIntent() {
        AiSafety.Validation validation = AiSafety.validate(
                "{\"headline\":\"建议\",\"suggestions\":[\"自动执行海洋任务\"]}");
        assertFalse(validation.valid);
        assertEquals("含可执行意图", validation.reason);
    }

    @Test
    public void acceptsNegatedExecutableIntent() {
        AiSafety.Validation validation = AiSafety.validate(
                "{\"headline\":\"不执行领取\",\"causes\":[\"不自动执行\",\"未执行\"],"
                        + "\"suggestions\":[\"不建议执行\",\"仅只读复核\"]}");
        assertTrue(validation.reason, validation.valid);
        assertEquals("不执行领取", validation.json.optString("headline"));
    }

    @Test
    public void rejectsLongDigitsInReply() {
        AiSafety.Validation validation = AiSafety.validate("{\"headline\":\"账号2088000000000000异常\"}");
        assertFalse(validation.valid);
    }

    @Test
    public void masksToken() {
        assertEquals("sk-***ab", AiClient.maskToken("sk-abcdefghijab"));
        assertEquals("***", AiClient.maskToken("short"));
        assertEquals("", AiClient.maskToken(""));
    }

    @Test
    public void extractsHostFromBaseUrl() {
        assertEquals("api.example.com", AiClient.hostOf("https://api.example.com/v1"));
        assertEquals("example.com", AiClient.hostOf("example.com/v1"));
        assertEquals("<invalid-url>", AiClient.hostOf(""));
    }

    @Test
    public void notReadyWithoutConfiguration() {
        // 默认关闭且三项为空，必须判定未就绪（不调用）
        assertFalse(AiClient.ready());
        assertFalse(AiClient.describeReadiness().isEmpty());
    }
}