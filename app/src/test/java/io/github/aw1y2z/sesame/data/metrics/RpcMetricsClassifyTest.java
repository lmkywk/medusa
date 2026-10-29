package io.github.aw1y2z.sesame.data.metrics;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** golden test：RPC 响应归类（只认服务端字段，不裸匹配整段文本）。 */
public class RpcMetricsClassifyTest {

    @Test
    public void emptyResponseIsEmpty() {
        assertEquals(RpcMetrics.Outcome.EMPTY, RpcMetrics.classify(null));
        assertEquals(RpcMetrics.Outcome.EMPTY, RpcMetrics.classify(""));
        assertEquals(RpcMetrics.Outcome.EMPTY, RpcMetrics.classify("   "));
    }

    @Test
    public void code1009IsServerRefusal() {
        assertEquals(RpcMetrics.Outcome.C1009, RpcMetrics.classify("{\"error\":\"1009\"}"));
        assertEquals(RpcMetrics.Outcome.C1009, RpcMetrics.classify("{\"errorTip\":\"1009\"}"));
        assertEquals(RpcMetrics.Outcome.C1009,
                RpcMetrics.classify("{\"result\":{\"resultCode\":\"1009\"}}"));
    }

    @Test
    public void code1004IsLimit() {
        assertEquals(RpcMetrics.Outcome.C1004, RpcMetrics.classify("{\"errorCode\":\"1004\"}"));
        assertEquals(RpcMetrics.Outcome.C1004, RpcMetrics.classify("{\"error\":\"1004\"}"));
    }

    @Test
    public void retryableCodesCountAsOk() {
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"error\":\"102\"}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"error\":\"100\"}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"error\":\"200\"}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"error\":\"0\"}"));
    }

    @Test
    public void stringSuccessCodeIsOk() {
        // 实测：会员查询成功时 resultCode 是字符串 SUCCESS，早期版本会误判成失败
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify(
                "{\"resultCode\":\"SUCCESS\",\"certList\":[{\"success\":true}]}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify(
                "{\"result\":{\"resultCode\":\"OK\"}}"));
    }

    @Test
    public void stringFailureCodeIsFailure() {
        assertEquals(RpcMetrics.Outcome.FAIL, RpcMetrics.classify("{\"resultCode\":\"FAIL\"}"));
        assertEquals(RpcMetrics.Outcome.FAIL, RpcMetrics.classify("{\"resultCode\":\"ERROR\"}"));
    }

    @Test
    public void nestedSuccessDoesNotMakeFailure() {
        // 顶层没有 success，只有嵌套业务数据里有 success=true，不能因此改判
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify(
                "{\"resultCode\":\"SUCCESS\",\"list\":[{\"success\":true}]}"));
    }

    @Test
    public void topLevelSuccessFalseIsFailure() {
        assertEquals(RpcMetrics.Outcome.FAIL, RpcMetrics.classify("{\"success\":false,\"memo\":\"x\"}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"success\":true,\"memo\":\"x\"}"));
    }

    @Test
    public void otherCodeIsFailure() {
        assertEquals(RpcMetrics.Outcome.FAIL, RpcMetrics.classify("{\"error\":\"400000040\"}"));
        assertEquals(RpcMetrics.Outcome.FAIL, RpcMetrics.classify("{\"resultCode\":\"5000\"}"));
    }

    @Test
    public void successFlagFalseIsFailure() {
        assertEquals(RpcMetrics.Outcome.FAIL, RpcMetrics.classify("{\"success\":false,\"memo\":\"x\"}"));
    }

    @Test
    public void plainSuccessBodyIsOk() {
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"success\":true}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("{\"result\":{\"x\":1}}"));
        assertEquals(RpcMetrics.Outcome.OK, RpcMetrics.classify("not-json-but-present"));
    }

    @Test
    public void methodNameIsTruncatedAtQuery() {
        // 归域用方法名，不允许带参数串
        String scope = RpcMetrics.scopeOf("alipay.antocean.antiep.tasklist.query?foo=bar");
        assertEquals("ocean", scope);
    }
}