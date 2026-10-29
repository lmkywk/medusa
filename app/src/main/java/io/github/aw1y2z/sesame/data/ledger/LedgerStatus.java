package io.github.aw1y2z.sesame.data.ledger;

/** 账本归一化状态：只表达“今天这项任务处于哪一步”，不替代服务端原始 taskStatus。 */
public enum LedgerStatus {
    UNTOUCHED("未做"),
    IN_PROGRESS("进行中"),
    CLAIMABLE("已完成待领"),
    CLAIMED("已领"),
    MANUAL("需手动"),
    SKIPPED("已跳过");

    public final String label;

    LedgerStatus(String label) {
        this.label = label;
    }

    public boolean isPending() {
        return this == UNTOUCHED || this == IN_PROGRESS || this == CLAIMABLE;
    }

    public static LedgerStatus fromName(String name) {
        if (name == null) {
            return UNTOUCHED;
        }
        for (LedgerStatus status : values()) {
            if (status.name().equals(name)) {
                return status;
            }
        }
        return UNTOUCHED;
    }
}
