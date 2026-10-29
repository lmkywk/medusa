package io.github.aw1y2z.sesame.data;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/**
 * 默认配置预设：几乎全开（安全任务 + 资源支出），但保留三条硬边界：
 * 1) 支付/交易/充值/捐赠/购买类一律关闭；
 * 2) 依赖好友列表但列表为空的动作关闭（帮喂小鸡例外：会回退到抓取好友）；
 * 3) 依赖数字/选项列表但列表为空的动作关闭。
 * 只在配置首次加载时应用一次，之后用户手动改开的字段不会被反复覆盖。
 */
public final class ConfigPreset {

    public static final int VERSION = 3;

    private ConfigPreset() {
    }

    public static void apply(ConfigV2 config) {
        if (config == null) {
            return;
        }
        for (Map.Entry<String, ModelFields> modelEntry : config.getModelFieldsMap().entrySet()) {
            String model = modelEntry.getKey();
            ModelFields fields = modelEntry.getValue();
            if (fields == null) {
                continue;
            }
            for (ModelField<?> field : fields.values()) {
                if (field == null) {
                    continue;
                }
                String code = field.getCode() == null ? "" : field.getCode();
                if (!"BOOLEAN".equals(field.getType())) {
                    continue;
                }
                boolean enable = !isExcluded(code) && !dependencyMissing(config, model, code);
                if ("AntFarm".equals(model) && "enableSleep".equals(code)) {
                    // 睡觉是影响产蛋节奏的模式开关，保持默认关闭。
                    enable = false;
                }
                field.setObjectValue(enable);
            }
        }

        // 特殊安全值：特殊饲料保留 10 个上限，避免一次清空库存触发 1009。
        ModelField<?> specialFood = field(config, "AntFarm", "useSpecialFood");
        if (specialFood != null) {
            specialFood.setObjectValue(true);
        }
        ModelField<?> specialFoodLimit = field(config, "AntFarm", "useSpecialFoodCountLimit");
        if (specialFoodLimit != null) {
            Object value = specialFoodLimit.getValue();
            if (!(value instanceof Number) || ((Number) value).intValue() <= 0) {
                specialFoodLimit.setObjectValue(10);
            }
        }
        // 帮喂小鸡保留开启：未选好友时运行期会用抓取好友兜底。
        ModelField<?> feedFriend = field(config, "AntFarm", "feedFriendAnimal");
        if (feedFriend != null) {
            feedFriend.setObjectValue(true);
        }

        // 需要好友/数字但没有配置的选择项回落到“不做”。
        if (isEmpty(config, "AntFarm", "hireAnimalList")) {
            setChoice(config, "AntFarm", "hireAnimalType", 0);
        }
        if (isEmpty(config, "AntFarm", "sendFriendList")) {
            setChoice(config, "AntFarm", "sendBackAnimalType", 0);
        }
        if (isEmpty(config, "AntFarm", "notifyFriendList")) {
            setChoice(config, "AntFarm", "notifyFriendType", 0);
        }
        // 捐赠类显式回落为不捐。
        setChoice(config, "AntFarm", "donationType", 0);
    }

    private static boolean dependencyMissing(ConfigV2 config, String model, String code) {
        if ("AntFarm".equals(model)) {
            switch (code) {
                case "useAccelerateTool":
                    return isEmpty(config, model, "useAccelerateToolOptions");
                case "ornamentsDressUp":
                    return isEmpty(config, model, "ornamentsDressUpList");
                case "family":
                    return isEmpty(config, model, "familyOptions");
                case "gameCenterBuyMallItem":
                    return isEmpty(config, model, "gameCenterBuyMallItemList");
                case "feedFriendAnimal":
                    // 未选好友时运行期用抓取好友，故不算缺依赖。
                    return false;
                default:
                    return false;
            }
        }
        if ("AntMember".equals(model) && "memberPointExchangeBenefit".equals(code)) {
            return isEmpty(config, model, "memberPointExchangeBenefitList");
        }
        if ("goldenbeans".equals(model) && "goldenBeansMallExchange".equals(code)) {
            return isEmpty(config, model, "GoldenBeansMallItemList");
        }
        if ("AntForestV2".equals(model) && "vitalityExchangeBenefit".equals(code)) {
            return isEmpty(config, model, "vitality_ExchangeBenefitList");
        }
        if ("AntDodo".equals(model) && "useProp".equals(code)) {
            return isEmpty(config, model, "usePropList");
        }
        return false;
    }

    /** 支付/交易/充值/捐赠/购买/社交邀请/组队模式/求助等硬边界。 */
    private static boolean isExcluded(String code) {
        String name = code.toLowerCase(Locale.ROOT);
        return name.contains("donate")
                || name.contains("donation")
                || name.contains("pay")
                || name.contains("payment")
                || name.contains("transaction")
                || name.contains("recharge")
                || name.contains("buy")
                || name.contains("seckill")
                || name.contains("invite")
                || name.contains("assistfriend")
                || name.contains("gifttofriend")
                || name.contains("tofriend")
                || name.contains("sendchat")
                || name.contains("hunthelp")
                || name.contains("teampriority");
    }

    private static ModelField<?> field(ConfigV2 config, String model, String code) {
        ModelFields fields = config.getModelFields(model);
        return fields == null ? null : fields.get(code);
    }

    private static void setChoice(ConfigV2 config, String model, String code, int value) {
        ModelField<?> field = field(config, model, code);
        if (field != null && "CHOICE".equals(field.getType())) {
            field.setObjectValue(value);
        }
    }

    private static boolean isEmpty(ConfigV2 config, String model, String code) {
        ModelField<?> field = field(config, model, code);
        if (field == null) {
            return true;
        }
        Object value = field.getValue();
        if (value == null) {
            return true;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).isEmpty();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).isEmpty();
        }
        if (value instanceof String) {
            return ((String) value).trim().isEmpty();
        }
        return false;
    }
}
