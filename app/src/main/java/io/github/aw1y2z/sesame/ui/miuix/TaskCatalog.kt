package io.github.aw1y2z.sesame.ui.miuix

import io.github.aw1y2z.sesame.data.plan.PlanTargets

/** 可执行任务目录：按域分组，供选择器展示与编排估算使用。 */
internal data class PickTask(
    val target: String,
    val minutes: Int,
    val note: String = ""
)

internal data class PickDomain(
    val key: String,
    val label: String,
    val tasks: List<PickTask>
)

internal object TaskCatalog {

    val domains: List<PickDomain> = listOf(
        PickDomain(
            "forest", "森林",
            listOf(
                PickTask(PlanTargets.FOREST_COLLECT, 10, "自己与好友"),
                PickTask(PlanTargets.FOREST_REWARD, 2, "签到/任务/领奖"),
                PickTask(PlanTargets.FOREST_PARK, 2, "乐园宝箱"),
                PickTask(PlanTargets.FOREST_GAME, 8, "乐园游戏任务"),
                PickTask(PlanTargets.FOREST_PATROL, 3, "巡护"),
                PickTask(PlanTargets.FOREST_READ, 8, "无纸阅读"),
                PickTask(PlanTargets.FOREST_HUNT, 5, "概率消耗"),
                PickTask(PlanTargets.FOREST_IP, 2, "消耗能量"),
                PickTask(PlanTargets.FOREST_DAILY, 12, "全量兜底"),
                PickTask(PlanTargets.FOREST_EXTERNAL, 12, "高风险·外跳")
            )
        ),
        PickDomain(
            "farm", "庄园",
            listOf(
                PickTask(PlanTargets.FARM, 8, "全量"),
                PickTask(PlanTargets.FARM_TASK, 3, "饲料任务"),
                PickTask(PlanTargets.FARM_DRAW, 2, "抽抽乐"),
                PickTask(PlanTargets.FARM_PARK, 2, "乐园"),
                PickTask(PlanTargets.FARM_KITCHEN, 2, "厨房"),
                PickTask(PlanTargets.FARM_DONATE, 2, "捐赠"),
                PickTask(PlanTargets.FARM_FAMILY, 3, "家庭"),
                PickTask(PlanTargets.FARM_FRIEND, 3, "好友"),
                PickTask(PlanTargets.FARM_LIFE, 2, "生活"),
                PickTask(PlanTargets.FARM_SUNFLOWER, 2, "向日葵"),
                PickTask(PlanTargets.FARM_COMPETITION, 2, "竞赛")
            )
        ),
        PickDomain(
            "ocean", "海洋",
            listOf(
                PickTask(PlanTargets.OCEAN, 5, "全量"),
                PickTask(PlanTargets.OCEAN_REWARD, 2, "奖励"),
                PickTask(PlanTargets.OCEAN_CLEAN, 2, "清理海域"),
                PickTask(PlanTargets.OCEAN_FISH, 3, "摸鱼转盘"),
                PickTask(PlanTargets.OCEAN_WELCOME, 1, "迎回")
            )
        ),
        PickDomain(
            "orchard", "果园",
            listOf(
                PickTask(PlanTargets.ORCHARD, 6, "全量"),
                PickTask(PlanTargets.ORCHARD_TASK, 2, "任务"),
                PickTask(PlanTargets.ORCHARD_PARK, 2, "乐园"),
                PickTask(PlanTargets.ORCHARD_DRAW, 2, "寻宝"),
                PickTask(PlanTargets.ORCHARD_CHALLENGE, 2, "挑战"),
                PickTask(PlanTargets.ORCHARD_FERTILIZER, 2, "肥料"),
                PickTask(PlanTargets.ORCHARD_SIGN, 1, "签到"),
                PickTask(PlanTargets.ORCHARD_SPREAD, 2, "推广"),
                PickTask(PlanTargets.ORCHARD_ASSIST, 2, "助力"),
                PickTask(PlanTargets.ORCHARD_FACTORY, 2, "工厂"),
                PickTask(PlanTargets.ORCHARD_GAMESTAY, 3, "游戏停留")
            )
        ),
        PickDomain(
            "species", "物种",
            listOf(
                PickTask(PlanTargets.SPECIES_TASK, 2, "任务"),
                PickTask(PlanTargets.SPECIES_DRAW, 2, "抽奖"),
                PickTask(PlanTargets.SPECIES_BOOK, 1, "图鉴"),
                PickTask(PlanTargets.SPECIES_FRIEND, 2, "好友"),
                PickTask(PlanTargets.SPECIES_PROP, 2, "道具"),
                PickTask(PlanTargets.SPECIES_GIFT, 1, "赠送")
            )
        ),
        PickDomain(
            "sports", "运动",
            listOf(
                PickTask(PlanTargets.SPORTS, 2, "步数"),
                PickTask(PlanTargets.KINETIC, 2, "动能")
            )
        ),
        PickDomain(
            "misc", "其他",
            listOf(
                PickTask(PlanTargets.GOLDEN, 2, "金豆"),
                PickTask(PlanTargets.GREEN, 2, "绿色行动"),
                PickTask(PlanTargets.MEMBER, 2, "会员"),
                PickTask(PlanTargets.ANCIENT, 2, "古树"),
                PickTask(PlanTargets.PROTECT, 2, "保护地"),
                PickTask(PlanTargets.DODO, 2, "多多"),
                PickTask(PlanTargets.STALL, 2, "摊位"),
                PickTask(PlanTargets.ANSWER_AI, 3, "AI答题")
            )
        )
    )

    private val minutesByTarget: Map<String, Int> =
        domains.flatMap { it.tasks }.associate { it.target to it.minutes }

    fun minutesOf(target: String): Int = minutesByTarget[target] ?: 2

    fun domainOf(target: String): PickDomain? =
        domains.firstOrNull { domain -> domain.tasks.any { it.target == target } }

    fun domainLabelOf(target: String): String = domainOf(target)?.label ?: "其他"
}
