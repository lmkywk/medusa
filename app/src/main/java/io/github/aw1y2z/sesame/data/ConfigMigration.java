package io.github.aw1y2z.sesame.data;

import java.util.Collection;
import java.util.Map;

/**
 * 一次性配置迁移。
 * <p>
 * 好友浇水 v1：旧版把「回赠」与「浇水动作/好友列表」拆成互不约束的开关，且回赠阈值 0 表示
 * “不限”，导致只要回赠开着就会给每个收过能量的好友按最高档回浇。新版收敛为
 * 「总开关 + 方式 + 每次克数 + 每日上限」，迁移只读旧值一次，之后不再改写用户设置。
 */
public final class ConfigMigration {

    public static final int WATER_LOGIC_VERSION = 2;

    private ConfigMigration() {
    }

    /**
     * @return true 表示配置被改过，调用方需要落盘
     */
    public static boolean apply(ConfigV2 config) {
        if (config == null) {
            return false;
        }
        ModelFields fields = config.getModelFields("AntForestV2");
        if (fields == null) {
            return false;
        }
        int version = intValue(fields, "waterLogicVersion");
        if (version >= WATER_LOGIC_VERSION) {
            return false;
        }
        if (version < 1) {
            boolean legacyReturn = boolValue(fields, "returnWater");
            int legacyType = intValue(fields, "waterFriendType");
            boolean hasList = !isEmpty(fields, "waterFriendList");
            // 旧版“动作”为 0（不浇水）时，即使列表有人也不浇；回赠则与动作无关
            boolean enabled = legacyReturn || (legacyType > 0 && hasList);

            setBool(fields, "waterFriendEnable", enabled);
            setInt(fields, "waterMode", legacyReturn ? 0 : 1);
            setInt(fields, "waterDailyLimit", 200);
            if (enabled && legacyType <= 0) {
                // 旧版回赠不看动作值；迁移后补一个明确克数，否则总开关会因“不浇水”而空转
                setInt(fields, "waterFriendType", 3);
            }
        }
        // v2：新模型用总开关表达“关”，旧值“不浇水(0)”会让用户打开总开关后什么都不发生。
        // 总开关仍关闭时把这个无意义的 0 归一到 33g；开关已开的不动，尊重“暂停浇水”的意图。
        if (version < 2 && !boolValue(fields, "waterFriendEnable")
                && intValue(fields, "waterFriendType") <= 0) {
            setInt(fields, "waterFriendType", 3);
        }
        setInt(fields, "waterLogicVersion", WATER_LOGIC_VERSION);

        // 保护地「合种|浇水」：没选任何合种目标时开关留着 ON 只会让人误以为它会浇水，顺手关掉
        ModelFields protect = config.getModelFields("ProtectEcology");
        if (protect != null && boolValue(protect, "cooperateWater")
                && isEmpty(protect, "cooperateWaterList")) {
            setBool(protect, "cooperateWater", false);
        }
        return true;
    }

    private static ModelField<?> field(ModelFields fields, String code) {
        return fields == null ? null : fields.get(code);
    }

    private static boolean boolValue(ModelFields fields, String code) {
        ModelField<?> field = field(fields, code);
        Object value = field == null ? null : field.getValue();
        return value instanceof Boolean && (Boolean) value;
    }

    private static int intValue(ModelFields fields, String code) {
        ModelField<?> field = field(fields, code);
        Object value = field == null ? null : field.getValue();
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static void setBool(ModelFields fields, String code, boolean value) {
        ModelField<?> field = field(fields, code);
        if (field != null) {
            field.setObjectValue(value);
        }
    }

    private static void setInt(ModelFields fields, String code, int value) {
        ModelField<?> field = field(fields, code);
        if (field != null) {
            field.setObjectValue(value);
        }
    }

    private static boolean isEmpty(ModelFields fields, String code) {
        ModelField<?> field = field(fields, code);
        Object value = field == null ? null : field.getValue();
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
