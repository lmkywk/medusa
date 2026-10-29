package io.github.aw1y2z.sesame.data.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 可被“计划”调度的执行目标。目标是域，不是单个开关项。 */
public final class PlanTargets {

    public static final String FOREST_COLLECT = "forest.collect";
    public static final String FOREST_HUNT = "forest.hunt";
    public static final String FOREST_IP = "forest.ip";
    /** 森林日常：领奖励/广场/乐园/巡护/背包，跑全量分支但跳过收能量与寻宝。 */
    public static final String FOREST_DAILY = "forest.daily";
    /** 保护地巡护：只跑旧版巡护、新版保护地（含任务）与动物伙伴，供手动直达。 */
    public static final String FOREST_PATROL = "forest.patrol";
    /** 森林领奖励：签到/任务列表/已完成领奖/兑换/医疗/装扮，短批优先。 */
    public static final String FOREST_REWARD = "forest.reward";
    /** 森林乐园：乐园限定任务（不含游戏进度）。 */
    public static final String FOREST_PARK = "forest.park";
    /** 森林乐园游戏：单独的游戏进度，避免 6/6 进度拖住限定任务。 */
    public static final String FOREST_GAME = "forest.game";
    /** 无纸阅读：单独成目标，避免 8 分钟阅读拖住整段森林日常。 */
    public static final String FOREST_READ = "forest.read";
    public static final String FOREST_EXTERNAL = "forest.external";
    /** 旧庄园全量目标：保留常量给风控/内部兼容，但不再出现在计划列表里。 */
    public static final String FARM = "farm";
    public static final String FARM_DAILY = "farm.daily";
    public static final String FARM_TASK = "farm.task";
    public static final String FARM_DONATE = "farm.donate";
    public static final String FARM_PARK = "farm.park";
    public static final String FARM_DRAW = "farm.draw";
    public static final String FARM_COMPETITION = "farm.competition";
    public static final String FARM_FAMILY = "farm.family";
    public static final String FARM_FRIEND = "farm.friend";
    public static final String FARM_KITCHEN = "farm.kitchen";
    public static final String FARM_LIFE = "farm.life";
    public static final String FARM_SUNFLOWER = "farm.sunflower";
    public static final String FARM_EXTERNAL = "farm.external";
    /** 旧海洋全量目标：保留常量给风控/内部兼容，但不再出现在计划列表里。 */
    public static final String OCEAN = "ocean";
    public static final String OCEAN_REWARD = "ocean.reward";
    public static final String OCEAN_CLEAN = "ocean.clean";
    public static final String OCEAN_FISH = "ocean.fish";
    public static final String OCEAN_WELCOME = "ocean.welcome";
    public static final String OCEAN_EXTERNAL = "ocean.external";
    public static final String MEMBER = "member";
    public static final String MEMBER_EXTERNAL = "member.external";
    public static final String SPORTS = "sports";
    public static final String SPORTS_EXTERNAL = "sports.external";
    public static final String KINETIC = "sports.kinetic";
    public static final String ANCIENT = "ancient";
    public static final String PROTECT = "protect";
    public static final String DODO = "dodo";
    public static final String SPECIES_DRAW = "species.draw";
    public static final String SPECIES_FRIEND = "species.friend";
    public static final String SPECIES_TASK = "species.task";
    public static final String SPECIES_PROP = "species.prop";
    public static final String SPECIES_BOOK = "species.book";
    public static final String SPECIES_GIFT = "species.gift";
    public static final String STALL = "stall";
    /** 果园日常全量：兼容旧计划，也作为首页卡片的一键执行入口。 */
    public static final String ORCHARD = "orchard";
    /** 果园日常：任务/签到/乐园/寻宝/挑战/工厂/助力/游戏试玩，不含肥料包与施肥。 */
    public static final String ORCHARD_DAILY = "orchard.daily";
    public static final String ORCHARD_FERTILIZER = "orchard.fertilizer";
    public static final String ORCHARD_TASK = "orchard.task";
    public static final String ORCHARD_SIGN = "orchard.sign";
    public static final String ORCHARD_SPREAD = "orchard.spread";
    public static final String ORCHARD_PARK = "orchard.park";
    public static final String ORCHARD_ASSIST = "orchard.assist";
    public static final String ORCHARD_DRAW = "orchard.draw";
    public static final String ORCHARD_CHALLENGE = "orchard.challenge";
    public static final String ORCHARD_FACTORY = "orchard.factory";
    public static final String ORCHARD_GAMESTAY = "orchard.gamestay";
    public static final String GREEN = "green";
    public static final String GOLDEN = "golden";
    public static final String ANSWER_AI = "answerAI";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(FOREST_COLLECT, "森林·收能量");
        LABELS.put(FOREST_HUNT, "森林·寻宝/抽1T");
        LABELS.put(FOREST_IP, "森林·IP联名馆");
        LABELS.put(FOREST_DAILY, "森林·日常");
        LABELS.put(FOREST_PATROL, "森林·保护地巡护");
        LABELS.put(FOREST_REWARD, "森林·领奖励");
        LABELS.put(FOREST_PARK, "森林·乐园宝箱");
        LABELS.put(FOREST_GAME, "森林·乐园游戏");
        LABELS.put(FOREST_READ, "森林·无纸阅读");
        LABELS.put(FOREST_EXTERNAL, "森林·外部页面");
        LABELS.put(FARM, "庄园·全量");
        LABELS.put(FARM_DAILY, "庄园·日常");
        LABELS.put(FARM_TASK, "庄园·饲料任务");
        LABELS.put(FARM_DONATE, "庄园·每日捐蛋");
        LABELS.put(FARM_PARK, "庄园·小鸡乐园");
        LABELS.put(FARM_DRAW, "庄园·装扮抽抽乐");
        LABELS.put(FARM_COMPETITION, "庄园·爱心鸡结号");
        LABELS.put(FARM_FAMILY, "庄园·亲密家庭");
        LABELS.put(FARM_FRIEND, "庄园·好友互动");
        LABELS.put(FARM_KITCHEN, "庄园·小鸡厨房");
        LABELS.put(FARM_LIFE, "庄园·小鸡生活");
        LABELS.put(FARM_SUNFLOWER, "庄园·加油小葵");
        LABELS.put(FARM_EXTERNAL, "庄园·外部页面");
        LABELS.put(OCEAN, "海洋·全量");
        LABELS.put(OCEAN_REWARD, "海洋·奖励");
        LABELS.put(OCEAN_CLEAN, "海洋·清理");
        LABELS.put(OCEAN_FISH, "海洋·摸鱼");
        LABELS.put(OCEAN_WELCOME, "海洋·迎回");
        LABELS.put(OCEAN_EXTERNAL, "海洋·外部页面");
        LABELS.put(MEMBER, "会员");
        LABELS.put(MEMBER_EXTERNAL, "会员·外部页面");
        LABELS.put(SPORTS, "医疗健康·运动");
        LABELS.put(SPORTS_EXTERNAL, "运动·外部页面");
        LABELS.put(ANCIENT, "古树");
        LABELS.put(PROTECT, "保护");
        LABELS.put(DODO, "神奇物种");
        LABELS.put(SPECIES_DRAW, "物种·每日抽卡");
        LABELS.put(SPECIES_FRIEND, "物种·好友抽卡");
        LABELS.put(SPECIES_TASK, "物种·任务");
        LABELS.put(SPECIES_PROP, "物种·使用道具");
        LABELS.put(SPECIES_BOOK, "物种·图鉴勋章");
        LABELS.put(SPECIES_GIFT, "物种·赠送卡片");
        LABELS.put(STALL, "摊位");
        LABELS.put(ORCHARD, "果园·全量");
        LABELS.put(ORCHARD_DAILY, "果园·日常");
        LABELS.put(ORCHARD_FERTILIZER, "果园·每日肥料");
        LABELS.put(ORCHARD_TASK, "果园·肥料任务");
        LABELS.put(ORCHARD_SIGN, "果园·每日签到");
        LABELS.put(ORCHARD_SPREAD, "果园·自动施肥");
        LABELS.put(ORCHARD_PARK, "果园·农场乐园");
        LABELS.put(ORCHARD_ASSIST, "果园·好友助力");
        LABELS.put(ORCHARD_DRAW, "果园·阿肥寻宝记");
        LABELS.put(ORCHARD_CHALLENGE, "果园·限时挑战");
        LABELS.put(ORCHARD_FACTORY, "果园·肥料工厂");
        LABELS.put(ORCHARD_GAMESTAY, "果园·游戏试玩");
        LABELS.put(GREEN, "绿色金融");
        LABELS.put(GOLDEN, "金豆");
        LABELS.put(ANSWER_AI, "AI答题");
    }

    private PlanTargets() {
    }

    public static List<String> all() {
        // 巡护是手动直达目标：不进晚间整域兜底，避免数据期内悄悄改变夜间节奏
        List<String> targets = new ArrayList<>(LABELS.keySet());
        targets.remove(FOREST_PATROL);
        return targets;
    }

    /** 临时全量测试：覆盖每个域的主执行目标；森林拆成收能量/日常/寻宝/IP 四段。 */
    public static List<String> fullTestTargets() {
        List<String> targets = new ArrayList<>();
        targets.add(FOREST_COLLECT);
        targets.add(FOREST_DAILY);
        targets.add(FOREST_PATROL);
        targets.add(FOREST_HUNT);
        targets.add(FOREST_IP);
        targets.add(FARM);
        targets.add(OCEAN);
        targets.add(ORCHARD);
        targets.add(DODO);
        targets.add(GOLDEN);
        targets.add(SPORTS);
        targets.add(STALL);
        targets.add(PROTECT);
        targets.add(GREEN);
        targets.add(MEMBER);
        targets.add(ANCIENT);
        return targets;
    }

    /** 测试模式：只读/安全 → 日常 → 高风险，覆盖全部域；不含已删除的6秒拼手速。 */
    public static List<String> testTargets() {
        List<String> targets = new ArrayList<>();
        targets.add(PROTECT);
        targets.add(GREEN);
        targets.add(MEMBER);
        targets.add(ANCIENT);
        targets.add(SPECIES_TASK);
        targets.add(OCEAN_CLEAN);
        targets.add(ORCHARD_FERTILIZER);
        targets.add(FARM_DRAW);
        targets.add(FOREST_HUNT);
        targets.add(FOREST_IP);
        targets.add(FOREST_COLLECT);
        targets.add(FOREST_DAILY);
        targets.add(FARM);
        targets.add(OCEAN);
        targets.add(ORCHARD);
        targets.add(DODO);
        targets.add(GOLDEN);
        targets.add(SPORTS);
        targets.add(STALL);
        return targets;
    }

    public static String labelOf(String target) {
        String label = LABELS.get(target);
        return label == null ? target : label;
    }

    public static boolean isValidTarget(String target) {
        return target != null && LABELS.containsKey(target);
    }

    public static boolean isForest(String target) {
        return target != null && target.startsWith("forest.");
    }

    /** HH:mm，00:00-23:59；计划页只允许这种格式，避免旧配置里的 065530 混进来。 */
    public static boolean isValidTime(String time) {
        if (time == null || time.length() != 5 || time.charAt(2) != ':') {
            return false;
        }
        try {
            int hour = Integer.parseInt(time.substring(0, 2));
            int minute = Integer.parseInt(time.substring(3, 5));
            return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
