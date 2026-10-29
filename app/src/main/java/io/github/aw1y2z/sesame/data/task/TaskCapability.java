package io.github.aw1y2z.sesame.data.task;

/**
 * 3.10.0 任务能力描述 v1：只做分类，不执行。
 * <p>订单/评价/点赞/转化/闯关等真实操作永远不伪申报；看/逛/玩且带 timeCount 的任务等待后上报；
 * autoCompleteTask 直接领取。后续新任务先按这套字段描述，再决定由哪个模块执行。
 */
public final class TaskCapability {

    public enum Kind {
        /** 服务端已自动完成或标记自动完成，直接领 */
        CLAIM,
        /** 看/逛/玩类，按 timeCount 等待后上报 */
        WAIT,
        /** 完成订单/评价/点赞/转化/闯关等真实操作，转手动 */
        REAL_ACTION,
        /** 任务已消失 */
        GONE,
        /** 字段不足，保守转手动 */
        UNKNOWN
    }

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    public final Kind kind;
    public final long waitSeconds;
    public final RiskLevel riskLevel;
    public final String reason;

    private TaskCapability(Kind kind, long waitSeconds, RiskLevel riskLevel, String reason) {
        this.kind = kind;
        this.waitSeconds = waitSeconds;
        this.riskLevel = riskLevel;
        this.reason = reason;
    }

    public static TaskCapability of(Kind kind, long waitSeconds) {
        RiskLevel risk = kind == Kind.WAIT || kind == Kind.CLAIM ? RiskLevel.LOW
                : kind == Kind.REAL_ACTION ? RiskLevel.HIGH : RiskLevel.MEDIUM;
        return new TaskCapability(kind, Math.max(0L, waitSeconds), risk, "");
    }

    /** 按服务端下发字段分类，不靠标题。 */
    public static Kind classify(String content, long timeCount, int taskRequire,
                                int taskProgress, boolean autoComplete) {
        if (autoComplete) {
            return Kind.CLAIM;
        }
        String text = content == null ? "" : content;
        // 4.0.0：交易/履约类名词先判，避免“完成订单”这类带 timeCount 的任务被当成 WAIT 伪申报。
        // “完成”只是修饰词（“完成浏览任务”确实是 WAIT），所以这里只认交易/履约名词。
        if (hardRealAction(text)) {
            return Kind.REAL_ACTION;
        }
        boolean realActionWord = text.contains("完成") || text.contains("订单") || text.contains("评价")
                || text.contains("点赞") || text.contains("转化") || text.contains("闯关")
                || text.contains("招募") || text.contains("充值") || text.contains("支付");
        if (timeCount > 0 && !realActionWord) {
            return Kind.WAIT;
        }
        if (timeCount > 0 && (text.contains("看") || text.contains("逛") || text.contains("玩"))) {
            return Kind.WAIT;
        }
        if (taskRequire > 0 || taskProgress > 0) {
            return Kind.REAL_ACTION;
        }
        if (timeCount > 0) {
            return Kind.WAIT;
        }
        return Kind.UNKNOWN;
    }

    /** 交易/履约类：必须真实操作或转手动，任何情况下都不伪申报。 */
    public static boolean hardRealAction(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("订单") || text.contains("评价") || text.contains("点赞")
                || text.contains("转化") || text.contains("闯关") || text.contains("招募")
                || text.contains("充值") || text.contains("支付");
    }
}
