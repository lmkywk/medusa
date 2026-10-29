package io.github.aw1y2z.sesame.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonMappingException;
import lombok.Data;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.JsonUtil;
import io.github.aw1y2z.sesame.util.Log;

import java.io.File;

@Data
public class AppConfig {

    private static final String TAG = AppConfig.class.getSimpleName();

    // 存到与注入进程（支付宝模块）共享的 sesame 目录，确保日志开关在模块进程同样生效
    private static final File APP_CONFIG_DIRECTORY_FILE = FileUtil.MAIN_DIRECTORY_FILE;

    public static final AppConfig INSTANCE = new AppConfig();

    /** 上次 load 解析失败：内存此时只是默认值，必须禁止写盘，否则会把默认值整份固化 */
    private static volatile boolean loadFailed = false;

    @JsonIgnore
    private boolean init;

    /** 配置 schema 版本；只增不改，供后续迁移判断。 */
    public static final int SCHEMA_VERSION = 2;
    private Integer schemaVersion = SCHEMA_VERSION;

    private Boolean newUI = true;
    private Boolean languageSimplifiedChinese = true;

    private Boolean darkMode = false;
    private Boolean followSystem = true;

    private Boolean enableForestLog = true;
    private Boolean enableGoldenBeansLog = true;
    private Boolean enableFarmLog = true;
    private Boolean enableOtherLog = true;
    private Boolean enableDebugLog = false;
    /** 旧计划的整域目标是否作为兜底继续巡检 */
    private Boolean wholeDomainFallback = true;
    private Boolean enableViewErrorLog = true;
    private Boolean enableViewRuntimeLog = true;
    private Boolean batteryPerm = true;

    // 模块级开关：原先是按账号存在 BaseModel 里，现改为全局（注入进程与模块 App 共用同一份）
    private Boolean newRpc = true;
    private Boolean showToast = true;
    private Integer toastOffsetY = 0;
    private Boolean enableOnGoing = false;
    private Boolean closeCaptchaDialog = true;
    /** 通知净化：只拦营销/活动/推荐/福利/优惠；支付、安全、验证永远保留。 */
    private Boolean noticeFilterMarketing = true;
    /** 高级：取消支付宝其余通知，仅保留美杜莎；默认关闭。 */
    private Boolean noticeOnlyMedusa = false;

    /** 手动执行的节律建议（分钟，0=关闭）；只提示，不阻止执行。 */
    private Integer manualCooldownGlobal = 30;
    private Integer manualCooldownTarget = 60;

    /** 计划流阈值：计划期间相邻写请求最小间隔（毫秒），0=关闭；实际会再加抖动。 */
    public static final int DEFAULT_PLAN_FLOW_MS = 5000;
    public static final int DEFAULT_PLAN_FLOW_JITTER = 30;
    private Integer planFlowMs = DEFAULT_PLAN_FLOW_MS;
    private Integer planFlowJitterPercent = DEFAULT_PLAN_FLOW_JITTER;

    /** 4.0.0 god class 试点开关：true=走拆分后的海洋/摸鱼编排；false=回退旧实现。 */
    private Boolean oceanFishSplit = true;

    /**
     * 类级冷却：自动计划遇到今天已失败的端点时，只跳过该类任务，不再重复撞同一接口。
     * 默认关闭；手动执行永远放行。关闭即回到整域原行为。
     */
    private Boolean capabilityClassCooldown = false;

    // ===== 4.0.0 AI 只读层（只出聚合摘要，不出原始日志/令牌/好友） =====
    /** 总开关；关闭时不产生任何 AI 调用。 */
    private Boolean aiSummaryEnabled = false;
    /** OpenAI 兼容接口根地址，例如 https://api.example.com/v1 */
    private String aiBaseUrl = "";
    private String aiModel = "";
    private String aiToken = "";
    private Integer aiMaxTokens = 2048;
    /** 读超时（秒），默认 180。 */
    private Integer aiTimeoutSeconds = 180;
    /** 失败重试次数，默认 2。 */
    private Integer aiRetries = 2;
    /** 手动任务说明（没有深链时生成一句“去支付宝哪里做什么”）。 */
    private Boolean aiManualHints = true;
    /** 每日摘要生成时间（小时，0-23）。 */
    private Integer aiSummaryHour = 22;

    public static final int RISK_POLICY_STOP_AND_PROMPT = 0;
    public static final int RISK_POLICY_NOTIFY_ONLY = 1;
    /** 1009 后的策略：0=停止当前运行并提示，1=只提示不停止。 */
    private Integer riskPolicy = RISK_POLICY_STOP_AND_PROMPT;

    public Boolean getNewRpc() {
        return newRpc;
    }

    public void setNewRpc(Boolean value) {
        newRpc = value;
    }

    public Boolean getShowToast() {
        return showToast;
    }

    public void setShowToast(Boolean value) {
        showToast = value;
    }

    public Integer getToastOffsetY() {
        return toastOffsetY;
    }

    public void setToastOffsetY(Integer value) {
        toastOffsetY = value;
    }

    public Boolean getEnableOnGoing() {
        return enableOnGoing;
    }

    public void setEnableOnGoing(Boolean value) {
        enableOnGoing = value;
    }

    public Boolean getCloseCaptchaDialog() {
        return closeCaptchaDialog;
    }

    public void setCloseCaptchaDialog(Boolean value) {
        closeCaptchaDialog = value;
    }

    public Boolean getNoticeFilterMarketing() {
        return noticeFilterMarketing;
    }

    public void setNoticeFilterMarketing(Boolean value) {
        noticeFilterMarketing = value;
    }

    public Boolean getNoticeOnlyMedusa() {
        return noticeOnlyMedusa;
    }

    public void setNoticeOnlyMedusa(Boolean value) {
        noticeOnlyMedusa = value;
    }

    public Integer getManualCooldownGlobal() {
        return manualCooldownGlobal;
    }

    public void setManualCooldownGlobal(Integer value) {
        manualCooldownGlobal = value;
    }

    public Integer getManualCooldownTarget() {
        return manualCooldownTarget;
    }

    public void setManualCooldownTarget(Integer value) {
        manualCooldownTarget = value;
    }

    public Boolean getOceanFishSplit() {
        return oceanFishSplit;
    }

    public void setOceanFishSplit(Boolean value) {
        oceanFishSplit = value;
    }

    public Boolean getCapabilityClassCooldown() {
        return capabilityClassCooldown;
    }

    public void setCapabilityClassCooldown(Boolean value) {
        capabilityClassCooldown = value;
    }

    public Boolean getAiSummaryEnabled() {
        return aiSummaryEnabled;
    }

    public void setAiSummaryEnabled(Boolean value) {
        aiSummaryEnabled = value;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    public void setAiBaseUrl(String value) {
        aiBaseUrl = value;
    }

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String value) {
        aiModel = value;
    }

    public String getAiToken() {
        return aiToken;
    }

    public void setAiToken(String value) {
        aiToken = value;
    }

    public Integer getAiMaxTokens() {
        return aiMaxTokens;
    }

    public void setAiMaxTokens(Integer value) {
        aiMaxTokens = value;
    }

    public Integer getAiTimeoutSeconds() {
        return aiTimeoutSeconds;
    }

    public void setAiTimeoutSeconds(Integer value) {
        aiTimeoutSeconds = value;
    }

    public Integer getAiRetries() {
        return aiRetries;
    }

    public void setAiRetries(Integer value) {
        aiRetries = value;
    }

    public Boolean getAiManualHints() {
        return aiManualHints;
    }

    public void setAiManualHints(Boolean value) {
        aiManualHints = value;
    }

    public Integer getAiSummaryHour() {
        return aiSummaryHour;
    }

    public void setAiSummaryHour(Integer value) {
        aiSummaryHour = value;
    }

    public Integer getPlanFlowMs() {
        return planFlowMs;
    }    public void setPlanFlowMs(Integer value) {
        planFlowMs = value;
    }

    public Integer getPlanFlowJitterPercent() {
        return planFlowJitterPercent;
    }

    public void setPlanFlowJitterPercent(Integer value) {
        planFlowJitterPercent = value;
    }

    public Integer getRiskPolicy() {
        return riskPolicy;
    }

    public void setRiskPolicy(Integer value) {
        riskPolicy = value;
    }

    public Boolean getLanguageSimplifiedChinese() {
        return languageSimplifiedChinese;
    }

    public void setLanguageSimplifiedChinese(Boolean value) {
        languageSimplifiedChinese = value;
    }

    public Boolean getDarkMode() {
        return darkMode;
    }

    public void setDarkMode(Boolean value) {
        darkMode = value;
    }

    public Boolean getFollowSystem() {
        return followSystem;
    }

    public void setFollowSystem(Boolean value) {
        followSystem = value;
    }

    public Boolean getEnableForestLog() { return enableForestLog; }
    public void setEnableForestLog(Boolean value) { enableForestLog = value; }

    public Boolean getEnableGoldenBeansLog() { return enableGoldenBeansLog; }
    public void setEnableGoldenBeansLog(Boolean value) { enableGoldenBeansLog = value; }

    public Boolean getEnableFarmLog() { return enableFarmLog; }
    public void setEnableFarmLog(Boolean value) { enableFarmLog = value; }

    public Boolean getEnableOtherLog() { return enableOtherLog; }
    public void setEnableOtherLog(Boolean value) { enableOtherLog = value; }

    public Boolean getEnableDebugLog() { return enableDebugLog; }
    public void setEnableDebugLog(Boolean value) { enableDebugLog = value; }
    public Boolean getWholeDomainFallback() { return wholeDomainFallback; }
    public void setWholeDomainFallback(Boolean value) { wholeDomainFallback = value; }

    public Boolean getEnableViewErrorLog() { return enableViewErrorLog; }
    public void setEnableViewErrorLog(Boolean value) { enableViewErrorLog = value; }

    public Boolean getEnableViewRuntimeLog() { return enableViewRuntimeLog; }
    public void setEnableViewRuntimeLog(Boolean value) { enableViewRuntimeLog = value; }

    public Boolean getBatteryPerm() { return batteryPerm; }
    public void setBatteryPerm(Boolean value) { batteryPerm = value; }

    public static Boolean save() {
        if (loadFailed) {
            // 上次加载失败，内存只是默认值：写盘会把用户配置整份固化，拒绝
            Log.i(TAG, "上次APP配置加载失败，本次不写盘");
            return false;
        }
        return FileUtil.write2File(toSaveStr(), new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json"));
    }

    public static synchronized AppConfig load() {
        File appConfigFile = new File(APP_CONFIG_DIRECTORY_FILE, "appConfig.json");
        loadFailed = false;
        try {
            if (appConfigFile.exists()) {
                String json = FileUtil.readFromFile(appConfigFile);
                JsonUtil.copyMapper().readerForUpdating(INSTANCE).readValue(json);
                // 注意：必须先加载文件再打印日志，否则开关判断仍取默认值 true，
                // 导致「运行日志已关闭」时启动进程仍写入加载日志
                Log.i("加载APP配置");
                String formatted = toSaveStr();
                if (formatted != null && !formatted.equals(json)) {
                    Log.i(TAG, "格式化APP配置");
                    FileUtil.write2File(formatted, appConfigFile);
                }
            } else {
                unload();
                Log.i(TAG, "初始APP配置");
                FileUtil.write2File(toSaveStr(), appConfigFile);
            }
        } catch (Throwable t) {
            Log.printStackTrace(TAG, t);
            // 解析失败只回落默认值、绝不写盘：瞬时 IO 抖动或半份文件不该把用户配置整份重置
            Log.i(TAG, "解析APP配置失败，本次使用默认值（不写盘）");
            loadFailed = true;
            unload();
        }
        INSTANCE.setInit(true);
        return INSTANCE;
    }

    public static synchronized void unload() {
        try {
            JsonUtil.copyMapper().updateValue(INSTANCE, new AppConfig());
        } catch (JsonMappingException e) {
            Log.printStackTrace(TAG, e);
        }
    }

    public static String toSaveStr() {
        return JsonUtil.toFormatJsonString(INSTANCE);
    }

}