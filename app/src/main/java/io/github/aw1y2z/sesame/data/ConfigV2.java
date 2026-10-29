package io.github.aw1y2z.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import io.github.aw1y2z.sesame.data.task.ModelTask;
import io.github.aw1y2z.sesame.entity.UserEntity;
import io.github.aw1y2z.sesame.util.*;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.aw1y2z.sesame.entity.KVNode;

@Data
public class ConfigV2 {

    private static final String TAG = ConfigV2.class.getSimpleName();

    public static final ConfigV2 INSTANCE = new ConfigV2();

    /** 配置 schema 版本；只增不改，供后续迁移判断。 */
    public static final int SCHEMA_VERSION = 2;
    private Integer schemaVersion = SCHEMA_VERSION;
    /** 默认配置预设版本；0/缺失时应用一次“几乎全开（安全+资源支出）”预设。 */
    private Integer presetVersion = 0;

    @JsonIgnore
    private boolean init;

    private final Map<String, ModelFields> modelFieldsMap = new ConcurrentHashMap<>();

    /**
     * 最近一次与磁盘同步（load 或 save）后的配置文本。
     * 模块 App 与注入进程各持一份 ConfigV2 快照、都会整份落盘，靠它识别"文件被别的进程改过"。
     */
    @JsonIgnore
    private static String lastSyncedText;

    /** 上次同步时各字段的值快照（key = modelCode|fieldCode），用于挑出本进程真正改过的字段 */
    @JsonIgnore
    private static final Map<String, String> valueBaseline = new ConcurrentHashMap<>();

    public void setModelFieldsMap(Map<String, ModelFields> newModels) {
        modelFieldsMap.clear();
        Map<String, ModelConfig> modelConfigMap = ModelTask.getModelConfigMap();
        if (newModels == null) {
            newModels = new HashMap<>();
        }
        for (ModelConfig modelConfig : modelConfigMap.values()) {
            String modelCode = modelConfig.getCode();
            ModelFields newModelFields = new ModelFields();
            ModelFields configModelFields = modelConfig.getFields();
            ModelFields modelFields = newModels.get(modelCode);
            if (modelFields != null) {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    ModelField<?> modelField = modelFields.get(configModelField.getCode());
                    try {
                        if (modelField != null) {
                            Object value = modelField.getValue();
                            if (value != null) {
                                configModelField.setObjectValue(value);
                            }
                        }
                    } catch (Exception e) {
                        Log.printStackTrace(e);
                    }
                    newModelFields.addField(configModelField);
                }
            } else {
                for (ModelField<?> configModelField : configModelFields.values()) {
                    newModelFields.addField(configModelField);
                }
            }
            modelFieldsMap.put(modelCode, newModelFields);
        }
    }

    public Boolean hasModelFields(String modelCode) {
        return modelFieldsMap.containsKey(modelCode);
    }

    public ModelFields getModelFields(String modelCode) {
        return modelFieldsMap.get(modelCode);
    }

    public void removeModelFields(String modelCode) {
        modelFieldsMap.remove(modelCode);
    }

    /*public void addModelFields(String modelCode, ModelFields modelFields) {
        modelFieldsMap.put(modelCode, modelFields);
    }*/

    public Boolean hasModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return false;
        }
        return modelFields.containsKey(fieldCode);
    }

    /*public ModelField getModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = modelFieldsMap.get(modelCode);
        if (modelFields == null) {
            return null;
        }
        return modelFields.get(fieldCode);
    }*/

    /*public void removeModelField(String modelCode, String fieldCode) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return;
        }
        modelFields.remove(fieldCode);
    }*/

    /*public Boolean addModelField(String modelCode, ModelField modelField) {
        ModelFields modelFields = getModelFields(modelCode);
        if (modelFields == null) {
            return false;
        }
        modelFields.put(modelCode, modelField);
        return true;
    }*/

    /*@SuppressWarnings("unchecked")
    public <T extends ModelField> T getModelFieldExt(String modelCode, String fieldCode) {
        return (T) getModelField(modelCode, fieldCode);
    }*/

    public static synchronized Boolean isModify(String userId) {
        String json = null;
        File configV2File;
        if (StringUtil.isEmpty(userId)) {
            configV2File = FileUtil.getDefaultConfigV2File();
        } else {
            configV2File = FileUtil.getConfigV2File(userId);
        }
        if (configV2File.exists()) {
            json = FileUtil.readFromFile(configV2File);
        }
        if (json != null) {
            String formatted = INSTANCE.toSaveStr();
            return formatted == null || !formatted.equals(json);
        }
        return true;
    }

    /** 本进程是否有相对上次同步的字段级改动（供 UI 判断是否需要落盘/提示，不受磁盘未知键影响） */
    public static synchronized boolean hasFieldChanges() {
        return !valueBaseline.isEmpty() && !collectChangedFields().isEmpty();
    }

    public static synchronized Boolean save(String userId, Boolean force) {
        // 非强制保存且没有字段级改动时直接返回；UI 的显式改动走 force=true，
        // 即使 300ms 防抖期间配置被重载、基线被刷新，也仍然落盘。
        if (!force && !valueBaseline.isEmpty() && collectChangedFields().isEmpty()) {
            return true;
        }
        if (!force && !isModify(userId)) {
            return true;
        }
        // 磁盘在本进程上次同步之后被别的进程改过时，先把磁盘内容合并进来再落盘
        if (mergeDiskChanges(userId)) {
            // 本进程没有可写内容、磁盘上的是更新版本：跳过写入，别把别人的改动覆盖掉
            return true;
        }
        String json = INSTANCE.toSaveStr();
        // 全量快照落盘的最后一道闸：本进程内存可能因模型重建缺了模块/字段（值可以不同，键不能少）。
        // 支付宝进程一天要重写整份配置几十次，没有这道闸，一次内存缺省就会把账号配置写成默认值。
        File targetFile = StringUtil.isEmpty(userId)
                ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
        String diskJson = targetFile != null && targetFile.exists()
                ? FileUtil.readFromFile(targetFile) : null;
        if (json != null && !StringUtil.isEmpty(diskJson)) {
            // 模型升级会删掉旧字段，磁盘仍保留；先补回这些对象键再校验，
            // 否则整份保存会被“缺键”保护永久拒绝。
            String merged = JsonUtil.mergeMissingKeys(diskJson, json);
            if (merged != null && !merged.equals(json)) {
                Log.record("保存配置#补回磁盘已有但本进程未知的配置项");
                json = merged;
            }
            if (!JsonUtil.coversSameKeys(diskJson, json)) {
                Log.record("保存配置跳过#合并后仍缺少磁盘配置项，拒绝整份覆盖: "
                        + (StringUtil.isEmpty(userId) ? "默认" : userId));
                return false;
            }
        }
        // 写盘前固定留一份上一版（覆盖式即时快照），与「每日一次」的滚动备份解耦
        FileUtil.backupConfigV2BeforeWrite(userId);
        FileUtil.backupConfigV2WithRolling(StringUtil.isEmpty(userId) ? "默认" : userId);
        boolean success;
        if (StringUtil.isEmpty(userId)) {
            userId = "默认";
            success = FileUtil.setDefaultConfigV2File(json);
        } else {
            success = FileUtil.setConfigV2File(userId, json);
        }
        
        if (success) {
            lastSyncedText = json;
            lastLoadedDiskMtime = targetFile != null ? targetFile.lastModified() : 0L;
            captureBaseline();
        }
        
        Log.record("保存配置: " + userId);
        return success;
    }
    
    public static synchronized ConfigV2 load(String userId) {
        Log.i(TAG, "开始加载配置");
        String userName = "";
        File configV2File = null;
        try {
            if (StringUtil.isEmpty(userId)) {
                configV2File = FileUtil.getDefaultConfigV2File();
                userName = "默认";
            } else {
                configV2File = FileUtil.getConfigV2File(userId);
                UserEntity userEntity = UserIdMap.get(userId);
                if (userEntity == null) {
                    userName = userId;
                } else {
                    userName = userEntity.getShowName();
                }
            }
            Log.record("加载配置: " + userName);
            if (configV2File.exists()) {
                String json = FileUtil.readFromFile(configV2File);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                String formatted = INSTANCE.toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    // 回写只允许改格式、不许改数据；判定不过就保留磁盘原文
                    if (!JsonUtil.isRewriteLossless(json, formatted)) {
                        Log.record("配置加载结果与磁盘数据不一致，已跳过格式化回写以免覆盖丢失: " + userName);
                    } else {
                        Log.i(TAG, "格式化配置: " + userName);
                        FileUtil.backupConfigV2WithRolling(userId);
                        FileUtil.write2File(formatted, configV2File);
                    }
                }
            } else {
                File defaultConfigV2File = FileUtil.getDefaultConfigV2File();
                if (defaultConfigV2File.exists()) {
                    String json = FileUtil.readFromFile(defaultConfigV2File);
                    JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                    Log.i(TAG, "复制新配置: " + userName);
                    FileUtil.write2File(json, configV2File);
                } else {
                    INSTANCE.setModelFieldsMap(null);
                    unload();
                    Log.i(TAG, "初始新配置: " + userName);
                    FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                }
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            // 解析失败先用滚动备份兜底恢复；恢复不了就**保留内存值、绝不写盘**。
            // 以前这里会把全默认值写回磁盘，等于一次读取抖动就抹掉整份账号配置。
            if (!restoreFromBackup(configV2File, userId)) {
                Log.record("配置读取失败且无可用备份，本次不改动磁盘: " + userName);
            }
        }
        clampAllFields();
        if (INSTANCE.getPresetVersion() == null
                || INSTANCE.getPresetVersion() < ConfigPreset.VERSION) {
            try {
                ConfigPreset.apply(INSTANCE);
                INSTANCE.setPresetVersion(ConfigPreset.VERSION);
                if (configV2File != null && configV2File.exists()) {
                    FileUtil.backupConfigV2WithRolling(userId);
                    FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
                }
                Log.record("配置⚙️已应用默认优化 v" + ConfigPreset.VERSION
                        + "：安全/资源支出开关开启；支付/交易/捐赠/缺好友/缺数字保持关闭");
            } catch (Throwable t) {
                Log.err(TAG, "应用默认配置预设失败", t);
            }
        }
        INSTANCE.setInit(true);
        captureBaseline();
        // 记下本次同步到的文本，供 save() 判断磁盘是否被别的进程改过
        lastSyncedText = configV2File != null && configV2File.exists() ? FileUtil.readFromFile(configV2File) : null;
        lastLoadedDiskMtime = configV2File != null ? configV2File.lastModified() : 0L;
        Log.i(TAG, "加载配置结束");
        return INSTANCE;
    }

    /** 本进程上次读/写磁盘配置时的文件修改时间，0 表示还没同步过 */
    private static volatile long lastLoadedDiskMtime = 0L;

    /**
     * 磁盘配置比本进程上次同步更新时重新加载。
     * <p>广播只是加速器：模块 App 写了盘但 restart/reloadConfig 没送达时，
     * 没有这道对齐就会出现「盘上 true、内存 false」，接下来任何一次全量保存都会把它写成 false。
     */
    public static synchronized void reloadIfDiskChanged(String userId) {
        try {
            File file = StringUtil.isEmpty(userId)
                    ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
            if (file == null || !file.exists() || lastLoadedDiskMtime == 0L) {
                return;
            }
            if (file.lastModified() != lastLoadedDiskMtime) {
                Log.record("配置📄磁盘已被更新，重新加载后再执行");
                load(userId);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
    }

    public static synchronized void unload() {
        for (ModelFields modelFields : INSTANCE.modelFieldsMap.values()) {
            for (ModelField<?> modelField : modelFields.values()) {
                if (modelField != null) {
                    modelField.reset();
                }
            }
        }
    }

    /**
     * 按各字段自身的刻度语义夹一遍越界值。Jackson 只认 value 属性、直接写字段，
     * 因此校验不能放在 setValue 里（带单位换算的子类会被按错误刻度截断）。
     */
    private static void clampAllFields() {
        for (ModelFields modelFields : INSTANCE.modelFieldsMap.values()) {
            for (ModelField<?> modelField : modelFields.values()) {
                if (modelField != null) {
                    modelField.clampValue();
                }
            }
        }
    }

    /**
     * 主配置文件解析失败时用最近一份滚动备份恢复；恢复失败返回 false，由调用方重置为默认。
     * 备份里存的就是完整的 config_v2 内容，所以恢复后可以直接写回主配置文件。
     */
    private static boolean restoreFromBackup(File configV2File, String userId) {
        try {
            // save() 落盘时把空 userId 归一成"默认"，备份文件名跟着用这个，这里保持一致
            File backupFile = FileUtil.findLatestBackupFile(StringUtil.isEmpty(userId) ? "默认" : userId);
            if (backupFile == null || !backupFile.exists()) {
                return false;
            }
            String json = FileUtil.readFromFile(backupFile);
            if (StringUtil.isEmpty(json)) {
                return false;
            }
            // 先重建为代码里的默认值再套备份，避免主文件"半解析"后的残留值混进来
            INSTANCE.setModelFieldsMap(null);
            unload();
            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
            if (configV2File != null) {
                FileUtil.write2File(INSTANCE.toSaveStr(), configV2File);
            }
            Log.record("配置解析失败，已从备份恢复: " + backupFile.getName());
            return true;
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            return false;
        }
    }

    /**
     * 值的稳定文本形式：集合/映射先排序再拼接，避免顺序抖动把「没改」误判成「本进程改过」，
     * 进而把旧值压回、覆盖对方进程的新值。
     */
    private static String stableValueText(Object value) {
        if (value instanceof List) {
            // List 的顺序就是值本身，不能排序
            return String.valueOf(value);
        }
        if (value instanceof java.util.Collection) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (Object item : (java.util.Collection<?>) value) {
                parts.add(String.valueOf(item));
            }
            java.util.Collections.sort(parts);
            return parts.toString();
        }
        if (value instanceof Map) {
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                parts.add(entry.getKey() + "=" + entry.getValue());
            }
            java.util.Collections.sort(parts);
            return parts.toString();
        }
        return String.valueOf(value);
    }

    /** 记录当前各字段的值快照，用于之后判断"本进程改过哪些字段" */
    private static void captureBaseline() {
        Map<String, String> baseline = new HashMap<>();
        for (Map.Entry<String, ModelFields> modelEntry : INSTANCE.modelFieldsMap.entrySet()) {
            for (ModelField<?> field : modelEntry.getValue().values()) {
                if (field != null && field.getCode() != null) {
                    baseline.put(fieldKey(modelEntry.getKey(), field.getCode()), stableValueText(field.getValue()));
                }
            }
        }
        valueBaseline.clear();
        valueBaseline.putAll(baseline);
    }

    /**
     * 收集本进程相对上次同步改过的字段（key = {@code modelCode|fieldCode}，值是值的浅拷贝，
     * 便于重载磁盘内容后原样压回）。没有基线的字段一律不算本进程改动——宁可少合并，也不拿旧值覆盖新值。
     */
    private static Map<String, Object> collectChangedFields() {
        Map<String, Object> changed = new LinkedHashMap<>();
        for (Map.Entry<String, ModelFields> modelEntry : INSTANCE.modelFieldsMap.entrySet()) {
            for (ModelField<?> field : modelEntry.getValue().values()) {
                if (field == null || field.getCode() == null) {
                    continue;
                }
                String key = fieldKey(modelEntry.getKey(), field.getCode());
                String baseline = valueBaseline.get(key);
                if (baseline == null || baseline.equals(stableValueText(field.getValue()))) {
                    continue;
                }
                changed.put(key, copyFieldValue(field.getValue()));
            }
        }
        return changed;
    }

    /**
     * 落盘前把「磁盘上更新的内容」和「本进程自己的改动」合到一起：
     * 磁盘被别的进程改过时先重载，再把本进程改过的字段压回去，
     * 这样两边都不会丢（原来是谁最后写谁覆盖）。
     *
     * @return true 表示本进程没有可写内容（已采纳磁盘上的更新），调用方应跳过本次写入
     */
    private static boolean mergeDiskChanges(String userId) {
        try {
            File configV2File = StringUtil.isEmpty(userId) ? FileUtil.getDefaultConfigV2File() : FileUtil.getConfigV2File(userId);
            if (!configV2File.exists() || lastSyncedText == null) {
                return false;
            }
            String disk = FileUtil.readFromFile(configV2File);
            if (disk == null || disk.equals(lastSyncedText)) {
                return false;
            }
            Map<String, Object> changed = collectChangedFields();
            // 先重载，让本进程也看到别的进程刚写入的内容
            JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(disk);
            clampAllFields();
            if (changed.isEmpty()) {
                // 本进程没有改动：磁盘上的才是最新状态，直接采纳，
                // 不能拿本进程的旧快照写回去（页面的 save() 只按 isModify 判断，
                // 这种情况会走到这里：文件被别的进程改过，但本进程没动过任何字段）
                lastSyncedText = disk;
                captureBaseline();
                Log.record("配置已被其他进程更新，本进程无改动，已重载并跳过写入");
                return true;
            }
            for (Map.Entry<String, Object> entry : changed.entrySet()) {
                int separator = entry.getKey().indexOf('|');
                ModelFields modelFields = INSTANCE.modelFieldsMap.get(entry.getKey().substring(0, separator));
                ModelField<?> field = modelFields == null ? null : modelFields.get(entry.getKey().substring(separator + 1));
                if (field != null) {
                    field.setObjectValue(entry.getValue());
                }
            }
            Log.record("配置已被其他进程更新，已合并本进程改动后再保存");
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
        }
        return false;
    }

    private static String fieldKey(String modelCode, String fieldCode) {
        return modelCode + "|" + fieldCode;
    }

    /** 字段值的浅拷贝：集合/Map/KVNode 复制一份，避免重载时被就地改掉；其余按不可变类型处理 */
    private static Object copyFieldValue(Object value) {
        if (value instanceof Set) {
            return new LinkedHashSet<>((Set<?>) value);
        }
        if (value instanceof Map) {
            return new LinkedHashMap<>((Map<?, ?>) value);
        }
        if (value instanceof List) {
            return new ArrayList<>((List<?>) value);
        }
        if (value instanceof KVNode) {
            KVNode<?, ?> node = (KVNode<?, ?>) value;
            return new KVNode<>(node.getKey(), node.getValue());
        }
        return value;
    }

    public String toSaveStr() {
        return JsonUtil.toFormatJsonString(this);
    }

}
