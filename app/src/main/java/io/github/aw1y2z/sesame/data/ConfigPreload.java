package io.github.aw1y2z.sesame.data;

import io.github.aw1y2z.sesame.entity.AlipayGoldenBeansMallItem;
import io.github.aw1y2z.sesame.entity.AlipayGoldTicketTaskList;
import io.github.aw1y2z.sesame.entity.AlipayWelfareFundTaskList;
import io.github.aw1y2z.sesame.util.FileUtil;
import io.github.aw1y2z.sesame.util.StringUtil;
import io.github.aw1y2z.sesame.util.idMap.AnimalIdMap;
import io.github.aw1y2z.sesame.util.idMap.AntDodoTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDoFarmTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntFarmDrawMachineTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestHuntTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntForestVitalityTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntMemberTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanAntiepTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOceanFishBlackListMap;
import io.github.aw1y2z.sesame.util.idMap.AntOrchardTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntSportsTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.AntStallTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.BeachIdMap;
import io.github.aw1y2z.sesame.util.idMap.CooperationIdMap;
import io.github.aw1y2z.sesame.util.idMap.FarmOrnamentsIdMap;
import io.github.aw1y2z.sesame.util.idMap.GameCenterMallItemMap;
import io.github.aw1y2z.sesame.util.idMap.GoldenBeansTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.GoldenBeansMallItemMap;
import io.github.aw1y2z.sesame.util.idMap.MarathonIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.MemberCreditSesameTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.MonopolyTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.NewAncientTreeIdMap;
import io.github.aw1y2z.sesame.util.idMap.OrchardChouChouLeTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.PathThemeMapListMap;
import io.github.aw1y2z.sesame.util.idMap.PlantSceneIdMap;
import io.github.aw1y2z.sesame.util.idMap.PromiseSimpleTemplateIdMap;
import io.github.aw1y2z.sesame.util.idMap.ReserveIdMap;
import io.github.aw1y2z.sesame.util.idMap.TreeIdMap;
import io.github.aw1y2z.sesame.util.idMap.UserIdMap;
import io.github.aw1y2z.sesame.util.idMap.VitalityBenefitIdMap;
import io.github.aw1y2z.sesame.util.idMap.GoldTicketTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.WelfareFundTaskListMap;
import io.github.aw1y2z.sesame.util.idMap.ForestHuntIdMap;
import io.github.aw1y2z.sesame.util.idMap.rpcRequestMap;

/**
 * 配置相关的预加载逻辑（原 SettingsActivity / NewSettingsActivity 中的初始化）。
 * miuix Compose 界面复用同一套数据，必须在这里完成 IdMap 加载与 ConfigV2.load，
 * 否则 SELECT_ONE / SELECT 等字段的选项列表为空。
 */
public final class ConfigPreload {

    private ConfigPreload() {
    }

    /** 本进程已为哪个账号建过模型图；换账号才需要重建 */
    private static volatile String preparedUid = null;

    public static void prepare(String userId) {
        // 模型图只在首次或换账号时重建。每进一层配置页就 initAllModel()，会让上一级页面
        // 正握着的字段对象变成孤儿：用户在那一级的改动从快照里消失，表现成
        // 「关一个开关，别的开关跟着一起关」+「保存成功但其实什么都没写」。
        boolean sameGeneration = userId == null ? preparedUid == null : userId.equals(preparedUid);
        if (!sameGeneration || Model.getModelList().isEmpty()) {
            Model.initAllModel();
            preparedUid = userId;
        }
        UserIdMap.setCurrentUserId(userId);
        UserIdMap.load(userId);
        CooperationIdMap.load(userId);
        VitalityBenefitIdMap.load(userId);
        GameCenterMallItemMap.load(userId);
        FarmOrnamentsIdMap.load(userId);
        MemberBenefitIdMap.load(userId);
        PromiseSimpleTemplateIdMap.load(userId);
        TreeIdMap.load();
        ReserveIdMap.load();
        AnimalIdMap.load();
        MarathonIdMap.load();
        NewAncientTreeIdMap.load();
        BeachIdMap.load();
        PlantSceneIdMap.load();
        rpcRequestMap.load();
        ForestHuntIdMap.load();
        MemberCreditSesameTaskListMap.load();
        AntForestVitalityTaskListMap.load();
        AntForestHuntTaskListMap.load();
        AntFarmDoFarmTaskListMap.load();
        AntFarmDrawMachineTaskListMap.load();
        AntDodoTaskListMap.load();
        AntOceanAntiepTaskListMap.load();
        AntOceanFishBlackListMap.load();
        AntOrchardTaskListMap.load();
        OrchardChouChouLeTaskListMap.load();
        AntStallTaskListMap.load();
        AntSportsTaskListMap.load();
        PathThemeMapListMap.load();
        AntMemberTaskListMap.load();
        GoldenBeansTaskListMap.load();
        GoldenBeansMallItemMap.load();
        // 候选实体的静态缓存是注入进程写的，App 进程必须清一次才会重建
        AlipayGoldenBeansMallItem.clear();
        WelfareFundTaskListMap.load();
        AlipayWelfareFundTaskList.clear();
        GoldTicketTaskListMap.load();
        AlipayGoldTicketTaskList.clear();
        MonopolyTaskListMap.load();
        ConfigV2.load(userId);
    }

    public static boolean isEmpty(String userId) {
        return StringUtil.isEmpty(userId);
    }

    /** 当前进程是否已经为这个账号建好模型并加载过配置。 */
    public static boolean isPreparedFor(String userId) {
        return userId == null ? preparedUid == null : userId.equals(preparedUid);
    }

    public static java.io.File getConfigFile(String userId) {
        if (StringUtil.isEmpty(userId)) {
            return FileUtil.getDefaultConfigV2File();
        }
        return FileUtil.getConfigV2File(userId);
    }
}
