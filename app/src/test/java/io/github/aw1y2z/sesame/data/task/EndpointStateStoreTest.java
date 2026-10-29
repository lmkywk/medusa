package io.github.aw1y2z.sesame.data.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 任务端点 → RPC 方法匹配：同端点才允许互相推断通不通。 */
public class EndpointStateStoreTest {

    @Test
    public void suffixAndSimpleNameMatch() {
        assertTrue(EndpointStateStore.endpointMatches("antfarm.cook", "com.alipay.antfarm.cook"));
        assertTrue(EndpointStateStore.endpointMatches("draw", "com.alipay.antfarm.drawMachine"));
        assertTrue(EndpointStateStore.endpointMatches("water", "com.alipay.antfarm.waterFriend"));
        assertTrue(EndpointStateStore.endpointMatches("feedanimal", "com.alipay.antfarm.feedAnimal"));
    }

    @Test
    public void sceneSuffixIsStripped() {
        assertTrue(EndpointStateStore.endpointMatches("doFarmTask:game", "com.alipay.antfarm.doFarmTask"));
        assertTrue(EndpointStateStore.endpointMatches("doFarmTask:bounty", "com.alipay.antfarm.doFarmTask"));
    }

    @Test
    public void differentEndpointsDoNotInfer() {
        assertFalse(EndpointStateStore.endpointMatches("antfarm.cook", "com.alipay.antfarm.feedAnimal"));
        assertFalse(EndpointStateStore.endpointMatches("cleanFriendOcean", "com.alipay.antocean.ocean.h5.cleanOcean"));
        assertFalse(EndpointStateStore.endpointMatches("", "com.alipay.antfarm.cook"));
    }

    @Test
    public void labelsAreHumanReadable() {
        assertTrue(EndpointStateStore.stateLabel(EndpointStateStore.PASS).contains("通过"));
        assertTrue(EndpointStateStore.stateLabel(EndpointStateStore.FAIL).contains("失败"));
        assertTrue(EndpointStateStore.stateLabel("").isEmpty());
    }
}
