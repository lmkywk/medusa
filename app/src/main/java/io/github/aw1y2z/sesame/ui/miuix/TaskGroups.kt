package io.github.aw1y2z.sesame.ui.miuix

import androidx.compose.ui.graphics.Color
import io.github.aw1y2z.sesame.data.plan.PlanTargets

/** 原型阶段的风险等级：只用于展示，不参与执行判断。 */
enum class GroupRisk(val label: String, val color: Color) {
    LOW("低风险", Color(0xFF2EAD6B)),
    MEDIUM("中风险", Color(0xFFE0A100)),
    HIGH("高风险", Color(0xFFE05252))
}

/**
 * 任务组原型：一个可展示、可选的任务组合。
 * <p>执行器暂时仍按 [target] 运行；[tasks] 与 [composition] 只用于验证命名/组成/风险展示。
 */
data class TaskGroupSpec(
    val id: String,
    val name: String,
    val target: String,
    val composition: String,
    val tasks: List<String>,
    val risk: GroupRisk,
    val estimatedMinutes: Int,
    val note: String = "",
    /** 原型阶段该组仍映射到更宽的目标（如森林日常全量），此时不显示目标的整组任务进度。 */
    val executesFullTarget: Boolean = false,
    /** 按类型执行：空列表＝该维度不限；两者都空＝整域全跑。 */
    val mechanisms: List<String> = emptyList(),
    val costTypes: List<String> = emptyList()
) {
    /** 交给 Plan.filters 的 JSON；空串表示不过滤。 */
    fun filterJson(): String {
        if (mechanisms.isEmpty() && costTypes.isEmpty()) return ""
        val jo = org.json.JSONObject()
        jo.put("mechanisms", org.json.JSONArray(mechanisms))
        jo.put("costTypes", org.json.JSONArray(costTypes))
        return jo.toString()
    }
}

object TaskGroups {

    /** 机制选项：与 TaskCapabilityRegistry.Mechanism 同名，供“按类型编排”勾选。 */
    val mechanismOptions: List<Pair<String, String>> = listOf(
        "CLAIM" to "领取",
        "TIMED" to "计时",
        "BROWSE" to "浏览",
        "SOCIAL" to "社交",
        "VIRTUAL_SPEND" to "资源支出",
        "CHANCE" to "抽机会",
        "GAME" to "游戏",
        "EXTERNAL_JUMP" to "外跳",
        "AUTH" to "授权",
        "REAL_ACTION" to "真实动作",
        "TRANSACTION" to "交易",
        "PROGRESS" to "进度",
        "ANSWER" to "答题"
    )

    fun mechanismLabel(code: String): String =
        mechanismOptions.firstOrNull { it.first == code }?.second ?: code

    val all: List<TaskGroupSpec> = listOf(
        TaskGroupSpec(
            id = "forest.safe",
            name = "森林·安全日常",
            target = PlanTargets.FOREST_DAILY,
            composition = "签到 · 任务列表 · 已完成领奖 · 乐园（不含游戏/外跳）",
            tasks = listOf("签到", "任务列表", "已完成领奖", "乐园"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 3,
            note = "只跑领取/浏览/计时类，跳过游戏、外跳和资源支出",
            executesFullTarget = true,
            mechanisms = listOf("CLAIM", "BROWSE", "TIMED")
        ),
        TaskGroupSpec(
            id = "forest.reward",
            name = "森林·领奖励",
            target = PlanTargets.FOREST_DAILY,
            composition = "签到 · 任务列表 · 已完成领奖",
            tasks = listOf("签到", "任务列表", "已完成领奖"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2,
            note = "只跑领取类，不做任务上报",
            executesFullTarget = true,
            mechanisms = listOf("CLAIM")
        ),
        TaskGroupSpec(
            id = "forest.collect",
            name = "森林·收能量",
            target = PlanTargets.FOREST_COLLECT,
            composition = "只收自己/好友能量",
            tasks = listOf("自己能量", "好友能量"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 10
        ),
        TaskGroupSpec(
            id = "species.task",
            name = "物种·任务",
            target = PlanTargets.SPECIES_TASK,
            composition = "已完成领奖 · 每日任务",
            tasks = listOf("已完成领奖", "每日任务"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2,
            mechanisms = listOf("CLAIM", "TIMED", "BROWSE")
        ),
        TaskGroupSpec(
            id = "ocean.clean",
            name = "海洋·清理",
            target = PlanTargets.OCEAN_CLEAN,
            composition = "自己海域清理（不含好友）",
            tasks = listOf("自己海域清理"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2
        ),
        TaskGroupSpec(
            id = "orchard.fertilizer",
            name = "果园·每日肥料",
            target = PlanTargets.ORCHARD_FERTILIZER,
            composition = "每日肥料领取 · 施肥",
            tasks = listOf("每日肥料领取", "施肥"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2,
            mechanisms = listOf("CLAIM", "VIRTUAL_SPEND"),
            costTypes = listOf("FERTILIZER")
        ),
        TaskGroupSpec(
            id = "farm.safe",
            name = "庄园·安全日常",
            target = PlanTargets.FARM,
            composition = "喂鸡 · 厨房 · 抽抽乐（不含好友/家庭/交易）",
            tasks = listOf("喂鸡", "厨房", "抽抽乐"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 5,
            note = "只跑领取/浏览/计时类，跳过交易与游戏",
            executesFullTarget = true,
            mechanisms = listOf("CLAIM", "BROWSE", "TIMED")
        ),
        TaskGroupSpec(
            id = "farm.draw",
            name = "庄园·抽抽乐",
            target = PlanTargets.FARM_DRAW,
            composition = "只跑庄园装扮抽抽乐",
            tasks = listOf("抽抽乐"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2,
            mechanisms = listOf("CHANCE", "CLAIM")
        ),
        TaskGroupSpec(
            id = "farm.task",
            name = "庄园·饲料任务",
            target = PlanTargets.FARM_TASK,
            composition = "只跑庄园饲料任务",
            tasks = listOf("饲料任务"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 3,
            mechanisms = listOf("CLAIM", "TIMED", "BROWSE")
        ),
        TaskGroupSpec(
            id = "orchard.park",
            name = "果园·乐园",
            target = PlanTargets.ORCHARD_PARK,
            composition = "只跑芭芭农场乐园",
            tasks = listOf("乐园"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2,
            mechanisms = listOf("CHANCE", "GAME", "CLAIM")
        ),
        TaskGroupSpec(
            id = "forest.hunt",
            name = "森林·寻宝",
            target = PlanTargets.FOREST_HUNT,
            composition = "只跑森林寻宝/抽1T",
            tasks = listOf("寻宝/抽1T"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 5,
            mechanisms = listOf("CHANCE", "GAME")
        ),
        TaskGroupSpec(
            id = "forest.ip",
            name = "森林·IP联名馆",
            target = PlanTargets.FOREST_IP,
            composition = "只跑森林 IP 联名馆",
            tasks = listOf("IP联名馆"),
            risk = GroupRisk.LOW,
            estimatedMinutes = 2,
            mechanisms = listOf("VIRTUAL_SPEND"),
            costTypes = listOf("ENERGY")
        ),
        TaskGroupSpec(
            id = "forest.expand",
            name = "森林·扩展日常",
            target = PlanTargets.FOREST_DAILY,
            composition = "玩一玩 6 轮 · 外跳小程序 · 好友互动",
            tasks = listOf("玩一玩", "外跳小程序", "好友互动"),
            risk = GroupRisk.HIGH,
            estimatedMinutes = 12,
            note = "高风险任务单独成组，默认不建议开启",
            executesFullTarget = true,
            mechanisms = listOf("EXTERNAL_JUMP", "BROWSE", "GAME")
        )
    )

    fun find(id: String?): TaskGroupSpec? = all.firstOrNull { it.id == id }

    fun forTarget(target: String): TaskGroupSpec? = all.firstOrNull { it.target == target }
}
