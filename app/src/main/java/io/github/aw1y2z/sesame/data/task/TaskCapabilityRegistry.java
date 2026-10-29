package io.github.aw1y2z.sesame.data.task;

import java.util.Locale;

/**
 * 能力注册表 v1（只读分类，不参与执行）：把任务归到「机制 × 端点」，供账本与手动队列分组。
 *
 * <p>推断单元是 {@code classKey = 机制|域|端点}，不是用户看到的任务形态：
 * 同为“资源支出”，做菜走 {@code antfarm.cook}、浇水走 water、施肥走 spreadManure，一个通不代表另一个通。
 *
 * <p>解析顺序固定：账本已有的明确原因 → 交易 → 授权 → 抽机会 → 游戏 → 外跳 → 社交 →
 * 虚拟资源支出 → 答题 → 领取 → 浏览 → 计时 → 进度 → 未分类。命中即停，避免规则互相覆盖。
 */
public final class TaskCapabilityRegistry {

    public enum Mechanism {
        CLAIM, TIMED, BROWSE, SOCIAL, VIRTUAL_SPEND, CHANCE, GAME,
        EXTERNAL_JUMP, AUTH, REAL_ACTION, TRANSACTION, PROGRESS, ANSWER, UNKNOWN
    }

    public enum CostType {
        NONE, ATTENTION, ENERGY, WATER, FERTILIZER, FEED, STEPS, COIN, CHANCE, CASH, UNKNOWN
    }

    public static final class Spec {
        public final Mechanism mechanism;
        public final CostType costType;
        public final String endpoint;
        public final String classKey;

        Spec(Mechanism mechanism, CostType costType, String endpoint, String classKey) {
            this.mechanism = mechanism;
            this.costType = costType;
            this.endpoint = endpoint;
            this.classKey = classKey;
        }
    }

    private static final String[] TRANSACTION_WORDS = {
            "支付", "下单", "充值", "缴费", "还款", "捐款", "筹款", "寄件", "回收", "租车", "打车", "机票", "酒店",
            "pay", "charge", "xiadan", "zhifu", "chongzhi", "jiaofei", "huankuan", "juankuan", "choukuan",
            "kuaidi", "huishou", "zulin", "zuche", "dache", "jipiao", "yuebao"
    };

    private static final String[] AUTH_WORDS = {
            "授权", "绑定", "开通", "实名", "auth"
    };

    private static final String[] CHANCE_WORDS = {
            "抽奖", "抽一", "抽1t", "转盘", "宝箱", "抽卡", "抽抽乐", "秒杀", "抽",
            "draw", "lottery", "chou", "seckill"
    };

    private static final String[] GAME_WORDS = {
            "玩一玩", "小游戏", "游戏", "闯关", "乐园",
            "_llrw_", "leyuan", "xddq", "optionalplay", "game", "youxi"
    };

    private static final String[] EXTERNAL_WORDS = {
            "淘宝", "快手", "京东", "拼多多", "小程序", "外跳", "灯火", "支付宝外",
            "阿福", "百度", "淘金币", "抖音", "微博", "小红书", "夸克", "番茄", "美团", "饿了么", "高德",
            "taobao", "kuaishou", "jingdong", "pinduoduo", "baidu", "douyin", "visit"
    };

    private static final String[] SOCIAL_WORDS = {
            "好友", "帮", "赠送", "送麦子", "合种", "组队", "招募", "点赞", "评价", "召回", "请教",
            "friend", "team"
    };

    private static final String[] ENERGY_WORDS = {"交能量", "能量兑换", "兑保护罩", "保护罩", "energy", "exchange"};
    private static final String[] WATER_WORDS = {"浇水", "浇一", "water"};
    private static final String[] FERTILIZER_WORDS = {"施肥", "肥料", "粪", "manure", "fertilizer", "spread"};
    private static final String[] FEED_WORDS = {"喂", "饲料", "麦子", "feed"};
    private static final String[] STEPS_WORDS = {"步数", "捐步", "步", "step"};
    private static final String[] COIN_WORDS = {"金豆", "积分", "活力值", "加速器", "道具", "coin", "point"};

    private static final String[] REAL_ACTION_WORDS = {"转化", "zhuanhua", "拉新", "开通会员", "锻炼"};
    private static final String[] ANSWER_WORDS = {"答题", "问答", "学知识", "answer"};
    private static final String[] CLAIM_WORDS = {"领取", "领奖", "签到", "礼包", "七日", "奖励", "claim", "sign"};
    private static final String[] BROWSE_WORDS = {
            "逛", "浏览", "商城", "页面", "看看", "去看", "科普", "冷知识", "剧情", "专场", "列表",
            "项目", "browse"
    };
    private static final String[] TIMED_WORDS = {
            "看视频", "观看", "停留", "秒", "看", "直播", "watch", "video", "viedo"
    };

    private TaskCapabilityRegistry() {
    }

    /** 带场景码：同端点不同玩法（乐园/玩一玩/XDDQ）不再共用一个类键。 */
    public static Spec resolve(String domain, String id, String title, String manualReason,
                               int progressCurrent, int progressTotal, String sceneCode) {
        Spec base = resolve(domain, id, title, manualReason, progressCurrent, progressTotal);
        String scene = sceneCode == null ? "" : sceneCode.trim();
        if (scene.isEmpty()) {
            return base;
        }
        return new Spec(base.mechanism, base.costType, base.endpoint, base.classKey + "#" + scene);
    }

    /** 段级动作（收能量/浇水/巡护等）用的类规格。 */
    public static Spec specOf(Mechanism mechanism, CostType costType, String section, String domain) {
        return spec(mechanism, costType, section, domain == null ? "" : domain);
    }

    public static Spec resolve(String domain, String id, String title, String manualReason,
                               int progressCurrent, int progressTotal) {
        String dom = domain == null ? "" : domain;
        String taskId = id == null ? "" : id;
        String text = (taskId + " " + (title == null ? "" : title)).toLowerCase(Locale.ROOT);
        String endpoint = endpointOf(taskId, dom);
        String reason = manualReason == null ? "" : manualReason;

        Mechanism explicit = mechanismOfReason(reason);
        if (explicit != null) {
            CostType cost = explicit == Mechanism.TRANSACTION ? CostType.CASH : CostType.NONE;
            return spec(explicit, cost, endpoint, dom);
        }
        if (containsAny(text, TRANSACTION_WORDS)) {
            return spec(Mechanism.TRANSACTION, CostType.CASH, endpoint, dom);
        }
        if (containsAny(text, AUTH_WORDS)) {
            return spec(Mechanism.AUTH, CostType.NONE, endpoint, dom);
        }
        if (containsAny(text, CHANCE_WORDS)) {
            return spec(Mechanism.CHANCE, CostType.CHANCE, endpoint, dom);
        }
        if (containsAny(text, GAME_WORDS)) {
            return spec(Mechanism.GAME, CostType.NONE, endpoint, dom);
        }
        if (containsAny(text, EXTERNAL_WORDS)) {
            return spec(Mechanism.EXTERNAL_JUMP, CostType.NONE, endpoint, dom);
        }
        // 先判“动词”：领取/浏览/计时说明用户怎么完成，成本只是附带（领肥料不是花肥料）
        if (containsAny(text, CLAIM_WORDS)) {
            return spec(Mechanism.CLAIM, CostType.NONE, endpoint, dom);
        }
        if (containsAny(text, BROWSE_WORDS)) {
            return spec(Mechanism.BROWSE, CostType.ATTENTION, endpoint, dom);
        }
        if (containsAny(text, TIMED_WORDS)) {
            return spec(Mechanism.TIMED, CostType.ATTENTION, endpoint, dom);
        }
        // 没有明确动词时才按成本归类；支出先于社交：water_friend / 送麦子同时含社交词，成本更关键
        if (containsAny(text, ENERGY_WORDS)) {
            return spec(Mechanism.VIRTUAL_SPEND, CostType.ENERGY, endpoint, dom);
        }
        if (containsAny(text, WATER_WORDS)) {
            return spec(Mechanism.VIRTUAL_SPEND, CostType.WATER, endpoint, dom);
        }
        if (containsAny(text, FERTILIZER_WORDS)) {
            return spec(Mechanism.VIRTUAL_SPEND, CostType.FERTILIZER, endpoint, dom);
        }
        if (containsAny(text, FEED_WORDS)) {
            return spec(Mechanism.VIRTUAL_SPEND, CostType.FEED, endpoint, dom);
        }
        if (containsAny(text, STEPS_WORDS)) {
            return spec(Mechanism.VIRTUAL_SPEND, CostType.STEPS, endpoint, dom);
        }
        if (containsAny(text, COIN_WORDS)) {
            return spec(Mechanism.VIRTUAL_SPEND, CostType.COIN, endpoint, dom);
        }
        if (containsAny(text, REAL_ACTION_WORDS)) {
            return spec(Mechanism.REAL_ACTION, CostType.NONE, endpoint, dom);
        }
        if (containsAny(text, SOCIAL_WORDS)) {
            return spec(Mechanism.SOCIAL, CostType.NONE, endpoint, dom);
        }
        if (containsAny(text, ANSWER_WORDS)) {
            return spec(Mechanism.ANSWER, CostType.ATTENTION, endpoint, dom);
        }
        if (progressTotal > 0 || progressCurrent > 0) {
            return spec(Mechanism.PROGRESS, CostType.NONE, endpoint, dom);
        }
        return spec(Mechanism.UNKNOWN, CostType.UNKNOWN, endpoint, dom);
    }

    /** 账本里已有的明确原因优先，保证与既有判定一致。 */
    private static Mechanism mechanismOfReason(String reason) {
        switch (reason) {
            case "TRANSACTION":
                return Mechanism.TRANSACTION;
            case "REAL_GAMEPLAY":
                return Mechanism.GAME;
            case "EXTERNAL_JUMP":
                return Mechanism.EXTERNAL_JUMP;
            case "AUTH_REQUIRED":
                return Mechanism.AUTH;
            case "BOUNTY_CARD":
            case "REAL_ACTION":
                return Mechanism.REAL_ACTION;
            default:
                return null;
        }
    }

    /** 端点族：真正决定通不通的接口，按 taskType/taskId 的稳定前缀推导。 */
    private static String endpointOf(String id, String domain) {
        String key = id == null ? "" : id.toUpperCase(Locale.ROOT);
        if (key.contains("COOK")) {
            return "antfarm.cook";
        }
        if (key.contains("FRIENDRUBBISCLEAN")) {
            return "cleanFriendOcean";
        }
        if (key.contains("ENERGY_XUANJIAO")) {
            return "doFarmTask:bounty";
        }
        if (key.contains("SPREAD")) {
            return "spreadManure";
        }
        if (key.contains("_LLRW_") || key.contains("GAME")) {
            return "doFarmTask:game";
        }
        if (key.contains("DRAW") || key.contains("CHOU") || key.contains("LOTTERY")) {
            return "draw";
        }
        if (key.contains("WATER")) {
            return "water";
        }
        if (key.contains("FEED")) {
            return "feedAnimal";
        }
        if (!key.isEmpty()) {
            return key.toLowerCase(Locale.ROOT);
        }
        return (domain == null || domain.isEmpty() ? "unknown" : domain) + ":unknown";
    }

    private static Spec spec(Mechanism mechanism, CostType cost, String endpoint, String domain) {
        String key = mechanism.name() + "|" + domain + "|" + endpoint;
        return new Spec(mechanism, cost, endpoint, key);
    }

    private static boolean containsAny(String text, String[] words) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    public static String mechanismOfClassKey(String classKey) {
        if (classKey == null) {
            return "";
        }
        int cut = classKey.indexOf('|');
        return cut < 0 ? classKey : classKey.substring(0, cut);
    }

    public static String mechanismLabel(String mechanism) {
        if (mechanism == null || mechanism.isEmpty()) {
            return "未分类";
        }
        try {
            return mechanismLabel(Mechanism.valueOf(mechanism));
        } catch (Throwable ignored) {
            return mechanism;
        }
    }

    public static String mechanismLabel(Mechanism mechanism) {
        switch (mechanism) {
            case CLAIM:
                return "领取";
            case TIMED:
                return "计时";
            case BROWSE:
                return "浏览";
            case SOCIAL:
                return "社交";
            case VIRTUAL_SPEND:
                return "资源支出";
            case CHANCE:
                return "抽机会";
            case GAME:
                return "游戏";
            case EXTERNAL_JUMP:
                return "外跳";
            case AUTH:
                return "授权";
            case REAL_ACTION:
                return "真实动作";
            case TRANSACTION:
                return "交易";
            case PROGRESS:
                return "进度";
            case ANSWER:
                return "答题";
            default:
                return "未分类";
        }
    }

    public static String costLabel(String costType) {
        if (costType == null || costType.isEmpty()) {
            return "";
        }
        try {
            return costLabel(CostType.valueOf(costType));
        } catch (Throwable ignored) {
            return costType;
        }
    }

    public static String costLabel(CostType costType) {
        switch (costType) {
            case ATTENTION:
                return "耗时";
            case ENERGY:
                return "能量";
            case WATER:
                return "水";
            case FERTILIZER:
                return "肥料";
            case FEED:
                return "饲料";
            case STEPS:
                return "步数";
            case COIN:
                return "金豆/积分";
            case CHANCE:
                return "次数";
            case CASH:
                return "现金";
            case UNKNOWN:
                return "未知消耗";
            case NONE:
            default:
                return "";
        }
    }
}
