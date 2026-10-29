package io.github.aw1y2z.sesame.data.ledger;

/**
 * 4.0.0：账本跨轮合并的纯函数，供 TaskLedgerStore 与 golden test 共用。
 * <p>规则（3.10.0 起的产品语义，这里只是把它从 IO 代码里抽出来）：
 * <ul>
 *   <li>手动状态跨轮保留：上轮是 MANUAL，本轮服务端仍待办时不吞回 UNTOUCHED；</li>
 *   <li>但如果服务端已经变成已领/可领/已跳过，说明任务真的推进了，解除手动；</li>
 *   <li>用户点过“我已完成”的时间要保留，并按是否仍待办区分 PENDING/UNCONFIRMED；</li>
 *   <li>域、跳转链接、首次出现时间这类“服务端不给、只能本地继承”的字段沿用上一轮。</li>
 * </ul>
 */
public final class LedgerMerge {

    private LedgerMerge() {
    }

    public static LedgerTask merge(LedgerTask previous, LedgerTask incoming, String domain) {
        if (incoming == null) {
            return null;
        }
        if (incoming.domain == null || incoming.domain.isEmpty()) {
            incoming.domain = domain == null ? "" : domain;
        }
        if (previous == null) {
            return incoming;
        }
        if (incoming.actionUrl == null || incoming.actionUrl.isEmpty()) {
            incoming.actionUrl = previous.actionUrl;
        }
        if (incoming.userDoneAt == 0L) {
            incoming.userDoneAt = previous.userDoneAt;
        }
        if (incoming.firstSeenAt <= 0L) {
            incoming.firstSeenAt = previous.firstSeenAt;
        }
        if (previous.status == LedgerStatus.MANUAL
                && incoming.status != LedgerStatus.CLAIMED
                && incoming.status != LedgerStatus.CLAIMABLE
                && incoming.status != LedgerStatus.SKIPPED) {
            boolean captureStillTodo = incoming.status != LedgerStatus.MANUAL;
            incoming.status = LedgerStatus.MANUAL;
            if (incoming.userDoneAt > 0L && captureStillTodo) {
                incoming.manualReason = "USER_DONE_UNCONFIRMED";
            } else if (incoming.userDoneAt > 0L) {
                incoming.manualReason = "USER_DONE_PENDING";
            } else {
                incoming.manualReason = previous.manualReason == null ? "" : previous.manualReason;
            }
        }
        return incoming;
    }
}