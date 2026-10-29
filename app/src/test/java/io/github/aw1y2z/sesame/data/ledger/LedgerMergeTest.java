package io.github.aw1y2z.sesame.data.ledger;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** golden test：手动队列状态跨轮合并规则。 */
public class LedgerMergeTest {

    private static LedgerTask task(String id, LedgerStatus status) {
        LedgerTask task = new LedgerTask();
        task.id = id;
        task.title = "任务" + id;
        task.status = status;
        return task;
    }

    @Test
    public void manualSurvivesWhenServerStillTodo() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        previous.manualReason = "需要真实操作";
        LedgerTask incoming = task("a", LedgerStatus.UNTOUCHED);

        LedgerMerge.merge(previous, incoming, "ocean.fish");

        assertEquals(LedgerStatus.MANUAL, incoming.status);
        assertEquals("需要真实操作", incoming.manualReason);
        assertEquals("ocean.fish", incoming.domain);
    }

    @Test
    public void manualReleasedWhenServerClaimed() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        LedgerTask incoming = task("a", LedgerStatus.CLAIMED);

        LedgerMerge.merge(previous, incoming, "farm");

        assertEquals(LedgerStatus.CLAIMED, incoming.status);
    }

    @Test
    public void manualReleasedWhenServerClaimable() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        LedgerTask incoming = task("a", LedgerStatus.CLAIMABLE);

        LedgerMerge.merge(previous, incoming, "farm");

        assertEquals(LedgerStatus.CLAIMABLE, incoming.status);
    }

    @Test
    public void userDonePendingWhenManualStillCaptured() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        previous.userDoneAt = 111L;
        LedgerTask incoming = task("a", LedgerStatus.MANUAL);

        LedgerMerge.merge(previous, incoming, "farm");

        assertEquals(LedgerStatus.MANUAL, incoming.status);
        assertEquals(111L, incoming.userDoneAt);
        assertEquals("USER_DONE_PENDING", incoming.manualReason);
    }

    @Test
    public void userDoneUnconfirmedWhenServerBackToTodo() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        previous.userDoneAt = 111L;
        LedgerTask incoming = task("a", LedgerStatus.UNTOUCHED);

        LedgerMerge.merge(previous, incoming, "farm");

        assertEquals(LedgerStatus.MANUAL, incoming.status);
        assertEquals("USER_DONE_UNCONFIRMED", incoming.manualReason);
    }

    @Test
    public void localOnlyFieldsInherited() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        previous.actionUrl = "alipays://platformapi/startapp?appId=x";
        previous.firstSeenAt = 999L;
        previous.userDoneAt = 5L;
        LedgerTask incoming = task("a", LedgerStatus.UNTOUCHED);
        incoming.firstSeenAt = 0L;

        LedgerMerge.merge(previous, incoming, "farm");

        assertEquals("alipays://platformapi/startapp?appId=x", incoming.actionUrl);
        assertEquals(999L, incoming.firstSeenAt);
        assertEquals(5L, incoming.userDoneAt);
    }

    @Test
    public void incomingFieldsWinWhenPresent() {
        LedgerTask previous = task("a", LedgerStatus.MANUAL);
        previous.actionUrl = "old";
        previous.firstSeenAt = 1L;
        LedgerTask incoming = task("a", LedgerStatus.UNTOUCHED);
        incoming.actionUrl = "new";
        incoming.firstSeenAt = 2L;

        LedgerMerge.merge(previous, incoming, "farm");

        assertEquals("new", incoming.actionUrl);
        assertEquals(2L, incoming.firstSeenAt);
    }

    @Test
    public void newTaskGetsDomainAndNoManual() {
        LedgerTask incoming = task("b", LedgerStatus.UNTOUCHED);

        LedgerMerge.merge(null, incoming, "orchard");

        assertEquals("orchard", incoming.domain);
        assertEquals(LedgerStatus.UNTOUCHED, incoming.status);
    }

    @Test
    public void nullIncomingIsIgnored() {
        assertEquals(null, LedgerMerge.merge(task("a", LedgerStatus.MANUAL), null, "farm"));
    }
}